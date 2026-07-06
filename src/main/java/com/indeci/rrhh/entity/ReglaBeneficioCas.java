package com.indeci.rrhh.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Data;

/**
 * Track B F4 — Regla versionada de beneficio CAS (Ley 32563). Distingue
 * {@code AGUINALDO_CAS_LEGACY} (monto fijo, solo históricos) de las
 * gratificaciones vigentes {@code GRATIFICACION_FIESTAS_PATRIAS_CAS} (julio) y
 * {@code GRATIFICACION_NAVIDAD_CAS} (diciembre) = 100% de la remuneración
 * mensual CAS. Controla activación/vigencia/fórmula/régimen del beneficio; el
 * motor la consulta por período (no hay switch manual en la generación).
 */
@Entity
@Table(name = "INDECI_REGLA_BENEFICIO_CAS", schema = "GESTIONRRHH")
@Data
public class ReglaBeneficioCas {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "CODIGO_BENEFICIO", nullable = false)
    private String codigoBeneficio;

    @Column(name = "MES_APLICA", nullable = false)
    private Integer mesAplica;

    /** {@code FIJO} (legacy) | {@code PCT_REMUNERACION} (gratificación 100%). */
    @Column(name = "MONTO_TIPO", nullable = false)
    private String montoTipo;

    /** Factor sobre la remuneración cuando {@code MONTO_TIPO = PCT_REMUNERACION} (1.0 = 100%). */
    @Column(name = "FACTOR")
    private BigDecimal factor;

    /** Concepto operativo a grabar (INDECI_CONCEPTO_PLANILLA.CODIGO_MEF). */
    @Column(name = "CODIGO_MEF")
    private String codigoMef;

    @Column(name = "REGIMEN", nullable = false)
    private String regimen;

    @Column(name = "VIGENCIA_DESDE", nullable = false)
    private LocalDate vigenciaDesde;

    @Column(name = "VIGENCIA_HASTA")
    private LocalDate vigenciaHasta;

    @Column(name = "ESTADO", nullable = false)
    private String estado;

    @Column(name = "NORMA_SUSTENTO")
    private String normaSustento;

    @Column(name = "REQUIERE_APROB_RRHH")
    private String requiereAprobRrhh;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;
}
