package com.indeci.rrhh.dto;

import java.time.LocalDate;

import lombok.Data;

@Data
public class TrayectoriaLaboralResponseDto {

    private Long id;

    private Long empleadoId;

    private Long cargoId;
    private String cargo;

    private Long oficinaId;
    private String oficina;

    private Long dependenciaId;
    private String dependencia;

    private Long sedeId;
    private String sede;

    private LocalDate fechaInicio;

    private LocalDate fechaFin;

    private Integer activo;
}