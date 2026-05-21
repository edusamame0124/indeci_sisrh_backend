package com.indeci.rrhh.dto;

import lombok.Data;

import java.time.LocalDate;

/** Cuerpo POST para registrar un préstamo (SPEC §12.2 PANTALLA-08). */
@Data
public class PrestamoDto {

    private Long empleadoId;

    private String descripcion;

    private Double montoTotal;

    private Integer numeroCuotas;

    private Double cuotaMensual;

    private LocalDate fechaInicio;
}
