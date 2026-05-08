package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "INDECI_EMPLEADO", schema = "GESTIONRRHH")
@Data
public class Empleado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "PERSONA_ID")
    private Long personaId;

    @Column(name = "CODIGO_INTERNO")
    private String codigoInterno;

    @Column(name = "ESTADO")
    private String estado;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;
    
    @Column(name = "PROFESION_ID")
    private Long profesionId;

    @Column(name = "GRADO_ACADEMICO_ID")
    private Long gradoAcademicoId;

    @Column(name = "CONADIS_CODIGO")
    private String conadisCodigo;
    
    @Column(name = "TIPO_PERSONAL_ID")
    private Long tipoPersonalId;
    
    @Column(name = "CODIGO_SISPER")
    private String codigoSisper;
    
}