package com.indeci.rrhh.dto;

import java.time.LocalDate;

import lombok.Data;

@Data
public class EmpleadoPuestoResponseDto {

    private Long id;

    private Long cargoId;



    private Long nivelId;



    private Long oficinaId;

    private Long sedeId;

    private Long dependenciaId;
    
    private Long estructuraOrganicaId;

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

/** F5.1 — fecha de inicio del puesto (timeline cargo histórico). */
private LocalDate fechaInicio;

/** F5.1 — fecha de fin del puesto; null cuando es el cargo vigente. */
private LocalDate fechaFin;
}