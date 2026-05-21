package com.indeci.rrhh.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * B3 / M09 — Response de una suspensión, con la descripción del catálogo resuelta.
 */
@Data
public class SuspensionResponseDto {

    private Long id;
    private Long empleadoId;
    private String codSuspension;
    private String descripcionSuspension;
    private String tipoPlame;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private Integer diasAfectos;
    private String nroCmp;
    private String nroResolucion;
    private String observacion;
    private String estado;
}
