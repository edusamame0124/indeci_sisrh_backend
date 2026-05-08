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

    // =====================================
    // NUEVOS
    // =====================================

    private Long estructuraOrganicaId;

    private Long dependenciaId;
    
    ///Descripcion
    ///
private String nivel;

private String sede;

private String oficina;

private String estructuraOrganica;

private String dependencia;

private String jefe;
}