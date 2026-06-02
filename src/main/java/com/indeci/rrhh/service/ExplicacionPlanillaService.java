package com.indeci.rrhh.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.ExplicacionLineaDto;
import com.indeci.rrhh.dto.ExplicacionPlanillaDto;
import com.indeci.rrhh.dto.ExplicacionPlanillaDto.ExplicacionCabeceraDto;
import com.indeci.rrhh.dto.ExplicacionPlanillaDto.ExplicacionTotalesDto;
import com.indeci.rrhh.entity.Bank;
import com.indeci.rrhh.entity.ConceptoPlanilla;
import com.indeci.rrhh.entity.ConciliacionAirhsp;
import com.indeci.rrhh.entity.Empleado;
import com.indeci.rrhh.entity.EmpleadoBanco;
import com.indeci.rrhh.entity.EmpleadoPlanilla;
import com.indeci.rrhh.entity.MovimientoPlanilla;
import com.indeci.rrhh.entity.MovimientoPlanillaDetalle;
import com.indeci.rrhh.entity.Persona;
import com.indeci.rrhh.entity.RegimenLaboral;
import com.indeci.rrhh.repository.BankRepository;
import com.indeci.rrhh.repository.ConceptoPlanillaRepository;
import com.indeci.rrhh.repository.ConciliacionAirhspRepository;
import com.indeci.rrhh.repository.EmpleadoBancoRepository;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.EmpleadoRepository;
import com.indeci.rrhh.repository.MovimientoPlanillaDetalleRepository;
import com.indeci.rrhh.repository.MovimientoPlanillaRepository;
import com.indeci.rrhh.repository.PersonaRepository;
import com.indeci.rrhh.repository.RegimenLaboralRepository;

import lombok.RequiredArgsConstructor;

/**
 * F3.1a — Construye la respuesta enriquecida que consume Ficha 360 del Empleado.
 *
 * <p>Diseño: <b>solo lectura</b>. NO modifica el motor de cálculo ni regenera
 * la planilla. Lee {@code MovimientoPlanilla} + {@code MovimientoPlanillaDetalle}
 * ya grabados por el motor y los enriquece con metadata del catálogo de
 * conceptos, identificación del empleado, banco/CCI y conciliación AIRHSP.</p>
 *
 * <p>Si no hay movimiento para el período → {@code aplica = false}; la UI
 * muestra empty state con CTA "Generar planilla".</p>
 */
@Service
@RequiredArgsConstructor
public class ExplicacionPlanillaService {

    private final MovimientoPlanillaRepository movimientoRepository;
    private final MovimientoPlanillaDetalleRepository detalleRepository;
    private final ConceptoPlanillaRepository conceptoRepository;
    private final EmpleadoRepository empleadoRepository;
    private final PersonaRepository personaRepository;
    private final EmpleadoPlanillaRepository planillaRepository;
    private final EmpleadoBancoRepository bancoRepository;
    private final BankRepository bankRepository;
    private final RegimenLaboralRepository regimenRepository;
    private final ConciliacionAirhspRepository conciliacionRepository;

    /**
     * Devuelve la explicación del cálculo del empleado para el período.
     */
    public ExplicacionPlanillaDto explicar(Long empleadoId, String periodo) {
        if (empleadoId == null) {
            throw new NegocioException("empleadoId requerido");
        }
        if (periodo == null || periodo.isBlank()) {
            throw new NegocioException("periodo requerido");
        }

        Optional<MovimientoPlanilla> movOpt = movimientoRepository
                .findByEmpleadoIdAndPeriodoAndActivo(empleadoId, periodo, 1);
        if (movOpt.isEmpty()) {
            return ExplicacionPlanillaDto.noAplica(empleadoId, periodo);
        }
        MovimientoPlanilla mov = movOpt.get();

        ExplicacionCabeceraDto cabecera = construirCabecera(empleadoId);
        ExplicacionTotalesDto totales = construirTotales(mov, empleadoId);
        List<ExplicacionLineaDto> lineas = construirLineas(mov.getId());

        return new ExplicacionPlanillaDto(
                true, empleadoId, periodo, cabecera, totales, lineas);
    }

    // ================== CABECERA ==================

