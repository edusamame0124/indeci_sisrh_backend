package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * V010_76 — Configuración anual de Rentas de 4ta Categoría para CAS.
 *
 * <p>El motor de planilla ({@code GeneradorPlanillaService}) consulta esta tabla
 * por período/año fiscal para obtener UIT vigente y tasa de retención IR4ta,
 * reemplazando la lectura directa de TBL_PARAMETRO_REMUNERATIVO.</p>
 *
 * <p>Base normativa: TUO LIR Art. 33 inc. e) · D.S. 122-94-EF · SUNAT 3042.</p>
 */
@Entity
@Table(name = "INDECI_IR4TA_CONFIG_ANUAL", schema = "GESTIONRRHH")
@Data
public class Ir4taConfigAnual {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ANIO_FISCAL", nullable = false)
    private Integer anioFiscal;

    @Column(name = "VIGENCIA_INICIO", nullable = false)
    private LocalDate vigenciaInicio;

    @Column(name = "VIGENCIA_FIN")
    private LocalDate vigenciaFin;

    @Column(name = "UIT_VIGENTE", nullable = false, precision = 10, scale = 2)
    private BigDecimal uitVigente;

    /** Tasa de retención IR4ta (%): históricamente 8% (D.S. 122-94-EF). */
    @Column(name = "TASA_IR4TA", nullable = false, precision = 6, scale = 4)
    private BigDecimal tasaIr4ta;

    /** Base inafecta mensual = 75% UIT / 12 meses (Art. 33 inc. e TUO LIR). */
    @Column(name = "BASE_INAFECTA_IR4TA", precision = 10, scale = 2)
    private BigDecimal baseInafectaIr4ta;

    // ── V010_93 — Límites de suspensión y reglas de alerta ──────────────────
    /** Tope anual de suspensión para personal CAS general (S/). */
    @Column(name = "TOPE_ANUAL_GENERAL", precision = 12, scale = 2)
    private BigDecimal topeAnualGeneral;

    /** Tope anual de suspensión referencial para directores y similares (S/). */
    @Column(name = "TOPE_ANUAL_DIRECTOR", precision = 12, scale = 2)
    private BigDecimal topeAnualDirector;

    /** ¿El tope general aplica a CAS? (Sí por defecto). */
    @Column(name = "APLICA_CAS_GENERAL")
    private Integer aplicaCasGeneral;

    /** ¿El tope director/similar aplica a CAS? (No por defecto). */
    @Column(name = "APLICA_CAS_DIRECTOR")
    private Integer aplicaCasDirector;

    /** Umbral de alerta preventiva (% del tope). Default 80. */
    @Column(name = "PCT_ALERTA_PREV", precision = 5, scale = 2)
    private BigDecimal pctAlertaPrev;

    /** Umbral de alerta crítica (% del tope). Default 90. */
    @Column(name = "PCT_ALERTA_CRIT", precision = 5, scale = 2)
    private BigDecimal pctAlertaCrit;

    /** Código tributo SUNAT/PLAME (referencial 3042). */
    @Column(name = "CODIGO_SUNAT_PLAME", length = 10)
    private String codigoSunatPlame;

    /** Regla: calcular acumulado conocido por INDECI. */
    @Column(name = "FLG_CALC_ACUMULADO")
    private Integer flgCalcAcumulado;

    /** Regla: generar alerta al alcanzar el % preventivo. */
    @Column(name = "FLG_ALERTA_80")
    private Integer flgAlerta80;

    /** Regla: generar alerta al alcanzar el % crítico. */
    @Column(name = "FLG_ALERTA_90")
    private Integer flgAlerta90;

    /** Regla: marcar "requiere validación" al superar el tope. */
    @Column(name = "FLG_MARCAR_VALIDACION")
    private Integer flgMarcarValidacion;

    /** Regla: activar retención automática sin validación RR.HH. (0 por defecto). */
    @Column(name = "FLG_RETENCION_AUTO")
    private Integer flgRetencionAuto;

    @Column(name = "FUENTE_OFICIAL", nullable = false, length = 500)
    private String fuenteOficial;

    @Column(name = "URL_FUENTE_OFICIAL", length = 1000)
    private String urlFuenteOficial;

    @Column(name = "FECHA_PUBLICACION")
    private LocalDate fechaPublicacion;

    @Column(name = "OBSERVACION", length = 200)
    private String observacion;

    /** BORRADOR | VIGENTE | CERRADO | ANULADO */
    @Column(name = "ESTADO", nullable = false, length = 20)
    private String estado;

    @Column(name = "BLOQUEADO_POR_PLANILLA", nullable = false)
    private Integer bloqueadoPorPlanilla;

    @Column(name = "MOTIVO_ANULACION", length = 500)
    private String motivoAnulacion;

    @Column(name = "ANULADO_POR", length = 100)
    private String anuladoPor;

    @Column(name = "ANULADO_EN")
    private LocalDateTime anuladoEn;

    @Column(name = "CREADO_POR", nullable = false, length = 100)
    private String creadoPor;

    @Column(name = "CREADO_EN", nullable = false)
    private LocalDateTime creadoEn;

    @Column(name = "MODIFICADO_POR", length = 100)
    private String modificadoPor;

    @Column(name = "MODIFICADO_EN")
    private LocalDateTime modificadoEn;
}
