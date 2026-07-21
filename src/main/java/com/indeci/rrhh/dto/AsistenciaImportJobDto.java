package com.indeci.rrhh.dto;

/**
 * Estado de un job asíncrono de validación de asistencia (Opción B — progreso real por polling).
 *
 * @param jobId      identificador del job (UUID)
 * @param estado     EN_COLA | PROCESANDO | COMPLETADO | ERROR
 * @param porcentaje 0..100 (100 solo cuando estado = COMPLETADO)
 * @param fase       descripción legible de la fase actual (p. ej. "Guardando")
 * @param resultado  el resultado, solo cuando estado = COMPLETADO. Genérico:
 *                   {@code AsistenciaImportPreviewDto} (validar/confirmar) o
 *                   {@code AsistenciaValidacionBatchDto} (ejecutar cálculo). Jackson serializa el
 *                   tipo real → el frontend castea según la operación que inició.
 * @param error      mensaje de error, solo cuando estado = ERROR
 */
public record AsistenciaImportJobDto(
        String jobId,
        String estado,
        int porcentaje,
        String fase,
        Object resultado,
        String error) {}
