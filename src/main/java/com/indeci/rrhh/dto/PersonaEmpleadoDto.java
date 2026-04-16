package com.indeci.rrhh.dto;

import lombok.Data;

@Data
public class PersonaEmpleadoDto {

    private String nombreCompleto;
    private String dni;
    private String email;
    private String telefono;
    private String direccion;
    private String distritoId;

    private String codigoInterno;
    private String estado;
}