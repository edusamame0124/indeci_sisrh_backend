package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "INDECI_CONCEPTO_PLANILLA", schema = "GESTIONRRHH")
@Data
public class ConceptoPlanilla {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "CODIGO")
    private String codigo;

    @Column(name = "NOMBRE")
    private String nombre;

    /** Tipo legacy free-form: INGRESO|DESCUENTO|APORTE. Mantener por compat con frontend Spec 009. */
    @Column(name = "TIPO")
    private String tipo;

    @Column(name = "NATURALEZA")
    private String naturaleza;

    @Column(name = "ACTIVO")
    private Integer activo;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    // ============================================================
    // SPEC_CONCEPTOS_PLANILLA P1 (V010_97) — ciclo de vida + RTPS
    // ============================================================

    /**
     * Ciclo de vida: BORRADOR | EN_REVISION | ACTIVO | CERRADO | ANULADO (§8/D1).
     * En P1 el motor sigue leyendo {@link #activo}; {@code estado} es la fuente
     * de verdad del flujo y mantiene {@code activo} sincronizado como espejo
     * legacy (ACTIVO→1; CERRADO/ANULADO→0).
     */
    @Column(name = "ESTADO")
    private String estado;

    /** FK a {@code INDECI_CONCEPTO_RTPS(CODIGO)} — clasificación externa PDT 601. */
    @Column(name = "RTPS_CODIGO")
    private String rtpsCodigo;

    /**
     * SPEC_CONCEPTOS_PLANILLA P3 (V010_99) — n.º de versión por CÓDIGO.
     * {@code crearNuevaVersion()} clona el concepto con {@code max(version)+1}
     * para el mismo CÓDIGO. DEFAULT 1 (conceptos no versionados quedan en 1).
     */
    @Column(name = "VERSION")
    private Integer version;

    // ============================================================
    // Spec 010 — campos MEF (Ley 32448 / SPEC §6.1)
    // ============================================================

    /** Código oficial AIRHSP. Obligatorio para pagar (LEY-01). */
    @Column(name = "CODIGO_MEF")
    private String codigoMef;

    /** Código SISPER del concepto (V010_08 / SPEC §6.3). Ej: 905, 816, 071. */
    @Column(name = "CODIGO_SISPER")
    private String codigoSisper;

    /** Código Anexo 2 SUNAT para el PDT 601 (V010_27 / B3). Ej: 0601, 0618, 1019, 2039. */
    @Column(name = "CODIGO_PLAME_SUNAT")
    private String codigoPlameSunat;

    /** Código MCPP Web para los archivos PLL*.TXT (V010_27 / B3). Ej: 0131, 0668, 0210. */
    @Column(name = "CODIGO_MCPP")
    private String codigoMcpp;

    /**
     * Código de tributo SUNAT (V010_49 / FASE1). Ej: 3042 = Retención Renta 4ta
     * Categoría. Independiente de {@code CODIGO_MEF} (catálogo de ingresos
     * MEF/AIRHSP): una retención tributaria no tiene código MEF.
     */
    @Column(name = "CODIGO_TRIBUTO_SUNAT")
    private String codigoTributoSunat;

    /** REMUNERATIVO | NO_REMUNERATIVO | DESCUENTO | APORTE_TRABAJADOR | APORTE_EMPLEADOR. */
    @Column(name = "TIPO_CONCEPTO")
    private String tipoConcepto;

    /**
     * SPEC_CONCEPTOS_PLANILLA §13 (V010_100) — "Tipo de Concepto" funcional (SISPER).
     * FK a {@code INDECI_TIPO_CONCEPTO_INTERNO(CODIGO)}. El {@link #tipoConcepto}
     * (motor) se DERIVA de la {@code CLASIFICACION_MOTOR} de la fila referenciada;
     * el motor sigue leyendo {@code TIPO_CONCEPTO}.
     */
    @Column(name = "TIPO_CONCEPTO_INTERNO")
    private String tipoConceptoInterno;

    /** S/N — afecto a retención 5ta categoría. */
    @Column(name = "AFECTO_IR_5TA")
    private String afectoIr5ta;

    /** S/N — afecto a aporte pensionario (ONP/AFP). */
    @Column(name = "AFECTO_APORTE_PENS")
    private String afectoAportePens;

    /** S/N — afecto a ESSALUD (base aporte empleador). */
    @Column(name = "AFECTO_ESSALUD")
    private String afectoEssalud;

    /** S/N — Monto Único Consolidado (LEY-07). */
    @Column(name = "ES_MUC")
    private String esMuc;

    /** S/N — Costo Único Consolidado (LEY-07). */
    @Column(name = "ES_CUC")
    private String esCuc;

    /**
     * Régimen(es) al que aplica el concepto.
     * Valor único: "276" | "728" | "1057" | "SERVIR" | "TODOS".
     * CSV (F1.5b): "728,1057" para conceptos del pacto colectivo MEF.
     * {@code null} o "TODOS" → aplica a todos los regímenes.
     */
    @Column(name = "REGIMEN_APLICABLE")
    private String regimenAplicable;

    @Column(name = "FECHA_VIG_INI")
    private LocalDate fechaVigIni;

    @Column(name = "FECHA_VIG_FIN")
    private LocalDate fechaVigFin;

    /**
     * F1.5b — S/N. Si "S", el motor v3 (motor.v3.prorrateo.enabled=true)
     * prorratea el monto del EmpleadoConcepto por días laborados:
     * {@code monto / 30 × dias_laborados}.
     * Si "N" (default), el monto se aplica completo independientemente de los días.
     */
    @Column(name = "ES_PRORRATEABLE")
    private String esProrrateable;

    /**
     * SPEC_CONCEPTOS_PLANILLA §14 / P4 (V010_101) — Modo de cálculo.
     * Valores: MONTO_FIJO | MONTO_INDIVIDUAL | PORCENTAJE | RESULTADO_MOTOR |
     * IMPORTACION. DEFAULT 'RESULTADO_MOTOR'.
     *
     * <p>MODO_CALCULO: metadata/intención; el motor NO se ramifica por él (P4).
     * Solo documenta cómo se origina el monto para guiar al operador; el motor v3
     * sigue valorizando cada concepto como hoy, sin consumir este campo.</p>
     */
    @Column(name = "MODO_CALCULO")
    private String modoCalculo;
}