package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "INDECI_MOVIMIENTO_PLANILLA", schema = "GESTIONRRHH")
@Data
public class MovimientoPlanilla {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "EMPLEADO_ID")
    private Long empleadoId;

    @Column(name = "PERIODO")
    private String periodo;

    @Column(name = "OBSERVACION")
    private String observacion;

    @Column(name = "ACTIVO")
    private Integer activo;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;
    
    @Column(name = "TOTAL_INGRESOS")
    private Double totalIngresos;

    @Column(name = "TOTAL_DESCUENTOS")
    private Double totalDescuentos;

    @Column(name = "NETO_PAGAR")
    private Double netoPagar;

    @Column(name = "ESTADO")
    private String estado;

    // ============================================================
    // Spec 010 / V010_14 — Validación neto 50% (REGLA SERVIR-07, §5.4)
    // ============================================================

    /** Umbral = (TOTAL_INGRESOS − IR5ta − aporte_pensionario − judicial) × 0.5. */
    @Column(name = "NETO_50PCT_MINIMO")
    private Double neto50pctMinimo;

    /** 'BIEN' | 'NETO_NO_VA'. Eje independiente del campo ESTADO (flujo). */
    @Column(name = "ESTADO_NETO")
    private String estadoNeto;
}