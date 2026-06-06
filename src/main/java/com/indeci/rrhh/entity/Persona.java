package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "INDECI_PERSONA", schema = "GESTIONRRHH")
@Data
public class Persona {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "USER_ID")
    private Long userId;

    @Column(name = "NOMBRE_COMPLETO")
    private String nombreCompleto;

    @Column(name = "DNI")
    private String dni;

    @Column(name = "FECHA_NACIMIENTO")
    private java.util.Date fechaNacimiento;

    @Column(name = "EMAIL")
    private String email;

    @Column(name = "TELEFONO")
    private String telefono;

    @Column(name = "DIRECCION")
    private String direccion;

    @Column(name = "DISTRITO_ID")
    private String distritoId;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;
    
    @Column(name = "CONTACTO_EMERGENCIA_NOMBRE")
    private String contactoEmergenciaNombre;

    @Column(name = "CONTACTO_EMERGENCIA_PARENTESCO")
    private String contactoEmergenciaParentesco;

    @Column(name = "CONTACTO_EMERGENCIA_TELEFONO")
    private String contactoEmergenciaTelefono;
    
    
    @Column(name = "SEXO_ID")
    private Long sexoId;

    @Column(name = "ESTADO_CIVIL_ID")
    private Long estadoCivilId;

    @Column(name = "TIPO_DOCUMENTO_ID")
    private Long tipoDocumentoId;
    
    @Column(name = "NACIONALIDAD")
    private String nacionalidad;

    @Column(name = "RUC")
    private String ruc;

    @Column(name = "CORREO_INSTITUCIONAL")
    private String correoInstitucional;
    
    @Column(name = "PROFESION_ID")
    private Long profesionId;


    @Column(name = "GRADO_ACADEMICO_ID")
    private Long gradoAcademicoId;

    @Column(name = "FOTO_PERFIL")
    private String fotoPerfil;
    
    
    @Column(name = "TELEFONO_FIJO")
    private String telefonoFijo;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;
    
    @Column(name = "TIPO_PERSONA_ID")
    private Long tipoPersonaId;
    
    @Column(name = "NIVEL_INSTRUCCION_ID")
    private Long nivelInstruccionId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "TIPO_PERSONA_ID",
            insertable = false,
            updatable = false)
    private TipoPersona tipoPersona;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "NIVEL_INSTRUCCION_ID",
            insertable = false,
            updatable = false)
    private NivelInstruccion nivelInstruccion;
    
    

}