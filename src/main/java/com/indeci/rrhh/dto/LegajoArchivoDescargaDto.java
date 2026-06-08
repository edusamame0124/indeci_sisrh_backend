package com.indeci.rrhh.dto;

/**
 * Binario de un documento de legajo listo para streaming HTTP.
 */
public record LegajoArchivoDescargaDto(
        byte[] contenido,
        String nombreArchivo,
        String mediaType) {}
