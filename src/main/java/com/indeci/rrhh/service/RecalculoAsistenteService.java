package com.indeci.rrhh.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.PreflightValidacionDto;
import com.indeci.rrhh.dto.RecalculoCriterioDto;
import com.indeci.rrhh.dto.RecalculoPreviewDto;
import com.indeci.rrhh.dto.RecalculoPreviewDto.RecalculoPreviewItemDto;
import com.indeci.rrhh.dto.RecalculoResultadoDto;
import com.indeci.rrhh.dto.RecalculoResultadoDto.RecalculoResultadoItemDto;
import com.indeci.rrhh.dto.ValidacionHallazgoDto;
import com.indeci.rrhh.entity.Empleado;
import com.indeci.rrhh.entity.EmpleadoPlanilla;
import com.indeci.rrhh.entity.MovimientoPlanilla;
import com.indeci.rrhh.entity.Persona;
import com.indeci.rrhh.entity.RegimenLaboral;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.EmpleadoRepository;
import com.indeci.rrhh.repository.MovimientoPlanillaRepository;
import com.indeci.rrhh.repository.PersonaRepository;
import com.indeci.rrhh.repository.RegimenLaboralRepository;

import lombok.RequiredArgsConstructor;

/**
 * F3.4 — Asistente de Recálculo.
 *
 * <p>Provee dos operaciones:</p>
 * <ul>
 *   <li>{@link #preview(String, RecalculoCriterioDto)} — solo lectura. Lista
 *       los empleados que coinciden con el criterio y el neto actual de su
 *       movimiento (si existe).</li>
 *   <li>{@link #ejecutar(String, RecalculoCriterioDto)} — itera el alcance,
 *       captura el neto anterior, invoca {@link GeneradorPlanillaService#generar}
 *       por empleado (cada uno con su propia transacción vía proxy) y
 *       devuelve el delta. No aborta cuando un empleado falla.</li>
 * </ul>
 *
 * <p>Reusa el motor existente: NO duplica lógica de cálculo. La modificación
 * es un orquestador con captura de antes/después del neto.</p>
 */
@Service
@RequiredArgsConstructor
public class RecalculoAsistenteService {

    public static final String TIPO_TODOS = "TODOS";
    public static final String TIPO_REGIMEN_LABORAL = "REGIMEN_LABORAL";
    public static final String TIPO_EMPLEADOS_LISTA = "EMPLEADOS_LISTA";
    public static final String TIPO_CON_PREFLIGHT = "CON_PREFLIGHT_PENDIENTE";

    private final EmpleadoRepository empleadoRepository;
    private final PersonaRepository personaRepository;
    private final EmpleadoPlanillaRepository planillaRepository;
    private final RegimenLaboralRepository regimenLaboralRepository;
    private final MovimientoPlanillaRepository movimientoRepository;
    private final ValidacionPreflightService preflightService;

    /**
     * El generador se inyecta lazy para que el proxy de Spring intercepte la
     * llamada y cada {@code generar()} corra en su propia transacción.
     */
    @Autowired
    @Lazy
    private GeneradorPlanillaService generadorService;

    // ============================ PREVIEW ============================

    public RecalculoPreviewDto preview(String periodo, RecalculoCriterioDto criterio) {
        validarEntrada(periodo, criterio);

        List<Long> empleadoIds = resolverAlcance(periodo, criterio);

        Map<Long, EmpleadoPlanilla> planillaPorEmp = new HashMap<>();
        for (EmpleadoPlanilla pl : planillaRepository.findByActivo(1)) {
            planillaPorEmp.put(pl.getEmpleadoId(), pl);
        }
        Map<Long, String> regimenPorId = new HashMap<>();
        for (RegimenLaboral rl : regimenLaboralRepository.findAll()) {
            regimenPorId.put(rl.getId(), rl.getCodigo());
        }

        List<RecalculoPreviewItemDto> items = new ArrayList<>(empleadoIds.size());
        for (Long empId : empleadoIds) {
            Empleado e = empleadoRepository.findById(empId).orElse(null);
            if (e == null) continue;

            String nombre = nombrePersona(e);
            EmpleadoPlanilla pl = planillaPorEmp.get(empId);
            String regimen = pl != null && pl.getRegimenLaboralId() != null
                    ? regimenPorId.get(pl.getRegimenLaboralId())
                    : null;

            Optional<MovimientoPlanilla> movOpt = movimientoRepository
                    .findByEmpleadoIdAndPeriodoAndActivo(empId, periodo, 1);
            BigDecimal netoActual = movOpt
                    .map(m -> toBd(m.getNetoPagar()))
                    .orElse(BigDecimal.ZERO);

            items.add(new RecalculoPreviewItemDto(
                    empId, nombre, regimen, netoActual, movOpt.isPresent()));
        }

        return new RecalculoPreviewDto(periodo, criterio.tipo(), items.size(), items);
    }

    // ============================ EJECUTAR ============================

