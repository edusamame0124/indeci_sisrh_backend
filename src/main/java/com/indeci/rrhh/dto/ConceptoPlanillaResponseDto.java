package com.indeci.rrhh.dto;

import lombok.Data;

@Data
public class ConceptoPlanillaResponseDto {

    private Long id;
    private String codigo;
    private String nombre;
    private String tipo;
    private String naturaleza;
    private Integer activo;
}