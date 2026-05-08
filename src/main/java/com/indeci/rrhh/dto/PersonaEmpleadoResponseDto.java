package com.indeci.rrhh.dto;

import lombok.Data;

@Data
public class PersonaEmpleadoResponseDto {

    private Long id;

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

    // =====================================
    // SEXO
    // =====================================

    private Long sexoId;

    private String sexo;

    // =====================================
    // ESTADO CIVIL
    // =====================================

    private Long estadoCivilId;

    private String estadoCivil;

    // =====================================
    // TIPO DOCUMENTO
    // =====================================

    private Long tipoDocumentoId;

    private String tipoDocumento;

    // =====================================
    // OTROS
    // =====================================

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
    private String profesion;

    private Long gradoAcademicoId;

    private String gradoAcademico;

    private String conadisCodigo;




 


    // =====================================
    // TIPO PERSONAL
    // =====================================

    private Long tipoPersonalId;

    private String tipoPersonal;
}