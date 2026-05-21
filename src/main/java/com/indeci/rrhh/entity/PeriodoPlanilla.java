package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "INDECI_PERIODO_PLANILLA", schema = "GESTIONRRHH")
@Data
public class PeriodoPlanilla {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "PERIODO")
    private String periodo;

    @Column(name = "FECHA_INICIO")
    private LocalDate fechaInicio;

    @Column(name = "FECHA_FIN")
    private LocalDate fechaFin;

    @Column(name = "ESTADO")
    private String estado;

    @Column(name = "OBSERVACION")
    private String observacion;

    @Column(name = "FECHA_CIERRE")
    private LocalDateTime fechaCierre;

    /** Spec 011 / LEY-05 — número de certificación presupuestal (Ley 28411). */
    @Column(name = "NRO_CERT_PRESUP")
    private String nroCertPresup;

    /** Spec 011 — fecha/hora en que el período pasó a APROBADO. */
    @Column(name = "FECHA_APROBACION")
    private LocalDateTime fechaAprobacion;

    @Column(name = "ACTIVO")
    private Integer activo;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;
}