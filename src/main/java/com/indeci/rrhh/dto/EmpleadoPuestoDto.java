package com.indeci.rrhh.dto;

import lombok.Data;

@Data
public class EmpleadoPuestoDto {

    private Long empleadoId;

    private String cargo;

    private Long nivelId;

    private Long sedeId;

    private Long oficinaId;

    private Long jefeId;

    // =====================================
    // NUEVOS
    // =====================================

    private Long estructuraOrganicaId;

    private Long dependenciaId;
}