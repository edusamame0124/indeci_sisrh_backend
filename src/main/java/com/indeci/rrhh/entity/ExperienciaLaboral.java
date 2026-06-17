package com.indeci.rrhh.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(
        name = "INDECI_EXPERIENCIA_LABORAL",
        schema = "GESTIONRRHH")
@Data
public class ExperienciaLaboral {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "EMPLEADO_ID")
    private Long empleadoId;

    @Column(name = "EMPRESA")
    private String empresa;

    @Column(name = "CARGO")
    private String cargo;

    @Column(name = "FECHA_INICIO")
    private LocalDate fechaInicio;

    @Column(name = "FECHA_FIN")
    private LocalDate fechaFin;

    @Column(name = "FUNCIONES")
    private String funciones;

    @Column(name = "LEGAJO_DOCUMENTO_ID")
    private Long legajoDocumentoId;

    @Column(name = "ACTIVO")
    private Integer activo;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;
}