package com.indeci.rrhh.dto;

import lombok.Data;

@Data
public class PersonaEmpleadoResponseDto {

    private Long id;
    /** Employee PK when linked; null if no employment row exists yet. */
    private Long empleadoId;
    private String nombreCompleto;
    private String dni;
    private String email;
    private String telefono;
    private String direccion;
    private String distritoId;

    private String codigoInterno;
    private String estado;
}