package com.indeci.rrhh.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * Spec 010 / M07 — Request para alta/edición de un descuento voluntario.
 * El CODIGO_SISPER no se envía: el servicio lo copia del concepto MEF.
 */
@Data
public class DescuentoVoluntarioDto {

    private Long empleadoId;
    private Long conceptoPlanillaId;
    private Double montoMensual;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
}
