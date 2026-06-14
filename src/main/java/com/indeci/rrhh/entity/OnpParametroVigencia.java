package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Parámetros ONP por vigencia de período. Spec V010_70.
 * Ley 19990 Art. 7 — tasa histórica 13%.
 */
@Entity
@Table(name = "INDECI_ONP_PARAMETRO_VIGENCIA", schema = "GESTIONRRHH")
@Data
public class OnpParametroVigencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Formato YYYYMM. */
    @Column(name = "PERIODO_INICIO", nullable = false, length = 6)
    private String periodoInicio;

    /** Null = vigencia abierta. Formato YYYYMM. */
    @Column(name = "PERIODO_FIN", length = 6)
    private String periodoFin;

    @Column(name = "APORTE_ONP_PCT", nullable = false, precision = 6, scale = 4)
    private BigDecimal aporteOnpPct;

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
}
