package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * F1.4 Motor v3 — Reintegro de días para un empleado en un período.
 *
 * <p>Una sola fila vigente por (empleadoId, periodo) — protegido por
 * INDECI_EMP_REINTEGRO_UK. Re-cargar implica baja lógica del anterior
 * (ACTIVO = 0) y nuevo INSERT.</p>
 *
 * <p>El motor PASO 5b consume esta entidad: si existe vigente para el
 * período, se prorratea la base remunerativa por {@code diasReintegro}
 * usando la fórmula <code>base / 30 × dias</code>. F1.4 graba la entidad
 * pero <b>no</b> conecta al flujo de cálculo todavía — esa conexión
 * ocurre en F1.5 junto con MONTO_CONTRATO e incrementos DS.</p>
 *
 * <p>Schema NO hardcodeado (ver [[claude-md-aspiracional]]).</p>
 */
@Entity
@Table(name = "INDECI_EMPLEADO_REINTEGRO")
@Data
public class EmpleadoReintegro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "EMPLEADO_ID", nullable = false)
    private Long empleadoId;

    /** Formato YYYYMM (ej. "202605"). */
    @Column(name = "PERIODO", nullable = false, length = 6)
    private String periodo;

    /** Rango 1..31 (CK en BD). */
    @Column(name = "DIAS_REINTEGRO", nullable = false)
    private Integer diasReintegro;

    @Column(name = "MOTIVO", nullable = false, length = 200)
    private String motivo;

    @Column(name = "OBSERVACION", length = 500)
    private String observacion;

    @Column(name = "ACTIVO", nullable = false)
    private Integer activo;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "CREATED_BY", length = 60)
    private String createdBy;
}
