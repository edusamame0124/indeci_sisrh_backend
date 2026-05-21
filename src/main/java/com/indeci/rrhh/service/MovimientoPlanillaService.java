package com.indeci.rrhh.service;

import com.indeci.audit.annotation.Auditable;
import com.indeci.audit.context.AuditoriaContext;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.MovimientoPlanillaResponseDto;
import com.indeci.rrhh.dto.ResumenMetaDto;
import com.indeci.rrhh.dto.ResumenMetaEmpleadoDto;
import com.indeci.rrhh.entity.ConceptoPlanilla;
import com.indeci.rrhh.entity.EmpleadoPlanilla;
import com.indeci.rrhh.entity.MovimientoPlanilla;
import com.indeci.rrhh.entity.MovimientoPlanillaDetalle;
import com.indeci.rrhh.repository.ConceptoPlanillaRepository;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.MovimientoPlanillaDetalleRepository;
import com.indeci.rrhh.repository.MovimientoPlanillaRepository;

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
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MovimientoPlanillaService {

    private final MovimientoPlanillaRepository repository;

    private final MovimientoPlanillaDetalleRepository
            detalleRepository;

    private final EmpleadoPlanillaRepository
            planillaRepository;

    private final ConceptoPlanillaRepository
            conceptoRepository;

    private final AuditoriaContext auditoriaContext;

    /** CODIGO_MEF del aporte ESSALUD empleador (sin y con EPS). */
    private static final Set<String> MEF_ESSALUD =
            Set.of("06001", "06002");

    /** CODIGO_MEF de los aportes pensionarios del trabajador (ONP/AFP). */
    private static final Set<String> MEF_APORTES_PENSION =
            Set.of("05001", "05002", "05003", "05004");

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

        MovimientoPlanillaResponseDto dto =
                new MovimientoPlanillaResponseDto();

        dto.setId(mov.getId());

        dto.setEmpleadoId(
                mov.getEmpleadoId());

        dto.setPeriodo(
                mov.getPeriodo());

        dto.setObservacion(
                mov.getObservacion());

        dto.setActivo(
                mov.getActivo());

        dto.setEstado(
                mov.getEstado());

        dto.setTotalIngresos(
                mov.getTotalIngresos());

        dto.setTotalDescuentos(
                mov.getTotalDescuentos());

        dto.setNetoPagar(
                mov.getNetoPagar());

        dto.setNeto50pctMinimo(
                mov.getNeto50pctMinimo());

        dto.setEstadoNeto(
                mov.getEstadoNeto());

        return dto;
    }

    // ==========================================
    // LISTAR PERIODO
    // ==========================================

    public List<MovimientoPlanillaResponseDto>
    listarPeriodo(String periodo) {

        return repository
                .findByPeriodoAndActivo(
                        periodo,
                        1)
                .stream()
                .map(mov -> {

                    MovimientoPlanillaResponseDto dto =
                            new MovimientoPlanillaResponseDto();

                    dto.setId(mov.getId());

                    dto.setEmpleadoId(
                            mov.getEmpleadoId());

                    dto.setPeriodo(
                            mov.getPeriodo());

                    dto.setObservacion(
                            mov.getObservacion());

                    dto.setActivo(
                            mov.getActivo());

                    dto.setEstado(
                            mov.getEstado());

                    dto.setTotalIngresos(
                            mov.getTotalIngresos());

                    dto.setTotalDescuentos(
                            mov.getTotalDescuentos());

                    dto.setNetoPagar(
                            mov.getNetoPagar());

                    dto.setNeto50pctMinimo(
                            mov.getNeto50pctMinimo());

                    dto.setEstadoNeto(
                            mov.getEstadoNeto());

                    return dto;

                }).toList();
    }

    // ==========================================
    // LISTAR POR EMPLEADO (historial — PANTALLA-08)
    // ==========================================

    public List<MovimientoPlanillaResponseDto>
    listarPorEmpleado(Long empleadoId) {

        return repository
                .findByEmpleadoIdAndActivo(empleadoId, 1)
                .stream()
                .map(mov -> {

                    MovimientoPlanillaResponseDto dto =
                            new MovimientoPlanillaResponseDto();

                    dto.setId(mov.getId());
                    dto.setEmpleadoId(mov.getEmpleadoId());
                    dto.setPeriodo(mov.getPeriodo());
                    dto.setObservacion(mov.getObservacion());
                    dto.setActivo(mov.getActivo());
                    dto.setEstado(mov.getEstado());
                    dto.setTotalIngresos(mov.getTotalIngresos());
                    dto.setTotalDescuentos(mov.getTotalDescuentos());
                    dto.setNetoPagar(mov.getNetoPagar());
                    dto.setNeto50pctMinimo(mov.getNeto50pctMinimo());
                    dto.setEstadoNeto(mov.getEstadoNeto());

                    return dto;

                }).toList();
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