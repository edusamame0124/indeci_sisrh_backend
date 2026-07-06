package com.indeci.rrhh.service;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.ReintegroMontoDto;
import com.indeci.rrhh.entity.ReintegroMonto;
import com.indeci.rrhh.repository.MovimientoPlanillaRepository;
import com.indeci.rrhh.repository.ReintegroMontoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReintegroMontoService {

    private final ReintegroMontoRepository reintegroMontoRepository;
    private final MovimientoPlanillaRepository movimientoPlanillaRepository;

    @Transactional
    public void registrarReintegro(ReintegroMontoDto dto) {
        // Origen OPCIONAL: solo si se indica movimiento origen, debe existir.
        if (dto.getMovimientoOrigenId() != null) {
            movimientoPlanillaRepository.findById(dto.getMovimientoOrigenId())
                    .orElseThrow(() -> new NegocioException("Movimiento origen no encontrado"));
        }

        ReintegroMonto reintegro = new ReintegroMonto();
        reintegro.setEmpleadoId(dto.getEmpleadoId());
        reintegro.setPeriodoDestino(dto.getPeriodoDestino());
        reintegro.setPeriodoOrigen(dto.getPeriodoOrigen());
        reintegro.setMovimientoOrigenId(dto.getMovimientoOrigenId());
        reintegro.setConceptoOrigenCodigo(dto.getConceptoOrigenCodigo());
        reintegro.setMonto(dto.getMonto());
        // El enum garantiza un motivo válido; se persiste su nombre canónico.
        reintegro.setMotivo(dto.getMotivo().name());
        reintegro.setSustento(dto.getSustento());
        reintegro.setEstadoPago("PENDIENTE");

        reintegroMontoRepository.save(reintegro);
    }
}
