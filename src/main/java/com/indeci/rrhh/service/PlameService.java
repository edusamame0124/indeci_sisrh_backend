package com.indeci.rrhh.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.indeci.audit.annotation.Auditable;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.PersonaResumenDto;
import com.indeci.rrhh.dto.PlamePreviewDto;
import com.indeci.rrhh.entity.AsistenciaCabecera;
import com.indeci.rrhh.entity.CatSuspensionSunat;
import com.indeci.rrhh.entity.ConceptoPlanilla;
import com.indeci.rrhh.entity.Entidad;
import com.indeci.rrhh.entity.MovimientoPlanilla;
import com.indeci.rrhh.entity.MovimientoPlanillaDetalle;
import com.indeci.rrhh.entity.Suspension;
import com.indeci.rrhh.repository.AsistenciaCabeceraRepository;
import com.indeci.rrhh.repository.CatSuspensionSunatRepository;
import com.indeci.rrhh.repository.ConceptoPlanillaRepository;
import com.indeci.rrhh.repository.EntidadRepository;
import com.indeci.rrhh.repository.MovimientoPlanillaDetalleRepository;
import com.indeci.rrhh.repository.MovimientoPlanillaRepository;
import com.indeci.rrhh.repository.SuspensionRepository;
import com.indeci.rrhh.service.support.PlameConsolidator;
import com.indeci.rrhh.service.support.PlameJorWriter;
import com.indeci.rrhh.service.support.PlameRemWriter;
import com.indeci.rrhh.service.support.PlameSnlWriter;

import lombok.RequiredArgsConstructor;

/**
 * B3 / M09 — Generación de los archivos PLAME / PDT 601 (.rem, .jor, .snl).
 *
 * <p>Nombre de archivo determinístico: {@code 0601 + YYYY + MM + RUC + ext}.
 *
 * <p>.rem: framework con estrategia por defecto Anexo 2 — cada concepto se agrupa
 * por su {@code CODIGO_PLAME_SUNAT} y se consolida por DNI ({@link PlameConsolidator}).
 * La redistribución de la remuneración base CAS en buckets 0601/1028/1030/2039
 * observada en archivos legacy NO se aplica (pendiente de validación RRHH/SUNAT).
 *
 * <p>.jor: horas ordinarias = {@code DIAS_LABORADOS × 8}; sin asistencia → mes
 * completo (176h). .snl: suspensiones del período con {@code VA_EN_SNL='S'}
 * (excluye cód 21 Lactancia). Montos vía {@code BigDecimal.valueOf(double)}.
 */
@Service
@RequiredArgsConstructor
public class PlameService {

    private static final Integer ACTIVO = 1;
    private static final String ESTADO_ACTIVO = "ACTIVO";
    private static final int HORAS_POR_DIA = 8;
    private static final int HORAS_MES_COMPLETO = 176;
    /** Tipo de documento SUNAT para DNI. */
    private static final String TIPO_DOC_DNI = "1";
    private static final String PREFIJO_PDT = "0601";

    private final MovimientoPlanillaRepository movimientoRepository;
    private final MovimientoPlanillaDetalleRepository detalleRepository;
    private final ConceptoPlanillaRepository conceptoRepository;
    private final AsistenciaCabeceraRepository asistenciaRepository;
    private final SuspensionRepository suspensionRepository;
    private final CatSuspensionSunatRepository catSuspensionRepository;
    private final EntidadRepository entidadRepository;
    private final PersonaService personaService;

    /** Archivo PLAME generado: nombre, contenido y metadata para el log de exportación. */
    public record PlameArchivo(
            String nombreArchivo,
            String contenido,
            int nroLineas,
            BigDecimal totalIngresos,
            BigDecimal totalDescuentos) {
    }

    // ============================ preview ============================

    /** Resumen del período: conteo de líneas de cada archivo + totales del .rem. */
    @Transactional(readOnly = true)
    public PlamePreviewDto preview(String periodo) {
        PlameArchivo rem = generarRem(periodo);
        PlameArchivo jor = generarJor(periodo);
        PlameArchivo snl = generarSnl(periodo);

        PlamePreviewDto dto = new PlamePreviewDto();
        dto.setPeriodo(periodo);
        dto.setRemLineas(rem.nroLineas());
        dto.setJorLineas(jor.nroLineas());
        dto.setSnlLineas(snl.nroLineas());
        dto.setTotalIngresos(rem.totalIngresos());
        dto.setTotalDescuentos(rem.totalDescuentos());
        return dto;
    }

    // ============================ .rem ============================

    @Auditable(accion = "GENERAR_PLAME_REM")
    @Transactional(readOnly = true)
    public PlameArchivo generarRem(String periodo) {
        Map<Long, String> dniPorEmpleado = dniPorEmpleado();
        Map<Long, ConceptoPlanilla> conceptoPorId = conceptoPorId();

        List<PlameConsolidator.RawConcepto> crudos = new ArrayList<>();
        BigDecimal totalIngresos = BigDecimal.ZERO;
        BigDecimal totalDescuentos = BigDecimal.ZERO;

        for (MovimientoPlanilla mov : movimientoRepository.findByPeriodoAndActivo(periodo, ACTIVO)) {
            String dni = dniPorEmpleado.get(mov.getEmpleadoId());
            if (dni == null) {
                continue;
            }
            for (MovimientoPlanillaDetalle det : detalleRepository.findByMovimientoPlanillaId(mov.getId())) {
                ConceptoPlanilla concepto = conceptoPorId.get(det.getConceptoPlanillaId());
                if (concepto == null || concepto.getCodigoPlameSunat() == null) {
                    continue; // sin homólogo PLAME (ej. CUC Total) → no va al .rem
                }
                BigDecimal monto = BigDecimal.valueOf(det.getMonto() == null ? 0d : det.getMonto());
                crudos.add(new PlameConsolidator.RawConcepto(
                        dni, concepto.getCodigoPlameSunat(), monto));

                if ("INGRESO".equalsIgnoreCase(concepto.getTipo())) {
                    totalIngresos = totalIngresos.add(monto);
                } else if ("DESCUENTO".equalsIgnoreCase(concepto.getTipo())) {
                    totalDescuentos = totalDescuentos.add(monto);
                }
            }
        }

        List<PlameRemWriter.Row> filas = PlameConsolidator.consolidar(crudos);
        String contenido = PlameRemWriter.write(filas);
        return new PlameArchivo(nombre(periodo, ".rem"), contenido,
                filas.size(), totalIngresos, totalDescuentos);
    }

