package com.indeci.rrhh.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * F2.6 — Metadata del documento que se sube al legajo del empleado.
 *
 * <p>El archivo viaja como {@code MultipartFile} en el endpoint; este DTO
 * contiene los campos descriptivos (no binarios).</p>
 */
@Data
public class LegajoDocumentoDto {

    private Long empleadoId;
    private Long categoriaId;
    /** Opcional. */
    private Long subcategoriaId;

    /** Título legible (ej. "Certificado médico mayo 2026"). */
    private String nombreDocumento;

    /** Fecha del documento (no la fecha de carga). Opcional. */
    private LocalDate fechaDocumento;

    private String observacion;

    /**
     * Identifica de dónde viene el documento: "MANUAL" / "EVENTO" /
     * "ASISTENCIA" / "IMPORTACION". Opcional, default "MANUAL".
     */
    private String origen;

    /** Id de la fuente (ej. EmpleadoEvento.id si origen='EVENTO'). Opcional. */
    private Long referenciaId;
}
