package com.indeci.rrhh.dto;

import java.time.LocalDateTime;

/**
 * B1 — Fila del historial de exports de planilla para el frontend.
 */
public record ExportHistorialDto(
        Long id,
        String periodo,
        String tipoArchivo,
        String nombreArchivo,
        Integer nroLineas,
        LocalDateTime fechaGenerado,
        String hashSha256
) {}