    public RecalculoResultadoDto ejecutar(String periodo, RecalculoCriterioDto criterio) {
        validarEntrada(periodo, criterio);

        List<Long> empleadoIds = resolverAlcance(periodo, criterio);
        Map<Long, String> nombrePorEmp = new HashMap<>();
        for (Long id : empleadoIds) {
            empleadoRepository.findById(id)
                    .ifPresent(e -> nombrePorEmp.put(id, nombrePersona(e)));
        }

        int exitosos = 0;
        int fallidos = 0;
        BigDecimal totalDelta = BigDecimal.ZERO;
        List<RecalculoResultadoItemDto> items = new ArrayList<>(empleadoIds.size());

        for (Long empId : empleadoIds) {
            BigDecimal netoAnterior = movimientoRepository
                    .findByEmpleadoIdAndPeriodoAndActivo(empId, periodo, 1)
                    .map(m -> toBd(m.getNetoPagar()))
                    .orElse(BigDecimal.ZERO);

            try {
                generadorService.generar(empId, periodo);
                BigDecimal netoNuevo = movimientoRepository
                        .findByEmpleadoIdAndPeriodoAndActivo(empId, periodo, 1)
                        .map(m -> toBd(m.getNetoPagar()))
                        .orElse(BigDecimal.ZERO);
                BigDecimal delta = netoNuevo.subtract(netoAnterior);
                totalDelta = totalDelta.add(delta);
                exitosos++;
                items.add(new RecalculoResultadoItemDto(
                        empId, nombrePorEmp.get(empId),
                        "OK", netoAnterior, netoNuevo, delta, null));
            } catch (Exception ex) {
                fallidos++;
                items.add(new RecalculoResultadoItemDto(
                        empId, nombrePorEmp.get(empId),
                        "ERROR", netoAnterior, null, null, ex.getMessage()));
            }
        }

        return new RecalculoResultadoDto(
                periodo, empleadoIds.size(), exitosos, fallidos, totalDelta, items);
    }

    // ============================ ALCANCE ============================

    private List<Long> resolverAlcance(String periodo, RecalculoCriterioDto criterio) {
        String tipo = criterio.tipo() == null ? "" : criterio.tipo().toUpperCase();
        switch (tipo) {
            case TIPO_TODOS                 -> { return alcanceTodos(); }
            case TIPO_REGIMEN_LABORAL       -> { return alcanceRegimen(criterio.valorString()); }
            case TIPO_EMPLEADOS_LISTA       -> { return alcanceLista(criterio.valorListaIds()); }
            case TIPO_CON_PREFLIGHT         -> { return alcancePreflight(periodo); }
            default -> throw new NegocioException(
                    "Tipo de criterio inválido. Use TODOS, REGIMEN_LABORAL, "
                            + "EMPLEADOS_LISTA o CON_PREFLIGHT_PENDIENTE.");
        }
    }

    private List<Long> alcanceTodos() {
        List<Long> ids = new ArrayList<>();
        for (EmpleadoPlanilla pl : planillaRepository.findByActivo(1)) {
            ids.add(pl.getEmpleadoId());
        }
        return ids;
    }

    private List<Long> alcanceRegimen(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            throw new NegocioException("Indica el código de régimen laboral (276, 728, CAS, SERVIR).");
        }
        Long regimenId = null;
        for (RegimenLaboral rl : regimenLaboralRepository.findAll()) {
            if (codigo.equalsIgnoreCase(rl.getCodigo())) {
                regimenId = rl.getId();
                break;
            }
        }
        if (regimenId == null) {
            throw new NegocioException("No existe régimen con código '" + codigo + "'.");
        }
        List<Long> ids = new ArrayList<>();
        for (EmpleadoPlanilla pl : planillaRepository.findByActivo(1)) {
            if (regimenId.equals(pl.getRegimenLaboralId())) {
                ids.add(pl.getEmpleadoId());
            }
        }
        return ids;
    }

    private List<Long> alcanceLista(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new NegocioException("Indica al menos un empleado en la lista.");
        }
        // Solo mantenemos los que existen y tienen planilla activa.
        Set<Long> activos = new HashSet<>();
        for (EmpleadoPlanilla pl : planillaRepository.findByActivo(1)) {
            activos.add(pl.getEmpleadoId());
        }
        List<Long> out = new ArrayList<>();
        for (Long id : ids) {
            if (id != null && activos.contains(id)) out.add(id);
        }
        return out;
    }

    private List<Long> alcancePreflight(String periodo) {
        PreflightValidacionDto pre = preflightService.evaluar(periodo);
        Set<Long> ids = new HashSet<>();
        for (ValidacionHallazgoDto h : pre.hallazgos()) {
            if (h.empleadoId() == null) continue;
            if ("BLOQUEO".equals(h.severidad()) || "ALERTA".equals(h.severidad())) {
                ids.add(h.empleadoId());
            }
        }
        return new ArrayList<>(ids);
    }

    // ============================ HELPERS ============================

    private void validarEntrada(String periodo, RecalculoCriterioDto criterio) {
        if (periodo == null || periodo.isBlank()) {
            throw new NegocioException("Selecciona un período válido (YYYY-MM).");
        }
        if (criterio == null || criterio.tipo() == null || criterio.tipo().isBlank()) {
            throw new NegocioException("Indica el criterio de selección.");
        }
    }

    private String nombrePersona(Empleado e) {
        if (e == null || e.getPersonaId() == null) return null;
        Persona p = personaRepository.findById(e.getPersonaId()).orElse(null);
        return p == null ? null : p.getNombreCompleto();
    }

    private BigDecimal toBd(Double v) {
        return v == null ? BigDecimal.ZERO : BigDecimal.valueOf(v);
    }
}
