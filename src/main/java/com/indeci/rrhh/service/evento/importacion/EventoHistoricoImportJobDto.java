package com.indeci.rrhh.service.evento.importacion;

import com.indeci.rrhh.service.evento.importacion.EventoHistoricoImportDtos.ImportResultDto;

/**
 * V012_42 F2 — Estado de un job asíncrono de import histórico de eventos (polling).
 *
 * @param jobId      identificador del job (UUID)
 * @param estado     EN_COLA | PROCESANDO | COMPLETADO | ERROR
 * @param porcentaje 0..100 (100 solo cuando estado = COMPLETADO)
 * @param fase       descripción legible de la fase actual (p. ej. "Fila 120 de 454")
 * @param resultado  {@link ImportResultDto}, solo cuando estado = COMPLETADO
 * @param error      mensaje de error, solo cuando estado = ERROR
 */
public record EventoHistoricoImportJobDto(
        String jobId,
        String estado,
        int porcentaje,
        String fase,
        ImportResultDto resultado,
        String error) {
}
