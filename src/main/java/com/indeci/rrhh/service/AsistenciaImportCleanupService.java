package com.indeci.rrhh.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.indeci.rrhh.repository.AsistenciaImportacionFilaRepository;

import lombok.RequiredArgsConstructor;

/**
 * Purga por lotes de INDECI_ASISTENCIA_IMPORTACION_FILA (Data Lifecycle, P0).
 *
 * Cada llamada a {@link #purgarLote(LocalDateTime)} borra como máximo un lote
 * y confirma en su propia transacción: así el undo de Oracle nunca acumula
 * más que un lote a la vez, sin importar cuántas filas venzan en total.
 * El orquestador ({@code AsistenciaImportCleanupJob}) invoca este método en
 * bucle hasta que no queden filas vencidas.
 */
@Service
@RequiredArgsConstructor
public class AsistenciaImportCleanupService {

    private final AsistenciaImportacionFilaRepository filaRepository;

    @Value("${asistencia.import.limpieza.tamano-lote:500}")
    private int tamanoLote;

    @Transactional
    public int purgarLote(LocalDateTime corte) {
        List<Long> ids = filaRepository.buscarIdsAnterioresA(corte, PageRequest.of(0, tamanoLote));
        if (ids.isEmpty()) {
            return 0;
        }
        return filaRepository.eliminarPorIds(ids);
    }
}
