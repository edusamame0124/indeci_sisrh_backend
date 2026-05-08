package com.indeci.rrhh.service;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.MovimientoPlanillaDetalleResponseDto;
import com.indeci.rrhh.entity.ConceptoPlanilla;
import com.indeci.rrhh.entity.MovimientoPlanilla;
import com.indeci.rrhh.entity.MovimientoPlanillaDetalle;
import com.indeci.rrhh.repository.ConceptoPlanillaRepository;
import com.indeci.rrhh.repository.MovimientoPlanillaDetalleRepository;
import com.indeci.rrhh.repository.MovimientoPlanillaRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MovimientoPlanillaDetalleService {

    private final MovimientoPlanillaRepository
            movimientoRepository;

    private final MovimientoPlanillaDetalleRepository
            detalleRepository;

    private final ConceptoPlanillaRepository
            conceptoRepository;

    // ==========================================
    // LISTAR DETALLE EMPLEADO
    // ==========================================

    public List<MovimientoPlanillaDetalleResponseDto>
    listarDetalle(Long empleadoId,
                  String periodo) {

        MovimientoPlanilla mov =
                movimientoRepository
                        .findByEmpleadoIdAndPeriodoAndActivo(
                                empleadoId,
                                periodo,
                                1)
                        .orElseThrow(() ->
                                new NegocioException(
                                        "Planilla no encontrada"));

        return detalleRepository
                .findByMovimientoPlanillaId(
                        mov.getId())
                .stream()
                .map(det -> {

                    ConceptoPlanilla concepto =
                            conceptoRepository
                                    .findById(
                                            det.getConceptoPlanillaId())
                                    .orElseThrow();

                    MovimientoPlanillaDetalleResponseDto dto =
                            new MovimientoPlanillaDetalleResponseDto();

                    dto.setId(det.getId());

                    dto.setConceptoPlanillaId(
                            concepto.getId());

                    dto.setCodigoConcepto(
                            concepto.getCodigo());

                    dto.setConcepto(
                            concepto.getNombre());

                    dto.setTipoConcepto(
                            concepto.getTipo());
                    

                    dto.setMonto(
                            det.getMonto());

                    dto.setCantidad(
                            det.getCantidad());

                    dto.setObservacion(
                            det.getObservacion());
                    

                    return dto;

                }).toList();
    }
}