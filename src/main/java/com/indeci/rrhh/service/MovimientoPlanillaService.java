package com.indeci.rrhh.service;

import com.indeci.audit.annotation.Auditable;
import com.indeci.audit.context.AuditoriaContext;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.MovimientoPlanillaResponseDto;
import com.indeci.rrhh.dto.ResumenMetaDto;
import com.indeci.rrhh.dto.ResumenMetaEmpleadoDto;
import com.indeci.rrhh.entity.ConceptoPlanilla;
import com.indeci.rrhh.entity.Empleado;
import com.indeci.rrhh.entity.EmpleadoPlanilla;
import com.indeci.rrhh.entity.EmpleadoPension;
import com.indeci.rrhh.entity.MovimientoPlanilla;
import com.indeci.rrhh.entity.MovimientoPlanillaDetalle;
import com.indeci.rrhh.entity.Persona;
import com.indeci.rrhh.entity.RegimenLaboral;
import com.indeci.rrhh.entity.RegimenPensionario;
import com.indeci.rrhh.repository.ConceptoPlanillaRepository;
import com.indeci.rrhh.repository.EmpleadoRepository;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.MovimientoPlanillaDetalleRepository;
import com.indeci.rrhh.repository.MovimientoPlanillaRepository;
import com.indeci.rrhh.repository.PersonaRepository;
import com.indeci.rrhh.repository.RegimenLaboralRepository;
import com.indeci.rrhh.repository.EmpleadoPensionRepository;
import com.indeci.rrhh.repository.RegimenPensionarioRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MovimientoPlanillaService {

    private final MovimientoPlanillaRepository repository;

    private final MovimientoPlanillaDetalleRepository
            detalleRepository;

    private final EmpleadoPlanillaRepository
            planillaRepository;

    private final EmpleadoRepository empleadoRepository;

    private final PersonaRepository personaRepository;

    private final RegimenLaboralRepository regimenLaboralRepository;

    private final ConceptoPlanillaRepository conceptoRepository;

    private final EmpleadoPensionRepository pensionRepository;

    private final RegimenPensionarioRepository regimenPensionarioRepository;

    private final AuditoriaContext auditoriaContext;

    /** CODIGO_MEF del aporte ESSALUD empleador (sin y con EPS). */
    private static final Set<String> MEF_ESSALUD =
            Set.of("06001", "06002");

    /** CODIGO_MEF de los aportes pensionarios del trabajador (ONP/AFP). */
    private static final Set<String> MEF_APORTES_PENSION =
            Set.of("05001", "05002", "05003", "05004");

    private static final Comparator<EmpleadoPlanilla> PLANILLA_ACTUAL_ORDER =
            Comparator
                    .comparing(
                            EmpleadoPlanilla::getUpdatedAt,
                            Comparator.nullsFirst(Comparator.naturalOrder()))
                    .thenComparing(
                            EmpleadoPlanilla::getCreatedAt,
                            Comparator.nullsFirst(Comparator.naturalOrder()))
                    .thenComparing(
                            EmpleadoPlanilla::getFechaInicio,
                            Comparator.nullsFirst(Comparator.naturalOrder()))
                    .thenComparing(
                            EmpleadoPlanilla::getId,
                            Comparator.nullsFirst(Comparator.naturalOrder()));

    // ==========================================
    // LISTAR EMPLEADO
    // ==========================================

    public MovimientoPlanillaResponseDto
    obtenerEmpleado(Long empleadoId,
                    String periodo) {

        MovimientoPlanilla mov =
                repository
                        .findByEmpleadoIdAndPeriodoAndActivo(
                                empleadoId,
                                periodo,
                                1)
                        .orElseThrow(() ->
                                new NegocioException(
                                        "Planilla no encontrada"));

        return mapearMovimientos(List.of(mov)).get(0);
    }

    // ==========================================
    // LISTAR PERIODO
    // ==========================================

    public List<MovimientoPlanillaResponseDto>
    listarPeriodo(String periodo) {

        return mapearMovimientos(repository.findByPeriodoAndActivo(periodo, 1));
    }

    // ==========================================
    // LISTAR POR EMPLEADO (historial — PANTALLA-08)
    // ==========================================

    public List<MovimientoPlanillaResponseDto>
    listarPorEmpleado(Long empleadoId) {

        return mapearMovimientos(repository.findByEmpleadoIdAndActivo(empleadoId, 1));
    }

    private List<MovimientoPlanillaResponseDto>
    mapearMovimientos(List<MovimientoPlanilla> movimientos) {

        if (movimientos.isEmpty()) {
            return List.of();
        }

        List<Long> empleadoIds = movimientos.stream()
                .map(MovimientoPlanilla::getEmpleadoId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<Long, Empleado> empleadosPorId = empleadoRepository
                .findAllById(empleadoIds)
                .stream()
                .collect(Collectors.toMap(
                        Empleado::getId,
                        Function.identity(),
                        (a, b) -> a));

        List<Long> personaIds = empleadosPorId.values().stream()
                .map(Empleado::getPersonaId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<Long, Persona> personasPorId = personaRepository
                .findAllById(personaIds)
                .stream()
                .collect(Collectors.toMap(
                        Persona::getId,
                        Function.identity(),
                        (a, b) -> a));

        Map<Long, EmpleadoPlanilla> planillasPorEmpleado =
                planillaActualPorEmpleado(empleadoIds);

        List<Long> regimenIds = planillasPorEmpleado.values().stream()
                .map(EmpleadoPlanilla::getRegimenLaboralId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<Long, RegimenLaboral> regimenesPorId = regimenLaboralRepository
                .findAllById(regimenIds)
                .stream()
                .collect(Collectors.toMap(
                        RegimenLaboral::getId,
                        Function.identity(),
                        (a, b) -> a));

        // 🔹 Nuevo: Cargar pensiones y regímenes pensionarios
        List<EmpleadoPension> pensionesList = pensionRepository
                .findByEmpleadoIdInAndActivo(empleadoIds, 1);
        
        Map<Long, EmpleadoPension> pensionesPorEmpleado = pensionesList.stream()
                .collect(Collectors.toMap(
                        EmpleadoPension::getEmpleadoId,
                        Function.identity(),
                        (a, b) -> a)); // Tomar el primero si hay duplicados activos
                        
        List<Long> regPensionIds = pensionesList.stream()
                .map(EmpleadoPension::getRegimenPensionarioId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
                
        Map<Long, RegimenPensionario> regimenesPensionariosPorId = regimenPensionarioRepository
                .findAllById(regPensionIds)
                .stream()
                .collect(Collectors.toMap(
                        RegimenPensionario::getId,
                        Function.identity(),
                        (a, b) -> a));

        return movimientos.stream()
                .map(mov -> mapearMovimiento(
                        mov,
                        empleadosPorId,
                        personasPorId,
                        planillasPorEmpleado,
                        regimenesPorId,
                        pensionesPorEmpleado,
                        regimenesPensionariosPorId))
                .toList();
    }

    private MovimientoPlanillaResponseDto
    mapearMovimiento(MovimientoPlanilla mov,
                     Map<Long, Empleado> empleadosPorId,
                     Map<Long, Persona> personasPorId,
                     Map<Long, EmpleadoPlanilla> planillasPorEmpleado,
                     Map<Long, RegimenLaboral> regimenesPorId,
                     Map<Long, EmpleadoPension> pensionesPorEmpleado,
                     Map<Long, RegimenPensionario> regimenesPensionariosPorId) {

        MovimientoPlanillaResponseDto dto = new MovimientoPlanillaResponseDto();
        dto.setId(mov.getId());
        dto.setEmpleadoId(mov.getEmpleadoId());
        dto.setPeriodo(mov.getPeriodo());
        dto.setTotalIngresos(mov.getTotalIngresos());
        dto.setTotalDescuentos(mov.getTotalDescuentos());
        dto.setNetoPagar(mov.getNetoPagar());
        dto.setEstado(mov.getEstado());
        dto.setObservacion(mov.getObservacion());
        dto.setActivo(mov.getActivo());
        dto.setNeto50pctMinimo(mov.getNeto50pctMinimo());
        dto.setEstadoNeto(mov.getEstadoNeto());
        dto.setLoteId(mov.getLoteId());

        // Días laborados netos (30 − faltas − eventos), persistidos por el motor
        // (V012_03). Fallback a 30 SOLO para movimientos previos a V012_03 que aún
        // tienen DIAS_LABORADOS NULL. Este fallback se puede eliminar cuando ya no
        // existan movimientos con DIAS_LABORADOS IS NULL en ningún período (todos
        // regenerados): SELECT COUNT(*) FROM INDECI_MOVIMIENTO_PLANILLA
        //   WHERE DIAS_LABORADOS IS NULL AND ACTIVO = 1  →  0.
        dto.setDias(mov.getDiasLaborados() != null ? mov.getDiasLaborados() : 30);

        Empleado empleado = empleadosPorId.get(mov.getEmpleadoId());
        if (empleado != null) {
            Persona persona = personasPorId.get(empleado.getPersonaId());
            if (persona != null) {
                dto.setEmpleadoNombre(persona.getNombreCompleto());
                dto.setEmpleadoDni(persona.getDni());
            }
        }

        EmpleadoPlanilla planilla = planillasPorEmpleado.get(mov.getEmpleadoId());
        if (planilla != null) {
            RegimenLaboral regimen = regimenesPorId.get(planilla.getRegimenLaboralId());
            if (regimen != null) {
                dto.setRegimenLaboralCodigo(regimen.getCodigo());
                dto.setRegimenLaboralNombre(regimen.getNombre());
            }
        }
        
        EmpleadoPension pension = pensionesPorEmpleado.get(mov.getEmpleadoId());
        if (pension != null) {
            RegimenPensionario rp = regimenesPensionariosPorId.get(pension.getRegimenPensionarioId());
            if (rp != null) {
                dto.setRegimenPensionario(rp.getNombre());
            }
        }

        return dto;
    }

    private Map<Long, EmpleadoPlanilla>
    planillaActualPorEmpleado(List<Long> empleadoIds) {

        if (empleadoIds.isEmpty()) {
            return Map.of();
        }

        return planillaRepository
                .findByEmpleadoIdInAndActivo(empleadoIds, 1)
                .stream()
                .collect(Collectors.toMap(
                        EmpleadoPlanilla::getEmpleadoId,
                        Function.identity(),
                        this::planillaMasReciente));
    }

    private EmpleadoPlanilla
    planillaMasReciente(EmpleadoPlanilla a,
                        EmpleadoPlanilla b) {

        return PLANILLA_ACTUAL_ORDER.compare(a, b) >= 0 ? a : b;
    }

    // ==========================================
    // ELIMINAR PLANILLA
    // ==========================================

    @Auditable(accion = "ELIMINAR_PLANILLA")
    public void eliminar(Long id) {

        MovimientoPlanilla mov =
                repository.findById(id)
                        .orElseThrow(() ->
                                new NegocioException(
                                        "Planilla no encontrada"));

        // ==========================================
        // ELIMINAR DETALLES
        // ==========================================

        detalleRepository
                .deleteByMovimientoPlanillaId(
                        mov.getId());

        // ==========================================
        // DESACTIVAR CABECERA
        // ==========================================

        mov.setActivo(0);

        repository.save(mov);

        auditoriaContext.setDetalle(
                "Planilla eliminada ID: "
                        + id);
    }

    // ==========================================
    // CAMBIAR ESTADO
    // ==========================================

    @Auditable(accion = "CAMBIAR_ESTADO_PLANILLA")
    public void cambiarEstado(Long id,
                              String estado) {

        MovimientoPlanilla mov =
                repository.findById(id)
                        .orElseThrow(() ->
                                new NegocioException(
                                        "Planilla no encontrada"));

        mov.setEstado(estado);

        repository.save(mov);

        auditoriaContext.setDetalle(
                "Estado actualizado planilla ID: "
                        + id);
    }

    // ==========================================
    // RESUMEN POR META PRESUPUESTAL
    // (SPEC §12.2 PANTALLA-05 — RES.COMPROMISO + RES.METAS)
    // ==========================================

    /**
     * Agrupa los movimientos del período por meta presupuestal del empleado
     * (INDECI_EMPLEADO_PLANILLA.META). Por cada meta entrega PEA, ingresos,
     * ESSALUD empleador, aportes pensionarios y el costo total para la entidad.
     *
     * <p>{@code total = ingresos + ESSALUD} (costo entidad / CUC — LEY-07). Los
     * aportes pensionarios son descuento al trabajador y ya están dentro de los
     * ingresos: la columna es informativa y no se vuelve a sumar al total.
     *
     * <p>El empleado sin META configurada se agrupa bajo "SIN META".
     */
    public List<ResumenMetaDto> resumenPorMeta(String periodo) {

        // Mapa concepto -> CODIGO_MEF para clasificar los detalles.
        Map<Long, String> mefPorConcepto = new HashMap<>();
        for (ConceptoPlanilla c : conceptoRepository.findAll()) {
            mefPorConcepto.put(c.getId(), c.getCodigoMef());
        }

        Map<String, ResumenMetaDto> grupos = new LinkedHashMap<>();

        for (MovimientoPlanilla mov :
                repository.findByPeriodoAndActivo(periodo, 1)) {

            EmpleadoPlanilla ep = planillaRepository
                    .findFirstByEmpleadoIdAndActivo(mov.getEmpleadoId(), 1)
                    .orElse(null);

            String meta = (ep != null
                    && ep.getMeta() != null
                    && !ep.getMeta().isBlank())
                    ? ep.getMeta().trim()
                    : "SIN META";
            String centroCosto = (ep != null && ep.getCentroCosto() != null)
                    ? ep.getCentroCosto()
                    : "";

            double essalud = 0d;
            double aportes = 0d;
            for (MovimientoPlanillaDetalle det :
                    detalleRepository.findByMovimientoPlanillaId(mov.getId())) {
                String mef = mefPorConcepto.get(det.getConceptoPlanillaId());
                double monto = det.getMonto() != null ? det.getMonto() : 0d;
                if (MEF_ESSALUD.contains(mef)) {
                    essalud += monto;
                } else if (MEF_APORTES_PENSION.contains(mef)) {
                    aportes += monto;
                }
            }

            double ingresos = mov.getTotalIngresos() != null
                    ? mov.getTotalIngresos()
                    : 0d;
            double total = ingresos + essalud;

            ResumenMetaEmpleadoDto linea = new ResumenMetaEmpleadoDto();
            linea.setEmpleadoId(mov.getEmpleadoId());
            linea.setIngresos(redondear(ingresos));
            linea.setEssalud(redondear(essalud));
            linea.setAportes(redondear(aportes));
            linea.setTotal(redondear(total));

            final String centroCostoMeta = centroCosto;
            ResumenMetaDto grupo = grupos.computeIfAbsent(meta, m -> {
                ResumenMetaDto g = new ResumenMetaDto();
                g.setMeta(m);
                g.setCentroCosto(centroCostoMeta);
                g.setPea(0);
                g.setIngresos(0d);
                g.setEssalud(0d);
                g.setAportes(0d);
                g.setTotal(0d);
                g.setEmpleados(new ArrayList<>());
                return g;
            });

            grupo.setPea(grupo.getPea() + 1);
            grupo.setIngresos(grupo.getIngresos() + ingresos);
            grupo.setEssalud(grupo.getEssalud() + essalud);
            grupo.setAportes(grupo.getAportes() + aportes);
            grupo.setTotal(grupo.getTotal() + total);
            grupo.getEmpleados().add(linea);
        }

        List<ResumenMetaDto> resultado = new ArrayList<>(grupos.values());
        for (ResumenMetaDto g : resultado) {
            g.setIngresos(redondear(g.getIngresos()));
            g.setEssalud(redondear(g.getEssalud()));
            g.setAportes(redondear(g.getAportes()));
            g.setTotal(redondear(g.getTotal()));
        }
        resultado.sort(Comparator.comparing(ResumenMetaDto::getMeta));
        return resultado;
    }

    private static double redondear(double valor) {
        return BigDecimal.valueOf(valor)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