    private ExplicacionCabeceraDto construirCabecera(Long empleadoId) {
        String nombre = null;
        String dni = null;
        Empleado emp = empleadoRepository.findById(empleadoId).orElse(null);
        if (emp != null && emp.getPersonaId() != null) {
            Persona persona = personaRepository.findById(emp.getPersonaId()).orElse(null);
            if (persona != null) {
                nombre = persona.getNombreCompleto();
                dni = persona.getDni();
            }
        }

        String regimenCodigo = null;
        String regimenNombre = null;
        String meta = null;
        EmpleadoPlanilla planilla = planillaRepository
                .findFirstByEmpleadoIdAndActivo(empleadoId, 1).orElse(null);
        if (planilla != null) {
            meta = planilla.getMeta();
            if (planilla.getRegimenLaboralId() != null) {
                RegimenLaboral reg = regimenRepository
                        .findById(planilla.getRegimenLaboralId()).orElse(null);
                if (reg != null) {
                    regimenCodigo = reg.getCodigo();
                    regimenNombre = reg.getNombre();
                }
            }
        }

        String bancoNombre = null;
        String cuenta = null;
        String cci = null;
        EmpleadoBanco banco = bancoRepository
                .findByEmpleadoIdAndEsCuentaPlanillaAndActivo(empleadoId, 1, 1)
                .orElse(null);
        if (banco != null) {
            cuenta = banco.getNumeroCuenta();
            cci = banco.getCci();
            if (banco.getBankId() != null) {
                Bank b = bankRepository.findById(banco.getBankId()).orElse(null);
                if (b != null) bancoNombre = b.getName();
            }
        }

        return new ExplicacionCabeceraDto(
                nombre, dni,
                regimenCodigo, regimenNombre,
                meta,
                bancoNombre, cuenta, cci);
    }

    // ================== TOTALES ==================

    private ExplicacionTotalesDto construirTotales(MovimientoPlanilla mov, Long empleadoId) {
        // Separa el detalle en grupos para sumar aporte trabajador y aporte empleador.
        // El motor ya grabó total ingresos / descuentos / neto en la cabecera; solo
        // necesitamos derivar aporteTrabajador (suma de APORTE_TRABAJADOR) y
        // aporteEmpleador (suma de APORTE_EMPLEADOR).
        BigDecimal aporteTrab = BigDecimal.ZERO;
        BigDecimal aporteEmp  = BigDecimal.ZERO;

        for (MovimientoPlanillaDetalle d : detalleRepository.findByMovimientoPlanillaId(mov.getId())) {
            if (d.getConceptoPlanillaId() == null || d.getMonto() == null) continue;
            ConceptoPlanilla c = conceptoRepository.findById(d.getConceptoPlanillaId()).orElse(null);
            if (c == null) continue;
            String tipo = resolverTipoConcepto(c);
            BigDecimal monto = BigDecimal.valueOf(d.getMonto());
            if ("APORTE_TRABAJADOR".equals(tipo)) {
                aporteTrab = aporteTrab.add(monto);
            } else if ("APORTE_EMPLEADOR".equals(tipo)) {
                aporteEmp = aporteEmp.add(monto);
            }
        }

        // Conciliación AIRHSP — opcional.
        BigDecimal montoSistema = null;
        BigDecimal montoAirhsp  = null;
        BigDecimal diferencia   = null;
        String estadoAirhsp = null;
        ConciliacionAirhsp con = conciliacionRepository
                .findByMovimientoPlanillaIdAndEmpleadoId(mov.getId(), empleadoId)
                .orElse(null);
        if (con != null) {
            montoSistema = con.getMontoSistema() != null
                    ? BigDecimal.valueOf(con.getMontoSistema()) : null;
            montoAirhsp = con.getMontoAirhsp() != null
                    ? BigDecimal.valueOf(con.getMontoAirhsp()) : null;
            diferencia = con.getDiferencia() != null
                    ? BigDecimal.valueOf(con.getDiferencia()) : null;
            estadoAirhsp = con.getEstado();
        }

        return new ExplicacionTotalesDto(
                toBd(mov.getTotalIngresos()),
                toBd(mov.getTotalDescuentos()),
                aporteTrab,
                aporteEmp,
                toBd(mov.getNetoPagar()),
                mov.getEstadoNeto(),
                toBd(mov.getNeto50pctMinimo()),
                montoSistema, montoAirhsp, diferencia, estadoAirhsp);
    }