    // ============================ .jor ============================

    @Auditable(accion = "GENERAR_PLAME_JOR")
    @Transactional(readOnly = true)
    public PlameArchivo generarJor(String periodo) {
        Map<Long, String> dniPorEmpleado = dniPorEmpleado();
        Map<Long, Integer> diasPorEmpleado = asistenciaRepository.findByPeriodoAndActivo(periodo, ACTIVO)
                .stream()
                .filter(a -> a.getDiasLaborados() != null)
                .collect(Collectors.toMap(AsistenciaCabecera::getEmpleadoId,
                        AsistenciaCabecera::getDiasLaborados, (a, b) -> a));

        List<PlameJorWriter.Row> filas = new ArrayList<>();
        for (MovimientoPlanilla mov : movimientoRepository.findByPeriodoAndActivo(periodo, ACTIVO)) {
            String dni = dniPorEmpleado.get(mov.getEmpleadoId());
            if (dni == null) {
                continue;
            }
            Integer dias = diasPorEmpleado.get(mov.getEmpleadoId());
            int horas = (dias != null) ? dias * HORAS_POR_DIA : HORAS_MES_COMPLETO;
            filas.add(new PlameJorWriter.Row(dni, horas, 0, 0, 0));
        }

        String contenido = PlameJorWriter.write(filas);
        return new PlameArchivo(nombre(periodo, ".jor"), contenido, filas.size(), null, null);
    }

    // ============================ .snl ============================

    @Auditable(accion = "GENERAR_PLAME_SNL")
    @Transactional(readOnly = true)
    public PlameArchivo generarSnl(String periodo) {
        int anio = Integer.parseInt(periodo.substring(0, 4));
        int mes = Integer.parseInt(periodo.substring(5, 7));
        YearMonth ym = YearMonth.of(anio, mes);
        LocalDate inicio = ym.atDay(1);
        LocalDate fin = ym.atEndOfMonth();

        Map<Long, String> dniPorEmpleado = dniPorEmpleado();
        Map<String, CatSuspensionSunat> catPorCodigo = catSuspensionRepository.findAll()
                .stream()
                .collect(Collectors.toMap(CatSuspensionSunat::getCodSuspension, c -> c));

        List<PlameSnlWriter.Row> filas = new ArrayList<>();
        List<Suspension> solapan = suspensionRepository
                .findByEstadoAndFechaInicioLessThanEqualAndFechaFinGreaterThanEqual(
                        ESTADO_ACTIVO, fin, inicio);
        for (Suspension s : solapan) {
            CatSuspensionSunat cat = catPorCodigo.get(s.getCodSuspension());
            if (cat == null || !"S".equalsIgnoreCase(cat.getVaEnSnl())) {
                continue; // excluye cód 21 (Lactancia, VA_EN_SNL='N') y códigos desconocidos
            }
            String dni = dniPorEmpleado.get(s.getEmpleadoId());
            if (dni == null) {
                continue;
            }
            filas.add(new PlameSnlWriter.Row(
                    TIPO_DOC_DNI, dni, s.getCodSuspension(), s.getDiasAfectos()));
        }

        String contenido = PlameSnlWriter.write(filas);
        return new PlameArchivo(nombre(periodo, ".snl"), contenido, filas.size(), null, null);
    }

    // ============================ HELPERS ============================

    private Map<Long, String> dniPorEmpleado() {
        return personaService.listar().stream()
                .filter(p -> p.getEmpleadoId() != null && p.getDni() != null)
                .collect(Collectors.toMap(PersonaResumenDto::getEmpleadoId,
                        PersonaResumenDto::getDni, (a, b) -> a));
    }

    private Map<Long, ConceptoPlanilla> conceptoPorId() {
        return conceptoRepository.findAll().stream()
                .collect(Collectors.toMap(ConceptoPlanilla::getId, c -> c));
    }

    /** Nombre PLAME determinístico: 0601 + YYYY + MM + RUC + extensión. */
    private String nombre(String periodo, String extension) {
        int anio = Integer.parseInt(periodo.substring(0, 4));
        int mes = Integer.parseInt(periodo.substring(5, 7));
        Entidad entidad = entidadRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new NegocioException("No hay entidad configurada (INDECI_ENTIDAD)"));
        if (entidad.getRuc() == null || entidad.getRuc().isBlank()) {
            throw new NegocioException("La entidad no tiene RUC configurado para el archivo PLAME");
        }
        return String.format("%s%04d%02d%s%s", PREFIJO_PDT, anio, mes, entidad.getRuc(), extension);
    }
}
