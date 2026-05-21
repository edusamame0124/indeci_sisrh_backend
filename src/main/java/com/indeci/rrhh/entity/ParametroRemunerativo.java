package com.indeci.rrhh.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Spec 010 — Parámetro remunerativo vigente por año fiscal y régimen laboral.
 * Cumple REGLA-02 (no hardcodear valores monetarios/porcentuales).
 */
@Entity
@Table(name = "INDECI_PARAMETRO_REMUNERATIVO", schema = "GESTIONRRHH")
@Data
public class ParametroRemunerativo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "CODIGO_PARAMETRO", nullable = false)
    private String codigoParametro;

    @Column(name = "ANIO_FISCAL", nullable = false)
    private Integer anioFiscal;

    /** NULL = aplica a todos los regímenes. */
    @Column(name = "REGIMEN_LABORAL_ID")
    private Long regimenLaboralId;

    @Column(name = "VALOR_NUMERICO", nullable = false, precision = 18, scale = 6)
    private BigDecimal valorNumerico;

    /** 'PEN' | 'PCT' | 'MIN' | 'DIAS'. */
    @Column(name = "UNIDAD")
    private String unidad;

    @Column(name = "FECHA_VIG_INI", nullable = false)
    private LocalDate fechaVigIni;

    @Column(name = "FECHA_VIG_FIN")
    private LocalDate fechaVigFin;

    @Column(name = "ACTIVO", nullable = false)
    private Integer activo;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;
}
