package com.indeci.rrhh.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.SubsidioCalculadoDto;
import com.indeci.rrhh.entity.EmpleadoEvento;
import com.indeci.rrhh.entity.MovimientoPlanilla;
import com.indeci.rrhh.entity.SubsidioEventoCalculo;
import com.indeci.rrhh.entity.TipoEvento;
import com.indeci.rrhh.repository.EmpleadoEventoRepository;
import com.indeci.rrhh.repository.MovimientoPlanillaRepository;
import com.indeci.rrhh.repository.SubsidioEventoCalculoRepository;

import lombok.RequiredArgsConstructor;

/**
 * F2.4 - Calculo de subsidios (maternidad/enfermedad) segun contrato Excel RR. HH.
 *
 * Formula:
 * - diasDescanso = fechaFin - fechaInicio + 1
 * - subsidioTotal100 = diasDescanso * (remuneracionMensualBase / 30)
 * - baseReconocidaEssalud = suma de 12 meses topados a 45% UIT
 * - subsidioDiarioEssalud = baseReconocidaEssalud / 360
 * - enfermedad: EsSalud reconoce los dias que exceden los primeros 20 dias
 *   acumulados del trabajador en el anio calendario
 * - maternidad: EsSalud reconoce todos los dias salvo parametro institucional
 * - diferenciaAsumidaIndeci = subsidioTotal100 - subsidioEssalud
 */
@Service
@RequiredArgsConstructor
public class SubsidioCalculadorService {

    private static final BigDecimal TREINTA = new BigDecimal("30");
    private static final int MESES_BASE_ESSALUD = 12;

    private static final String TIPO_ENFERMEDAD = "ENFERMEDAD";
    private static final String TIPO_MATERNIDAD = "MATERNIDAD";
    private static final String PLAME_SUBSIDIO_MATERNIDAD = "0915";
    private static final String PLAME_SUBSIDIO_ENFERMEDAD = "0916";
    private static final String PLAME_DIFERENCIAL_CAS = "2073";

    private static final String PARAM_UIT = "UIT";
    private static final String PARAM_SUBSIDIO_TOPE_PCT_UIT = "SUBSIDIO_TOPE_PCT_UIT";
    private static final String PARAM_SUBSIDIO_DIVISOR_PROMEDIO = "SUBSIDIO_DIVISOR_PROMEDIO";
    private static final String PARAM_SUBSIDIO_DIAS_ASUME_ENTIDAD_ENFERMEDAD =
            "SUBSIDIO_DIAS_ASUME_ENTIDAD_ENFERMEDAD";
    private static final String PARAM_SUBSIDIO_DIAS_ASUME_ENTIDAD_MATERNIDAD =
            "SUBSIDIO_DIAS_ASUME_ENTIDAD_MATERNIDAD";

    private final MovimientoPlanillaRepository movimientoRepository;
    private final EmpleadoEventoRepository empleadoEventoRepository;
    private final SubsidioEventoCalculoRepository subsidioEventoCalculoRepository;
    private final ParametroRemunerativoService parametroService;
    private final CalculoSnapshotService calculoSnapshotService;

    @Transactional(readOnly = true)
    public SubsidioCalculadoDto calcular(
            EmpleadoEvento evento,
            BigDecimal remuneracionMensualBase) {

        validarEntrada(evento, remuneracionMensualBase);

        TipoEvento tipo = evento.getTipoEvento();
        if (tipo == null || !"S".equalsIgnoreCase(tipo.getGeneraSubsidio())) {
            return SubsidioCalculadoDto.noAplica();
        }

        int diasDescanso = calcularDiasDescanso(evento);
        if (diasDescanso <= 0) {
            return SubsidioCalculadoDto.noAplica();
        }

        BigDecimal remuneracionDiaria = remuneracionMensualBase
                .divide(TREINTA, 2, RoundingMode.HALF_UP);
        BigDecimal subtotalRemunerativo = remuneracionDiaria
                .multiply(BigDecimal.valueOf(diasDescanso))
                .setScale(2, RoundingMode.HALF_UP);

        int anioFiscal = evento.getFechaInicio().getYear();
        BigDecimal topeMensual = calcularTopeMensualSubsidio(anioFiscal);
        BigDecimal baseReconocida = calcularBaseReconocidaUltimos12Meses(
                evento.getEmpleadoId(), evento.getFechaInicio(),
                remuneracionMensualBase, topeMensual);
        BigDecimal divisorPromedio = parametroService.obtenerValor(
                PARAM_SUBSIDIO_DIVISOR_PROMEDIO, anioFiscal, null);
        BigDecimal subsidioDiarioEssalud = baseReconocida
                .divide(divisorPromedio, 2, RoundingMode.HALF_UP);

        DistribucionDiasSubsidio distribucion = calcularDistribucionDias(
                evento, tipo, diasDescanso, anioFiscal);
        BigDecimal subsidioEssalud = subsidioDiarioEssalud
                .multiply(BigDecimal.valueOf(distribucion.diasSubsidioEssalud))
                .setScale(0, RoundingMode.HALF_UP);

        BigDecimal diferenciaIndeci = subtotalRemunerativo.subtract(subsidioEssalud);

        return new SubsidioCalculadoDto(
                true,
                normalizarCodigo(tipo.getCodigo()),
                diasDescanso,
                distribucion.diasAcumuladosPrevios,
                distribucion.diasEntidad,
                distribucion.diasSubsidioEssalud,
                codigoPlameSubsidio(tipo),
                PLAME_DIFERENCIAL_CAS,
                remuneracionDiaria,
                subtotalRemunerativo,
                baseReconocida,
                subsidioDiarioEssalud,
                subsidioEssalud,
                diferenciaIndeci);
    }

