package com.indeci.rrhh.dto;

import lombok.Data;

@Data
public class EmpleadoConceptoResponseDto {

    private Long id;

    private Long conceptoPlanillaId;

    private String concepto;

    private Double monto;

    private Double porcentaje;

    private String formula;

    private Integer activo;
}