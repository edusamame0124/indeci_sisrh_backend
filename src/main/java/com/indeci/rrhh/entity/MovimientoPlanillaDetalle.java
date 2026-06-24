package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "INDECI_MOVIMIENTO_PLANILLA_DET",
        schema = "GESTIONRRHH"
)
@Data
public class MovimientoPlanillaDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "MOVIMIENTO_PLANILLA_ID")
    private Long movimientoPlanillaId;

    @Column(name = "CONCEPTO_PLANILLA_ID")
    private Long conceptoPlanillaId;

    @Column(name = "MONTO")
    private Double monto;

    @Column(name = "CANTIDAD")
    private Double cantidad;

    @Column(name = "OBSERVACION")
    private String observacion;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    // ============================================================
    // SPEC_CONCEPTOS_PLANILLA P3 (V010_99) — snapshot histórico del concepto
    //   Congela nombre/código/tipo al grabar el detalle (§8/D5). Renombrar o
    //   reconfigurar el concepto NO altera la boleta histórica. Nullable: filas
    //   previas a V010_99 hacen fallback al concepto vivo.
    // ============================================================

    /** Snapshot del CÓDIGO del concepto al grabar. */
    @Column(name = "CONCEPTO_CODIGO")
    private String conceptoCodigo;

    /** Snapshot del NOMBRE del concepto al grabar. */
    @Column(name = "CONCEPTO_NOMBRE")
    private String conceptoNombre;

    /** Snapshot del TIPO_CONCEPTO al grabar. */
    @Column(name = "CONCEPTO_TIPO")
    private String conceptoTipo;

    // ============================================================
    // Spec 010 / V010_09 — campos v2 del detalle de planilla
    // ============================================================

    /** SPEC §5.4 — diferencial pagado adicional (SISPER-059/060). */
    @Column(name = "PAGO_DIFERENCIAL")
    private Double pagoDiferencial;

    /** SPEC §5.4 — días reintegrados. */
    @Column(name = "DIAS_REINTEGRO")
    private Integer diasReintegro;

    /** SPEC §5.4 — monto de reintegro: (remun/30) * dias (SISPER-041). */
    @Column(name = "MONTO_REINTEGRO")
    private Double montoReintegro;

    /** SPEC §5.7 — BW: IR 5ta sobre remuneración mensual. */
    @Column(name = "IR_5TA_REMUNERACION")
    private Double ir5taRemuneracion;

    /** SPEC §5.7 — BX: IR 5ta sobre aguinaldo (jul/dic). */
    @Column(name = "IR_5TA_AGUINALDO")
    private Double ir5taAguinaldo;

    /** SPEC §5.7 — BY = BW + BX (SISPER-820). */
    @Column(name = "IR_5TA_TOTAL")
    private Double ir5taTotal;

    /** SPEC §5.5 — ESSALUD empleador 6.75% cuando el trabajador tiene EPS (SISPER-907). */
    @Column(name = "ESSALUD_6_75")
    private Double essalud675;

    /** SPEC §5.5 — copago EPS 2.25% del trabajador (SISPER-725). */
    @Column(name = "COPAGO_EPS")
    private Double copagoEps;

    /** SPEC §5.4 — umbral mínimo para validar el neto (REGLA SERVIR-07). */
    @Column(name = "NETO_50PCT_MINIMO")
    private Double neto50pctMinimo;

    /** SPEC §5.4 — 'BIEN' | 'NETO_NO_VA'. */
    @Column(name = "ESTADO_NETO")
    private String estadoNeto;
}