package com.indeci.rrhh.dto;

import lombok.Data;

@Data
public class EmpleadoPuestoResponseDto {

    private Long id;

    private Long cargoId;



    private Long nivelId;



    private Long oficinaId;

    private Long jefeId;

    private Integer activo;
    
    private Long tipoCargoId;



    // =====================================
    // NUEVOS
    // =====================================

    
    ///Descripcion
    ///
private String nivel;

private String sede;
private String cargo;

private String oficina;

private String estructuraOrganica;

private String dependencia;

private String jefe;

private String tipoCargo;
}