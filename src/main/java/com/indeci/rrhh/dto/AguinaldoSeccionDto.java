package com.indeci.rrhh.dto;

import java.util.List;

import lombok.Data;

/**
 * Track B — Sección "AGUINALDO/GRATIFICACIÓN" de la boleta consolidada (opción A).
 * Se arma solo en presentación (BoletaDataService); el movimiento AGUINALDO
 * permanece separado en BD. Desagregado por concepto (ingreso + descuento judicial
 * si existe) + neto del aguinaldo. {@code null} en la boleta cuando no hay aguinaldo.
 */
@Data
public class AguinaldoSeccionDto {
    private String titulo;
    private List<ConceptoBoletaDto> ingresos;
    private List<ConceptoBoletaDto> descuentos;
    private Double totalIngresos;
    private Double totalDescuentos;
    private Double neto;
}