    @Transactional
    public SubsidioCalculadoDto calcularYRegistrar(
            EmpleadoEvento evento,
            BigDecimal remuneracionMensualBase,
            Long movimientoPlanillaId,
            String periodo,
            String usuario) {

        SubsidioCalculadoDto calculo = calcular(evento, remuneracionMensualBase);
        if (!calculo.aplica()) {
            return calculo;
        }
        if (evento.getId() == null) {
            throw new NegocioException("Evento sin ID: no se puede registrar calculo de subsidio");
        }

        subsidioEventoCalculoRepository.desactivarVigentesPorEvento(evento.getId());

        SubsidioEventoCalculo entity = new SubsidioEventoCalculo();
        entity.setEmpleadoId(evento.getEmpleadoId());
        entity.setEmpleadoEventoId(evento.getId());
        entity.setMovimientoPlanillaId(movimientoPlanillaId);
        entity.setPeriodo(periodo);
        entity.setAnioCalendario(evento.getFechaInicio().getYear());
        entity.setTipoSubsidio(calculo.tipoSubsidio());
        entity.setFechaInicioDescanso(evento.getFechaInicio());
        entity.setFechaFinDescanso(evento.getFechaFin());
        entity.setDiasDescanso(calculo.diasDescanso());
        entity.setDiasAcumuladosPrevios(calculo.diasAcumuladosPrevios());
        entity.setDiasACargoEntidad(calculo.diasEntidad());
        entity.setDiasSubsidioEssalud(calculo.diasSubsidioEssalud());
        entity.setCittNumero(null);
        entity.setDocumentoMedicoReferencia(documentoReferencia(evento));
        entity.setCodigoPlameSubsidio(calculo.codigoPlameSubsidio());
        entity.setCodigoPlameDiferencial(calculo.codigoPlameDiferencial());
        entity.setSubsidioTotal100(calculo.subtotalRemunerativo());
        entity.setBaseReconocidaEssalud(calculo.baseReconocidaEssalud());
        entity.setSubsidioDiarioEssalud(calculo.subsidioDiarioEssalud());
        entity.setSubsidioEssalud(calculo.subsidioEssalud());
        entity.setPagoDiferencial(calculo.diferenciaAsumidaIndeci());
        entity.setEstado("CALCULADO");
        entity.setObservacion("Calculo subsidio " + calculo.tipoSubsidio()
                + " | PLAME " + calculo.codigoPlameSubsidio()
                + " | diferencial " + calculo.codigoPlameDiferencial());
        entity.setActivo(1);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setCreatedBy(usuario);
        subsidioEventoCalculoRepository.save(entity);

        registrarSnapshotSubsidio(evento, movimientoPlanillaId, periodo, calculo);
        return calculo;
    }

    private void validarEntrada(EmpleadoEvento evento, BigDecimal remuneracionMensualBase) {
        if (evento == null) {
            throw new NegocioException("Evento nulo");
        }
        if (evento.getFechaInicio() == null || evento.getFechaFin() == null) {
            throw new NegocioException(
                    "Evento sin fechas inicio/fin (empleadoId=" + evento.getEmpleadoId() + ")");
        }
        if (evento.getFechaFin().isBefore(evento.getFechaInicio())) {
            throw new NegocioException("Evento con fechaFin < fechaInicio");
        }
        if (remuneracionMensualBase == null || remuneracionMensualBase.signum() <= 0) {
            throw new NegocioException("Remuneracion mensual base invalida (debe ser > 0)");
        }
    }

