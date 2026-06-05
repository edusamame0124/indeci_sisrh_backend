package com.indeci.rrhh.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * FASE 1 — Alta/edición de una constancia de suspensión de retención de 4ta.
 */
@Data
public class Suspension4taRequestDto {

    private Long empleadoId;
    private String nroConstancia;
    private LocalDate fechaEmision;
    private LocalDate fechaVigIni;
    private LocalDate fechaVigFin;
    private String observacion;
    /** FK opcional al PDF de la constancia en el legajo. */
    private Long legajoDocumentoId;
}
