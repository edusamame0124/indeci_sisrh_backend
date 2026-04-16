package com.indeci.rrhh.dto;

import lombok.Data;

@Data
public class EmpleadoPuestoResponseDto {

    private Long id;
    private String cargo;
    private Long nivelId;
    private Long sedeId;
    private Long oficinaId;
    private Long jefeId;
    private Integer activo;
}