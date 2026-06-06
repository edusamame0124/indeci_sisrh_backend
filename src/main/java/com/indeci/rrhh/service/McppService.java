package com.indeci.rrhh.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.indeci.audit.annotation.Auditable;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.McppPlanillaDisponibleDto;
import com.indeci.rrhh.dto.PersonaResumenDto;
import com.indeci.rrhh.entity.ConceptoPlanilla;
import com.indeci.rrhh.entity.Empleado;
import com.indeci.rrhh.entity.EmpleadoPlanilla;
import com.indeci.rrhh.entity.Entidad;
import com.indeci.rrhh.entity.MovimientoPlanilla;
import com.indeci.rrhh.entity.MovimientoPlanillaDetalle;
import com.indeci.rrhh.entity.RegimenLaboral;
import com.indeci.rrhh.repository.ConceptoPlanillaRepository;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.EmpleadoRepository;
import com.indeci.rrhh.repository.EntidadRepository;
import com.indeci.rrhh.repository.MovimientoPlanillaDetalleRepository;
import com.indeci.rrhh.repository.MovimientoPlanillaRepository;
import com.indeci.rrhh.repository.RegimenLaboralRepository;
import com.indeci.rrhh.service.support.McppTxtWriter;

import lombok.RequiredArgsConstructor;

/**
 * B3 / M14 — Generación de archivos MCPP Web (PLL*.TXT).
 *
 * <p>Tipos de planilla: {@code 01} SERVIR, {@code 03} CAS, {@code 12} Judiciales
 * (cross-régimen, solo concepto MCPP {@code 0210}). Mapeos (defaults aprobados):
 * régimen {@code SERVIR→01}/{@code CAS→03}; campo REGIMEN del detalle
 * {@code SERVIR→1}/{@code CAS→4}; tipo documento {@code 01/03→01}, {@code 12→04};
 * correlativo NRO_PLANILLA global por período ({@code TIPO_DOCUMENTO="MCPP"}).
 * Montos: {@code BigDecimal.valueOf(double)}.
 *
 * <p>{@link #generar} asigna correlativo (emite la planilla); {@link #listarDisponibles}
 * solo resume (no quema correlativo).
 */
@Service
@RequiredArgsConstructor
public class McppService {

    private static final Integer ACTIVO = 1;
    private static final String COD_MANDATO_JUDICIAL = "0210";
    private static final List<String> TIPOS = List.of("01", "03", "12");

    private final MovimientoPlanillaRepository movimientoRepository;
    private final MovimientoPlanillaDetalleRepository detalleRepository;
    private final ConceptoPlanillaRepository conceptoRepository;
    private final EmpleadoRepository empleadoRepository;
    private final EmpleadoPlanillaRepository empleadoPlanillaRepository;
    private final RegimenLaboralRepository regimenLaboralRepository;
    private final EntidadRepository entidadRepository;
    private final PersonaService personaService;
    private final CorrelativoService correlativoService;

    /** Archivo MCPP generado: nombre, contenido y metadata para el log de exportación. */
    public record McppArchivo(
            String nombreArchivo,
            String contenido,
            int totalRegistros,
            BigDecimal totalIngresos,
            BigDecimal totalDescuentos) {
    }

    /** Maps de resolución cargados una vez por operación. */
    private record Contexto(
            String codEntidad,
            Map<Long, String> regimenPorEmpleado,
            Map<Long, String> airhspPorEmpleado,
            Map<Long, String> dniPorEmpleado,
            Map<Long, ConceptoPlanilla> conceptoPorId) {
    }

    /** Detalles construidos + totales (sin cabecera ni correlativo). */
    private record Detalles(
            List<McppTxtWriter.Detail> filas,
            BigDecimal totalIngresos,
            BigDecimal totalDescuentos) {
    }

    // ============================ GENERAR (emite correlativo) ============================