    private static int calcularDiasDescanso(EmpleadoEvento evento) {
        long dias = ChronoUnit.DAYS.between(evento.getFechaInicio(), evento.getFechaFin()) + 1;
        return (int) dias;
    }

    private BigDecimal calcularTopeMensualSubsidio(int anioFiscal) {
        BigDecimal factorTope = parametroService.obtenerValor(
                PARAM_SUBSIDIO_TOPE_PCT_UIT, anioFiscal, null);
        BigDecimal uit = parametroService.obtenerValor(PARAM_UIT, anioFiscal, null);
        return uit.multiply(factorTope).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calcularBaseReconocidaUltimos12Meses(
            Long empleadoId,
            LocalDate fechaBase,
            BigDecimal fallbackMensual,
            BigDecimal topeMensual) {

        YearMonth eventoYm = YearMonth.from(fechaBase);
        YearMonth desde = eventoYm.minusMonths(MESES_BASE_ESSALUD);
        YearMonth hasta = eventoYm.minusMonths(1);

        List<MovimientoPlanilla> historial = movimientoRepository
                .findByEmpleadoIdAndActivo(empleadoId, 1)
                .stream()
                .filter(m -> estaEnVentanaSubsidio(m.getPeriodo(), desde, hasta))
                .toList();

        if (historial.isEmpty()) {
            return toparBaseSubsidio(fallbackMensual, topeMensual)
                    .multiply(BigDecimal.valueOf(MESES_BASE_ESSALUD))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        return historial.stream()
                .map(MovimientoPlanilla::getTotalIngresos)
                .map(monto -> monto != null ? BigDecimal.valueOf(monto) : BigDecimal.ZERO)
                .map(monto -> toparBaseSubsidio(monto, topeMensual))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static boolean estaEnVentanaSubsidio(
            String periodo, YearMonth desde, YearMonth hasta) {
        YearMonth ym = parsePeriodoSeguro(periodo);
        return ym != null && !ym.isBefore(desde) && !ym.isAfter(hasta);
    }

    private static BigDecimal toparBaseSubsidio(BigDecimal monto, BigDecimal topeMensual) {
        if (monto == null || monto.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return monto.min(topeMensual);
    }

    private DistribucionDiasSubsidio calcularDistribucionDias(
            EmpleadoEvento evento, TipoEvento tipo, int diasDescanso, int anioFiscal) {
        String codigo = normalizarCodigo(tipo.getCodigo());
        if (TIPO_MATERNIDAD.equalsIgnoreCase(codigo)) {
            int diasAsumeEntidad = parametroService.obtenerValor(
                    PARAM_SUBSIDIO_DIAS_ASUME_ENTIDAD_MATERNIDAD, anioFiscal, null)
                    .intValue();
            int diasEntidad = Math.min(diasDescanso, Math.max(0, diasAsumeEntidad));
            return new DistribucionDiasSubsidio(
                    0, diasEntidad, Math.max(0, diasDescanso - diasEntidad));
        }
        if (TIPO_ENFERMEDAD.equalsIgnoreCase(codigo)) {
            int limiteEntidad = parametroService.obtenerValor(
                    PARAM_SUBSIDIO_DIAS_ASUME_ENTIDAD_ENFERMEDAD, anioFiscal, null)
                    .intValue();
            int diasAcumuladosPrevios = calcularDiasAcumuladosPreviosEnfermedad(evento);
            int diasRestantesEntidad = Math.max(0, limiteEntidad - diasAcumuladosPrevios);
            int diasEntidad = Math.min(diasDescanso, diasRestantesEntidad);
            int diasSubsidioEssalud = Math.max(0, diasDescanso - diasEntidad);
            return new DistribucionDiasSubsidio(
                    diasAcumuladosPrevios, diasEntidad, diasSubsidioEssalud);
        }
        return new DistribucionDiasSubsidio(0, 0, diasDescanso);
    }

    private int calcularDiasAcumuladosPreviosEnfermedad(EmpleadoEvento evento) {
        LocalDate desde = LocalDate.of(evento.getFechaInicio().getYear(), 1, 1);
        LocalDate hasta = evento.getFechaInicio().minusDays(1);
        if (hasta.isBefore(desde)) {
            return 0;
        }
        return empleadoEventoRepository.findEnfermedadesPreviasEnAnio(
                        evento.getEmpleadoId(), desde, hasta, evento.getId())
                .stream()
                .mapToInt(previo -> contarDiasSolapados(previo, desde, hasta))
                .sum();
    }

    private static int contarDiasSolapados(
            EmpleadoEvento evento, LocalDate desde, LocalDate hasta) {
        if (evento.getFechaInicio() == null || evento.getFechaFin() == null) {
            return 0;
        }
        LocalDate inicio = evento.getFechaInicio().isAfter(desde)
                ? evento.getFechaInicio() : desde;
        LocalDate fin = evento.getFechaFin().isBefore(hasta)
                ? evento.getFechaFin() : hasta;
        if (fin.isBefore(inicio)) {
            return 0;
        }
        return (int) ChronoUnit.DAYS.between(inicio, fin) + 1;
    }

    private String codigoPlameSubsidio(TipoEvento tipo) {
        String codigo = normalizarCodigo(tipo.getCodigo());
        if (TIPO_MATERNIDAD.equals(codigo)) {
            return PLAME_SUBSIDIO_MATERNIDAD;
        }
        if (TIPO_ENFERMEDAD.equals(codigo)) {
            return PLAME_SUBSIDIO_ENFERMEDAD;
        }
        return null;
    }

    private static String normalizarCodigo(String codigo) {
        return codigo == null ? "" : codigo.trim().toUpperCase();
    }

    private static String documentoReferencia(EmpleadoEvento evento) {
        if (evento.getSustentoLegajoDocId() != null) {
            return "LEGAJO_DOC_ID=" + evento.getSustentoLegajoDocId();
        }
        String observacion = evento.getObservacion();
        if (observacion == null || observacion.isBlank()) {
            return null;
        }
        String valor = observacion.trim();
        return valor.length() <= 100 ? valor : valor.substring(0, 100);
    }

    private void registrarSnapshotSubsidio(
            EmpleadoEvento evento,
            Long movimientoPlanillaId,
            String periodo,
            SubsidioCalculadoDto calculo) {

        calculoSnapshotService.registrar(
                CalculoSnapshotService.registro(
                                evento.getEmpleadoId(), periodo, CalculoSnapshotService.REGLA_SUBSIDIO)
                        .movimiento(movimientoPlanillaId)
                        .base(calculo.baseReconocidaEssalud())
                        .resultado(calculo.subsidioEssalud())
                        .version(String.valueOf(evento.getFechaInicio().getYear()))
                        .formula("subsidioEssalud = diasSubsidioEssalud * subsidioDiarioEssalud")
                        .param("tipoSubsidio", calculo.tipoSubsidio())
                        .param("fechaInicio", evento.getFechaInicio())
                        .param("fechaFin", evento.getFechaFin())
                        .param("diasDescanso", calculo.diasDescanso())
                        .param("diasAcumuladosPrevios", calculo.diasAcumuladosPrevios())
                        .param("diasEntidad", calculo.diasEntidad())
                        .param("diasSubsidioEssalud", calculo.diasSubsidioEssalud())
                        .param("codigoPlameSubsidio", calculo.codigoPlameSubsidio())
                        .param("codigoPlameDiferencial", calculo.codigoPlameDiferencial())
                        .param("subsidioTotal100", calculo.subtotalRemunerativo())
                        .param("baseReconocidaEssalud", calculo.baseReconocidaEssalud())
                        .param("subsidioDiarioEssalud", calculo.subsidioDiarioEssalud())
                        .param("subsidioEssalud", calculo.subsidioEssalud())
                        .param("pagoDiferencial", calculo.diferenciaAsumidaIndeci()));
    }

    private static YearMonth parsePeriodoSeguro(String periodo) {
        if (periodo == null) {
            return null;
        }
        String p = periodo.replace("-", "").trim();
        if (p.length() != 6) {
            return null;
        }
        try {
            int anio = Integer.parseInt(p.substring(0, 4));
            int mes = Integer.parseInt(p.substring(4, 6));
            return YearMonth.of(anio, mes);
        } catch (NumberFormatException | java.time.DateTimeException ex) {
            return null;
        }
    }

    private record DistribucionDiasSubsidio(
            int diasAcumuladosPrevios,
            int diasEntidad,
            int diasSubsidioEssalud) {}
}
