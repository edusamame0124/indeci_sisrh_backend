package com.indeci.rrhh.dto;

import lombok.Data;

@Data
public class PersonaEmpleadoDto {

    // =====================================
    // PERSONA
    // =====================================

    private String nombreCompleto;

    private String dni;

    private String email;

    private String telefono;

    private String direccion;

    private String distritoId;

    private String contactoEmergenciaNombre;

    private String contactoEmergenciaParentesco;

    private String contactoEmergenciaTelefono;

    private Long sexoId;

    private Long estadoCivilId;

    private Long tipoDocumentoId;

    private String nacionalidad;

    private String ruc;

    private String correoInstitucional;

    private String fotoPerfil;

    // =====================================
    // EMPLEADO
    // =====================================

    private String codigoInterno;

    private String estado;

    private Long profesionId;

    private Long gradoAcademicoId;

    private String conadisCodigo;

    private Long tipoPersonalId;
    
}