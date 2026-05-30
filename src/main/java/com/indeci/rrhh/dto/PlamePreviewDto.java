package com.indeci.rrhh.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * B3 / M09 — Resumen previo a la descarga del PLAME: conteo de líneas por archivo
 * y totales del período.
 */
@Data
public class PlamePreviewDto {

    private String periodo;
    private int remLineas;
    private int jorLineas;
    private int snlLineas;
    private BigDecimal totalIngresos;
    private BigDecimal totalDescuentos;
}
