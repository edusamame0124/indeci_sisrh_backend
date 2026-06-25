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

                    // SPEC_CONCEPTOS_PLANILLA P3 — preferir el snapshot histórico del
                    // detalle; el concepto vivo es solo fallback para filas previas a
                    // V010_99 (snapshot null). Si el concepto fue anulado/eliminado, el
                    // snapshot evita romper la lista.
                    ConceptoPlanilla concepto =
                            conceptoRepository
                                    .findById(
                                            det.getConceptoPlanillaId())
                                    .orElse(null);

                    MovimientoPlanillaDetalleResponseDto dto =
                            new MovimientoPlanillaDetalleResponseDto();

                    dto.setId(det.getId());

                    dto.setConceptoPlanillaId(
                            det.getConceptoPlanillaId());

                    dto.setCodigoConcepto(
                            preferirSnapshot(
                                    det.getConceptoCodigo(),
                                    concepto != null ? concepto.getCodigo() : null));

                    dto.setConcepto(
                            preferirSnapshot(
                                    det.getConceptoNombre(),
                                    concepto != null ? concepto.getNombre() : null));

                    dto.setTipoConcepto(
                            preferirSnapshot(
                                    det.getConceptoTipo(),
                                    concepto != null ? concepto.getTipo() : null));

                    dto.setMonto(
                            det.getMonto());

                    dto.setCantidad(
                            det.getCantidad());

                    dto.setObservacion(
                            det.getObservacion());

                    return dto;

                }).toList();
    }

    /**
     * Devuelve el valor del snapshot histórico si está presente; si no
     * (fila anterior a V010_99), cae al valor vivo del concepto.
     */
    private static String preferirSnapshot(String snapshot, String vivo) {
        return (snapshot != null && !snapshot.isBlank()) ? snapshot : vivo;
    }
}