    // ================== LÍNEAS ==================

    private List<ExplicacionLineaDto> construirLineas(Long movimientoId) {
        List<MovimientoPlanillaDetalle> detalles =
                detalleRepository.findByMovimientoPlanillaId(movimientoId);

        List<ExplicacionLineaDto> lineas = new ArrayList<>(detalles.size());
        for (MovimientoPlanillaDetalle d : detalles) {
            ConceptoPlanilla c = d.getConceptoPlanillaId() != null
                    ? conceptoRepository.findById(d.getConceptoPlanillaId()).orElse(null)
                    : null;

            String grupo = c != null ? resolverTipoConcepto(c) : "INFO";
            String descripcion = c != null ? c.getNombre() : "Concepto sin nombre";
            String codigoMef = c != null ? c.getCodigoMef() : null;
            String codigoSisper = c != null ? c.getCodigoSisper() : null;

            BigDecimal monto = d.getMonto() != null
                    ? BigDecimal.valueOf(d.getMonto())
                    : BigDecimal.ZERO;

            String detalleTexto = construirDetalleTexto(d, c);
            String fuenteTipo = c != null && MEF_AUTOCALCULADOS.contains(codigoMef)
                    ? "CONCEPTO_AUTO"
                    : "EMPLEADO_CONCEPTO";

            lineas.add(new ExplicacionLineaDto(
                    grupo,
                    d.getConceptoPlanillaId(),
                    codigoMef,
                    codigoSisper,
                    descripcion,
                    monto,
                    detalleTexto,
                    d.getObservacion(),
                    fuenteTipo,
                    d.getConceptoPlanillaId()));
        }
        return lineas;
    }

    /**
     * Texto humano para el desglose. Combina pistas del detalle (cantidad,
     * pagoDiferencial, diasReintegro) cuando están presentes. Idea: si no
     * hay nada útil, devuelve null y la UI no muestra subtítulo.
     */
    private String construirDetalleTexto(MovimientoPlanillaDetalle d, ConceptoPlanilla c) {
        if (d.getDiasReintegro() != null && d.getDiasReintegro() > 0) {
            return "Reintegro " + d.getDiasReintegro() + " días";
        }
        if (d.getCantidad() != null && d.getCantidad() > 0) {
            return d.getCantidad() + " unidades";
        }
        if (c != null && "S".equalsIgnoreCase(c.getEsProrrateable())) {
            return "Prorrateable";
        }
        return null;
    }

    // ================== HELPERS ==================

    /** Mismo set que el motor (GeneradorPlanillaService.MEF_AUTOCALCULADOS). */
    private static final java.util.Set<String> MEF_AUTOCALCULADOS = java.util.Set.of(
            "00302", "00502",
            "05001", "05002", "05003", "05004",
            "05101",
            "06001", "06002", "05309",
            "05401", "05402");

    private static String resolverTipoConcepto(ConceptoPlanilla c) {
        if (c.getTipoConcepto() != null && !c.getTipoConcepto().isBlank()) {
            String tipo = c.getTipoConcepto().toUpperCase();
            // Mapeo a los grupos canónicos del DTO.
            return switch (tipo) {
                case "REMUNERATIVO", "NO_REMUNERATIVO" -> "INGRESO";
                case "DESCUENTO"                        -> "DESCUENTO";
                case "APORTE_TRABAJADOR"                -> "APORTE_TRABAJADOR";
                case "APORTE_EMPLEADOR"                 -> "APORTE_EMPLEADOR";
                default                                  -> "INGRESO";
            };
        }
        // Fallback al tipo legacy.
        String legacy = c.getTipo();
        if (legacy == null) return "INGRESO";
        return switch (legacy.toUpperCase()) {
            case "INGRESO"   -> "INGRESO";
            case "DESCUENTO" -> "DESCUENTO";
            case "APORTE"    -> "APORTE_TRABAJADOR";
            default          -> "INGRESO";
        };
    }

    private static BigDecimal toBd(Double v) {
        return v == null ? null : BigDecimal.valueOf(v);
    }
}
