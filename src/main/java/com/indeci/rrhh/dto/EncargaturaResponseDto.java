package com.indeci.rrhh.dto;

import java.time.LocalDate;

import lombok.Data;

/**
 * F5.2 — Vista para la UI de Encargaturas.
 *
 * <p>Espejo enriquecido de {@link com.indeci.rrhh.entity.EmpleadoEncargatura}
 * con nombres y DNI del titular y del encargado para renderizar la tabla
 * sin queries adicionales del frontend.</p>
 */
@Data
public class EncargaturaResponseDto {

    private Long id;

    private Long empleadoTitularId;
    private String titularNombre;
    private String titularDni;

    private Long empleadoEncargId;
    private String encargadoNombre;
    private String encargadoDni;

    private LocalDate fechaInicio;
    private LocalDate fechaFin;

    private String resolucion;

    /** ACTIVO | CULMINADO. */
    private String estado;
}
