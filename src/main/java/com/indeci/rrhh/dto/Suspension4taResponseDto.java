package com.indeci.rrhh.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * FASE 1 — Constancia de suspensión de 4ta con el badge de vigencia resuelto
 * para la pantalla (VIGENTE | VENCIDA | NO_REGISTRADA por ausencia).
 */
@Data
public class Suspension4taResponseDto {

    private Long id;
    private Long empleadoId;
    private String nroConstancia;
    private LocalDate fechaEmision;
    private LocalDate fechaVigIni;
    private LocalDate fechaVigFin;
    private String estado;
    private String observacion;
    private Long legajoDocumentoId;

    /** Badge calculado contra la fecha actual: VIGENTE | VENCIDA | INACTIVA. */
    private String estadoVigencia;
}
