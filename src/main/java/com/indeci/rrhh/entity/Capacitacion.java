package com.indeci.rrhh.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(
        name = "INDECI_CAPACITACION",
        schema = "GESTIONRRHH")
@Data
public class Capacitacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "EMPLEADO_ID")
    private Long empleadoId;

    @Column(name = "NOMBRE_CURSO")
    private String nombreCurso;

    @Column(name = "INSTITUCION")
    private String institucion;

    @Column(name = "HORAS")
    private Integer horas;

    @Column(name = "FECHA_INICIO")
    private LocalDate fechaInicio;

    @Column(name = "FECHA_FIN")
    private LocalDate fechaFin;

    @Column(name = "CERTIFICADO")
    private Integer certificado;

    @Column(name = "LEGAJO_DOCUMENTO_ID")
    private Long legajoDocumentoId;

    @Column(name = "ACTIVO")
    private Integer activo;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;
}