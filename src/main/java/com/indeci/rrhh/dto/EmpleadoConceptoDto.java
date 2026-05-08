package com.indeci.rrhh.dto;

import lombok.Data;

@Data
public class EmpleadoConceptoDto {

    private Long empleadoId;

    private Long conceptoPlanillaId;

    private Double monto;

    private Double porcentaje;

    private String formula;
}