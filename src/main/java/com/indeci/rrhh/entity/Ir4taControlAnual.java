package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * V010_94 — Control anual de la suspensión de retención IR4ta por trabajador.
 *
 * <p>Una fila por (EMPLEADO_ID, ANIO_FISCAL). Registra el monitoreo del tope
 * anual de suspensión: acumulado conocido por INDECI (recalculado desde
 * planillas cerradas), estado de control, detección de exceso y la
 * confirmación documentada del reinicio de retención por RR.HH.</p>
 *
 * <p><b>El TIPO_TOPE es un flag MANUAL</b> fijado por RR.HH. (default
 * GENERAL_CAS). El acumulado NO es editable a mano: lo recalcula
 * {@code Ir4taControlAnualService}.</p>
 *
 * <p>Esta tabla NO altera el motor ni las planillas históricas.</p>
 */
@Entity
@Table(name = "INDECI_IR4TA_CONTROL_ANUAL", schema = "GESTIONRRHH")
@Data
public class Ir4taControlAnual {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "EMPLEADO_ID", nullable = false)
    private Long empleadoId;

    @Column(name = "ANIO_FISCAL", nullable = false)
    private Integer anioFiscal;

    /** FK opcional a la constancia INDECI_SUSPENSION_4TA vigente. */
    @Column(name = "SUSPENSION_4TA_ID")
    private Long suspension4taId;

    /** Flag manual RR.HH.: GENERAL_CAS | DIRECTOR_SIMILAR. */
    @Column(name = "TIPO_TOPE", nullable = false, length = 20)
    private String tipoTope;

    /** Snapshot del tope aplicado al trabajador (S/). */
    @Column(name = "TOPE_ANUAL_APLICADO", precision = 12, scale = 2)
    private BigDecimal topeAnualAplicado;

    /** Acumulado conocido por INDECI (recalculado, no editable a mano). */
    @Column(name = "ACUMULADO_INDECI", nullable = false, precision = 14, scale = 2)
    private BigDecimal acumuladoIndeci;

    @Column(name = "PCT_CONSUMIDO", precision = 7, scale = 2)
    private BigDecimal pctConsumido;

    /** Último período calculado (YYYY-MM o YYYYMM). */
    @Column(name = "ULTIMO_PERIODO_CALC", length = 7)
    private String ultimoPeriodoCalc;

    /**
     * VIGENTE | ALERTA_80_PORCIENTO | ALERTA_90_PORCIENTO | CERCA_DEL_TOPE |
     * EXCEDE_TOPE_REQUIERE_VALIDACION | REINICIO_CONFIRMADO | RETENCION_ACTIVA |
     * VENCIDA | ANULADA.
     */
    @Column(name = "ESTADO_CONTROL", nullable = false, length = 40)
    private String estadoControl;

    @Column(name = "PERIODO_EXCESO", length = 7)
    private String periodoExceso;

    @Column(name = "FECHA_DETECCION_EXCESO")
    private LocalDateTime fechaDeteccionExceso;

    /** Período desde el cual se reinicia la retención (confirmado por RR.HH.). */
    @Column(name = "PERIODO_REINICIO", length = 7)
    private String periodoReinicio;

    @Column(name = "SUSTENTO_REINICIO", length = 1000)
    private String sustentoReinicio;

    @Column(name = "CONFIRMADO_POR", length = 100)
    private String confirmadoPor;

    @Column(name = "CONFIRMADO_EN")
    private LocalDateTime confirmadoEn;

    @Column(name = "OBSERVACION", length = 500)
    private String observacion;

    @Column(name = "CREADO_POR", nullable = false, length = 100)
    private String creadoPor;

    @Column(name = "CREADO_EN", nullable = false)
    private LocalDateTime creadoEn;

    @Column(name = "MODIFICADO_POR", length = 100)
    private String modificadoPor;

    @Column(name = "MODIFICADO_EN")
    private LocalDateTime modificadoEn;
}
