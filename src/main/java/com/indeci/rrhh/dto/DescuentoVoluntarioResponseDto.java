package com.indeci.rrhh.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * Spec 010 / M07 — Response de un descuento voluntario, con el nombre del
 * concepto MEF resuelto para mostrar en pantalla.
 */
@Data
public class DescuentoVoluntarioResponseDto {

    private Long id;
    private Long empleadoId;
    private String codigoSisper;
    private Long conceptoPlanillaId;
    private String conceptoNombre;
    private String codigoMef;
    private Double montoMensual;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private String estado;
}
