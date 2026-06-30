package com.indeci.rrhh.dto;

import lombok.Data;

@Data
public class ConceptoBoletaDto {
    private String codigo;
    private String concepto;
    private Double monto;
    private String observacion;
}