    @Auditable(accion = "GENERAR_MCPP")
    @Transactional
    public McppArchivo generar(String periodo, String tipoPlanilla) {
        int anio = Integer.parseInt(periodo.substring(0, 4));
        int mes = Integer.parseInt(periodo.substring(5, 7));

        Contexto ctx = cargarContexto();
        Detalles d = construirDetalles(ctx, periodo, tipoPlanilla);

        long nroPlanilla = correlativoService.siguiente(ctx.codEntidad(), anio, mes, "MCPP");
        String tipoDoc = tipoDocumento(tipoPlanilla);

        McppTxtWriter.Header header = new McppTxtWriter.Header(
                ctx.codEntidad(), anio, mes, tipoDoc, tipoPlanilla, (int) nroPlanilla,
                d.filas().size(), d.totalIngresos(), d.totalDescuentos(), BigDecimal.ZERO);

        String contenido = McppTxtWriter.write(header, d.filas());
        String nombre = nombreArchivo(ctx.codEntidad(), anio, mes, tipoDoc, tipoPlanilla, nroPlanilla);

        return new McppArchivo(nombre, contenido, d.filas().size(),
                d.totalIngresos(), d.totalDescuentos());
    }

    // ============================ LISTAR DISPONIBLES (no quema correlativo) ============================

    @Transactional(readOnly = true)
    public List<McppPlanillaDisponibleDto> listarDisponibles(String periodo) {
        Contexto ctx = cargarContexto();
        List<McppPlanillaDisponibleDto> disponibles = new ArrayList<>();
        for (String tipo : TIPOS) {
            Detalles d = construirDetalles(ctx, periodo, tipo);
            if (d.filas().isEmpty()) {
                continue;
            }
            McppPlanillaDisponibleDto dto = new McppPlanillaDisponibleDto();
            dto.setTipoPlanilla(tipo);
            dto.setNroPlanilla(0); // se asigna al generar
            dto.setTotalRegistros(d.filas().size());
            dto.setTotalIngresos(d.totalIngresos());
            dto.setTotalDescuentos(d.totalDescuentos());
            disponibles.add(dto);
        }
        return disponibles;
    }

    // ============================ RESOLUCIÓN ============================

    private Contexto cargarContexto() {
        Entidad entidad = entidadRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new NegocioException("No hay entidad configurada (INDECI_ENTIDAD)"));

        Map<Long, String> regimenCodigoPorId = regimenLaboralRepository.findAll().stream()
                .collect(Collectors.toMap(RegimenLaboral::getId, RegimenLaboral::getCodigo));
        Map<Long, String> regimenPorEmpleado = new HashMap<>();
        for (EmpleadoPlanilla ep : empleadoPlanillaRepository.findByActivo(ACTIVO)) {
            if (ep.getRegimenLaboralId() != null) {
                regimenPorEmpleado.putIfAbsent(
                        ep.getEmpleadoId(), regimenCodigoPorId.get(ep.getRegimenLaboralId()));
            }
        }
        Map<Long, String> airhspPorEmpleado = empleadoRepository.findAll().stream()
                .filter(e -> e.getRegistroAirhsp() != null)
                .collect(Collectors.toMap(Empleado::getId, Empleado::getRegistroAirhsp, (a, b) -> a));
        Map<Long, String> dniPorEmpleado = personaService.listar().stream()
                .filter(p -> p.getEmpleadoId() != null && p.getDni() != null)
                .collect(Collectors.toMap(PersonaResumenDto::getEmpleadoId,
                        PersonaResumenDto::getDni, (a, b) -> a));
        Map<Long, ConceptoPlanilla> conceptoPorId = conceptoRepository.findAll().stream()
                .collect(Collectors.toMap(ConceptoPlanilla::getId, c -> c));

