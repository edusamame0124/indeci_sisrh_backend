package com.indeci.rrhh.dto;

import lombok.Data;

/**
 * Body de PUT /api/rrhh/persona/me — autoservicio del empleado.
 * Solo campos de contacto, no datos regulatorios (DNI, nombre, régimen, etc.).
 */
@Data
public class MiPerfilUpdateDto {

    private String telefono;

    private String correoPersonal;

    private String direccion;

    private String contactoEmergenciaNombre;

    private String contactoEmergenciaParentesco;

    private String contactoEmergenciaTelefono;
}
