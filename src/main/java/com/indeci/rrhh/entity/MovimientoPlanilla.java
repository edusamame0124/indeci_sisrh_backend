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

    /**
     * V012_03 — Días laborados netos del período (30 − faltas − eventos). Lo
     * calcula y persiste el motor. Nullable: movimientos previos a V012_03 quedan
     * en null y la lista/boleta caen al fallback 30 hasta regenerarse.
     */
    @Column(name = "DIAS_LABORADOS")
    private Integer diasLaborados;

    // ============================================================
    // Spec 010 / V010_14 — Validación neto 50% (REGLA SERVIR-07, §5.4)
    // ============================================================

    /** Umbral = (TOTAL_INGRESOS − IR5ta − aporte_pensionario − judicial) × 0.5. */
    @Column(name = "NETO_50PCT_MINIMO")
    private Double neto50pctMinimo;

    /** 'BIEN' | 'NETO_NO_VA'. Eje independiente del campo ESTADO (flujo). */
    @Column(name = "ESTADO_NETO")
    private String estadoNeto;

    // B2 — Trazabilidad: vigencias AFP/ONP activas al momento del cálculo.
    // Nullable: movimientos previos a V010_71 quedan sin valor.

    @Column(name = "AFP_PARAM_VIGENCIA_ID")
    private Long afpParamVigenciaId;

    @Column(name = "ONP_PARAM_VIGENCIA_ID")
    private Long onpParamVigenciaId;
    
    // ============================================================
    // Spec 011 / Aislamiento de Pagos (Contratos Múltiples)
    // ============================================================
    
    @Column(name = "EMPLEADO_PUESTO_ID")
    private Long empleadoPuestoId;

    @Column(name = "TIPO_PLANILLA")
    private String tipoPlanilla;

    @Column(name = "FECHA_INICIO_PAGO")
    private java.time.LocalDate fechaInicioPago;

    @Column(name = "FECHA_FIN_PAGO")
    private java.time.LocalDate fechaFinPago;

    @Column(name = "LOTE_ID")
    private Long loteId;

    // ============================================================
    // Spec 012 / Fase 4 — Snapshots inmutables de metadata
    // ============================================================
    
    @Column(name = "REGIMEN_LABORAL_SNAPSHOT")
    private String regimenLaboralSnapshot;

    @Column(name = "NIVEL_REMUNERATIVO_SNAPSHOT")
    private String nivelRemunerativoSnapshot;

    @Column(name = "CUENTA_BANCARIA_SNAPSHOT")
    private String cuentaBancariaSnapshot;

    @Column(name = "MODALIDAD_SNAPSHOT")
    private String modalidadSnapshot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "LOTE_ID", insertable = false, updatable = false)
    private PlanillaLote lote;
}