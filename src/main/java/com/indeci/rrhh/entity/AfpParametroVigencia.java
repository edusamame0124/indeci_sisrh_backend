package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Parámetros AFP por vigencia de período. Spec V010_70.
 * TUO SPP D.S. 054-97-EF · SBS Res. 8514-2012.
 */
@Entity
@Table(name = "INDECI_AFP_PARAMETRO_VIGENCIA", schema = "GESTIONRRHH")
@Data
public class AfpParametroVigencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "AFP_ID", nullable = false)
    private IndAfp afp;

    /** Formato YYYYMM. */
    @Column(name = "PERIODO_INICIO", nullable = false, length = 6)
    private String periodoInicio;

    /** Null = vigencia abierta. Formato YYYYMM. */
    @Column(name = "PERIODO_FIN", length = 6)
    private String periodoFin;

    @Column(name = "APORTE_OBLIGATORIO_PCT", nullable = false, precision = 6, scale = 4)
    private BigDecimal aporteObligatorioPct;

    @Column(name = "COMISION_FLUJO_PCT", nullable = false, precision = 6, scale = 4)
    private BigDecimal comisionFlujoPct;

    @Column(name = "COMISION_SALDO_ANUAL_PCT", nullable = false, precision = 6, scale = 4)
    private BigDecimal comisionSaldoAnualPct;

    @Column(name = "PRIMA_SEGURO_PCT", nullable = false, precision = 6, scale = 4)
    private BigDecimal primaSeguroPct;

    @Column(name = "REMUNERACION_MAXIMA_ASEG", nullable = false, precision = 10, scale = 2)
    private BigDecimal remuneracionMaximaAseg;

    @Column(name = "FUENTE_OFICIAL", nullable = false, length = 200)
    private String fuenteOficial;

    @Column(name = "URL_FUENTE_OFICIAL", length = 500)
    private String urlFuenteOficial;

    @Column(name = "FECHA_PUBLICACION")
    private LocalDate fechaPublicacion;

    @Column(name = "OBSERVACION", length = 500)
    private String observacion;

    /** VIGENTE | PROGRAMADO | CERRADO | INACTIVO. */
    @Column(name = "ESTADO", nullable = false, length = 20)
    private String estado;

    @Column(name = "BLOQUEADO_POR_PLANILLA", nullable = false)
    private Integer bloqueadoPorPlanilla;

    @Column(name = "CREADO_POR", nullable = false, length = 100)
    private String creadoPor;

    @Column(name = "CREADO_EN", nullable = false)
    private LocalDateTime creadoEn;

    @Column(name = "MODIFICADO_POR", length = 100)
    private String modificadoPor;

    @Column(name = "MODIFICADO_EN")
    private LocalDateTime modificadoEn;

    // V010_72 — Anulación lógica (Eliminar en UI = ANULADO en BD)

    @Column(name = "MOTIVO_ANULACION", length = 1000)
    private String motivoAnulacion;

    @Column(name = "ANULADO_POR", length = 100)
    private String anuladoPor;

    @Column(name = "ANULADO_EN")
    private LocalDateTime anuladoEn;
}
