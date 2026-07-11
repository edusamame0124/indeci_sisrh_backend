package com.indeci.rrhh.dto;

import java.util.List;

/**
 * Respuesta paginada del padrón vacacional — SPEC_VACACIONES F4.
 * Espeja {@code PersonaResumenPageDto} para consistencia con el resto del sistema.
 */
public record PadronVacacionalPageDto(
        List<PadronVacacionalRowDto> content,
        long totalElements,
        int totalPages,
        int pageNumber,
        int pageSize) {
}