        return new Contexto(entidad.getCodEntidad(), regimenPorEmpleado,
                airhspPorEmpleado, dniPorEmpleado, conceptoPorId);
    }

    private Detalles construirDetalles(Contexto ctx, String periodo, String tipoPlanilla) {
        List<McppTxtWriter.Detail> filas = new ArrayList<>();
        BigDecimal totalIngresos = BigDecimal.ZERO;
        BigDecimal totalDescuentos = BigDecimal.ZERO;

        for (MovimientoPlanilla mov : movimientoRepository.findByPeriodoAndActivo(periodo, ACTIVO)) {
            Long empId = mov.getEmpleadoId();
            String regimenCodigo = ctx.regimenPorEmpleado().get(empId);
            if (!incluyeEmpleado(tipoPlanilla, regimenCodigo)) {
                continue;
            }
            String dni = ctx.dniPorEmpleado().get(empId);
            String airhsp = ctx.airhspPorEmpleado().getOrDefault(empId, "000000");
            String regimenDetalle = regimenDetalle(regimenCodigo);

            for (MovimientoPlanillaDetalle det : detalleRepository.findByMovimientoPlanillaId(mov.getId())) {
                ConceptoPlanilla concepto = ctx.conceptoPorId().get(det.getConceptoPlanillaId());
                if (concepto == null || concepto.getCodigoMcpp() == null) {
                    continue;
                }
                String tipoConcepto = tipoConceptoMcpp(concepto.getTipo());
                if (tipoConcepto == null) {
                    continue;
                }
                if (!conceptoVaEnPlanilla(tipoPlanilla, concepto.getCodigoMcpp())) {
                    continue;
                }
                BigDecimal monto = BigDecimal.valueOf(det.getMonto() == null ? 0d : det.getMonto());
                filas.add(new McppTxtWriter.Detail(
                        dni, tipoConcepto, concepto.getCodigoMcpp(),
                        concepto.getNombre(), monto, regimenDetalle, airhsp));
                if ("1".equals(tipoConcepto)) {
                    totalIngresos = totalIngresos.add(monto);
                } else {
                    totalDescuentos = totalDescuentos.add(monto);
                }
            }
        }
        return new Detalles(filas, totalIngresos, totalDescuentos);
    }

    // ============================ REGLAS DE TIPO ============================

    private boolean incluyeEmpleado(String tipoPlanilla, String regimenCodigo) {
        if ("12".equals(tipoPlanilla)) {
            return true;
        }
        return tipoPlanilla.equals(regimenToTipo(regimenCodigo));
    }

    private boolean conceptoVaEnPlanilla(String tipoPlanilla, String codigoMcpp) {
        if ("12".equals(tipoPlanilla)) {
            return COD_MANDATO_JUDICIAL.equals(codigoMcpp);
        }
        return !COD_MANDATO_JUDICIAL.equals(codigoMcpp);
    }

    private String regimenToTipo(String regimenCodigo) {
        if ("SERVIR".equalsIgnoreCase(regimenCodigo)) return "01";
        if ("CAS".equalsIgnoreCase(regimenCodigo)) return "03";
        return null;
    }

    private String regimenDetalle(String regimenCodigo) {
        if ("SERVIR".equalsIgnoreCase(regimenCodigo)) return "1";
        if ("CAS".equalsIgnoreCase(regimenCodigo)) return "4";
        return "0";
    }

    private String tipoDocumento(String tipoPlanilla) {
        return "12".equals(tipoPlanilla) ? "04" : "01";
    }

    private String tipoConceptoMcpp(String tipo) {
        if ("INGRESO".equalsIgnoreCase(tipo)) return "1";
        if ("DESCUENTO".equalsIgnoreCase(tipo)) return "2";
        return null;
    }

    private String nombreArchivo(String codEntidad, int anio, int mes,
                                 String tipoDoc, String tipoPlanilla, long nroPlanilla) {
        return String.format("PLL%s%04d%02d%s%s%04d.TXT",
                codEntidad, anio, mes, tipoDoc, tipoPlanilla, nroPlanilla);
    }
}
