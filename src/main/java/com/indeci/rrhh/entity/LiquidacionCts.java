package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Feature 016 — Liquidación de CTS Trunca por cese (una fila = un vínculo/período).
 * Naming INDECI_*; PK IDENTITY; schema GESTIONRRHH. Ver V012_15.
 */
@Entity
@Table(name = "INDECI_LIQUIDACION_CTS", schema = "GESTIONRRHH")
@Data
public class LiquidacionCts {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "EMPLEADO_ID", nullable = false)
    private Long empleadoId;

    /** Vínculo EXACTO liquidado (anti-colisión con reingresos históricos). */
    @Column(name = "EMPLEADO_PLANILLA_ID", nullable = false)
    private Long empleadoPlanillaId;

    @Column(name = "PERIODO", nullable = false)
    private String periodo;

    @Column(name = "REGIMEN_LABORAL_ID", nullable = false)
    private Long regimenLaboralId;

    @Column(name = "REGIMEN_CODIGO", nullable = false)
    private String regimenCodigo;

    /** CTS276 | CTSSERVIR. */
    @Column(name = "ESTRATEGIA", nullable = false)
    private String estrategia;

    @Column(name = "FECHA_INGRESO", nullable = false)
    private LocalDate fechaIngreso;

    @Column(name = "FECHA_CESE", nullable = false)
    private LocalDate fechaCese;

    @Column(name = "ANIOS", nullable = false)
    private Integer anios;

    @Column(name = "MESES", nullable = false)
    private Integer meses;

    @Column(name = "DIAS", nullable = false)
    private Integer dias;

    @Column(name = "DIAS_EFECTIVOS_FRACCION", nullable = false)
    private Integer diasEfectivosFraccion;

    @Column(name = "BASE_COMPUTABLE", nullable = false)
    private BigDecimal baseComputable;

    @Column(name = "FACTOR_ANUAL", nullable = false)
    private BigDecimal factorAnual;

    @Column(name = "DIVISOR_DIAS", nullable = false)
    private Integer divisorDias;

    @Column(name = "MONTO_ANIOS", nullable = false)
    private BigDecimal montoAnios;

    @Column(name = "MONTO_FRACCION", nullable = false)
    private BigDecimal montoFraccion;

    @Column(name = "MONTO_TOTAL", nullable = false)
    private BigDecimal montoTotal;

    /** PENDIENTE | CALCULADO | CERRADO (inmutable). */
    @Column(name = "ESTADO", nullable = false)
    private String estado;

    @Column(name = "SNAPSHOT_ID")
    private Long snapshotId;

    @Column(name = "NRO_CONSTANCIA")
    private String nroConstancia;

    @Column(name = "OBSERVACION")
    private String observacion;

    @Column(name = "CREATED_BY")
    private String createdBy;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_BY")
    private String updatedBy;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;
}
