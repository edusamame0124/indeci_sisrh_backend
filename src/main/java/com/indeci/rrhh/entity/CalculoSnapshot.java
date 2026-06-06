package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * FASE 2 / V010_55 — Snapshot de trazabilidad del cálculo de planilla.
 *
 * <p>Registra, por {@code (empleadoId, periodo, regla)}, los parámetros y
 * magnitudes que el motor usó para obtener un resultado. Permite que la
 * pantalla de explicación responda "¿cómo se obtuvo S/ X?" sin recalcular y da
 * reproducibilidad para auditoría (D.L. 1451).</p>
 *
 * <p>Diseño <b>solo añadido</b>: el motor escribe estas filas como efecto
 * lateral de la generación; ningún cálculo depende de leerlas. Al regenerar se
 * desactivan ({@code activo=0}) las filas previas del par
 * {@code (empleadoId, periodo)} y se insertan nuevas.</p>
 */
@Entity
@Table(name = "INDECI_CALCULO_SNAPSHOT", schema = "GESTIONRRHH")
@Data
public class CalculoSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "EMPLEADO_ID", nullable = false)
    private Long empleadoId;

    /** Período YYYY-MM. */
    @Column(name = "PERIODO", nullable = false)
    private String periodo;

    /** Movimiento de planilla que originó el snapshot (puede ser null). */
    @Column(name = "MOVIMIENTO_PLANILLA_ID")
    private Long movimientoPlanillaId;

    /** GENERAL | IR4TA_CAS | IR5TA | SUBSIDIO. */
    @Column(name = "REGLA", nullable = false)
    private String regla;

    /** Base sobre la que se aplicó la regla (base afecta / reconocida). */
    @Column(name = "BASE_CALCULO")
    private BigDecimal baseCalculo;

    /** Monto resultante de la regla (retención / diferencia / etc.). */
    @Column(name = "RESULTADO")
    private BigDecimal resultado;

    /** Fórmula legible: "base 1800 - inafecto 1500 = 300 × 8% = 24.00". */
    @Column(name = "FORMULA")
    private String formula;

    /** Payload JSON con los valores específicos de la regla. */
    @Lob
    @Column(name = "PARAMETROS_JSON")
    private String parametrosJson;

    /** Versión de parámetros usados (típicamente el año fiscal de vigencia). */
    @Column(name = "VERSION_PARAMETROS")
    private String versionParametros;

    /** 1 = vigente; 0 = reemplazado por una regeneración posterior. */
    @Column(name = "ACTIVO", nullable = false)
    private Integer activo;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "CREATED_BY")
    private String createdBy;
}
