package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Spec 010 / V010_10 — Descuento voluntario del empleado (12 tipos SISPER, §6.4).
 */
@Entity
@Table(name = "INDECI_DESCUENTO_VOLUNTARIO", schema = "GESTIONRRHH")
@Data
public class DescuentoVoluntario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "EMPLEADO_ID", nullable = false)
    private Long empleadoId;

    /** Código SISPER del descuento (ej: 735, 722). */
    @Column(name = "CODIGO_SISPER", nullable = false)
    private String codigoSisper;

    @Column(name = "CONCEPTO_PLANILLA_ID", nullable = false)
    private Long conceptoPlanillaId;

    @Column(name = "MONTO_MENSUAL", nullable = false)
    private Double montoMensual;

    @Column(name = "FECHA_INICIO", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "FECHA_FIN")
    private LocalDate fechaFin;

    /** ACTIVO | INACTIVO. */
    @Column(name = "ESTADO", nullable = false)
    private String estado;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;
}
