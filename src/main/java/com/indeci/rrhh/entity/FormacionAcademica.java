package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "INDECI_FORMACION_ACADEMICA",
        schema = "GESTIONRRHH")
@Data
public class FormacionAcademica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "EMPLEADO_ID")
    private Long empleadoId;

    @Column(name = "NIVEL_INSTRUCCION_ID")
    private Long nivelInstruccionId;

    @Column(name = "GRADO_ACADEMICO_ID")
    private Long gradoAcademicoId;
    
    @Column(name = "INSTITUCION")
    private String institucion;

    @Column(name = "CARRERA")
    private String carrera;

    @Column(name = "FECHA_INICIO")
    private LocalDate fechaInicio;

    @Column(name = "FECHA_FIN")
    private LocalDate fechaFin;

    @Column(name = "EGRESADO")
    private Integer egresado;

    @Column(name = "BACHILLER")
    private Integer bachiller;

    @Column(name = "TITULADO")
    private Integer titulado;

    @Column(name = "NRO_TITULO")
    private String nroTitulo;

    @Column(name = "LEGAJO_DOCUMENTO_ID")
    private Long legajoDocumentoId;

    @Column(name = "ACTIVO")
    private Integer activo;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "NIVEL_INSTRUCCION_ID",
            insertable = false,
            updatable = false)
    private NivelInstruccion nivelInstruccion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "GRADO_ACADEMICO_ID",
            insertable = false,
            updatable = false)
    private GradoAcademico gradoAcademico;
}