package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * B3 / M09 / V010_30 — Evento de suspensión/licencia de un empleado.
 * Fuente de los días del archivo .snl y del ajuste de horas del .jor.
 */
@Entity
@Table(name = "INDECI_SUSPENSION", schema = "GESTIONRRHH")
@Data
public class Suspension {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "EMPLEADO_ID", nullable = false)
    private Long empleadoId;

    /** FK a INDECI_CAT_SUSPENSION_SUNAT.COD_SUSPENSION. */
    @Column(name = "COD_SUSPENSION", nullable = false)
    private String codSuspension;

    @Column(name = "FECHA_INICIO", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "FECHA_FIN", nullable = false)
    private LocalDate fechaFin;

    @Column(name = "DIAS_AFECTOS", nullable = false)
    private Integer diasAfectos;

    /** Colegiatura médica / CITT cuando el tipo lo exige. */
    @Column(name = "NRO_CMP")
    private String nroCmp;

    @Column(name = "NRO_RESOLUCION")
    private String nroResolucion;

    @Column(name = "OBSERVACION")
    private String observacion;

    /** ACTIVO | ANULADO. */
    @Column(name = "ESTADO", nullable = false)
    private String estado;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;
}
