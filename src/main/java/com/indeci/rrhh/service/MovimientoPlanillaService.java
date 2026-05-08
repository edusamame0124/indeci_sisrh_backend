package com.indeci.rrhh.service;

import com.indeci.audit.annotation.Auditable;
import com.indeci.audit.context.AuditoriaContext;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.MovimientoPlanillaResponseDto;
import com.indeci.rrhh.entity.MovimientoPlanilla;
import com.indeci.rrhh.repository.MovimientoPlanillaDetalleRepository;
import com.indeci.rrhh.repository.MovimientoPlanillaRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MovimientoPlanillaService {

    private final MovimientoPlanillaRepository repository;

    private final MovimientoPlanillaDetalleRepository
            detalleRepository;

    private final AuditoriaContext auditoriaContext;

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
}