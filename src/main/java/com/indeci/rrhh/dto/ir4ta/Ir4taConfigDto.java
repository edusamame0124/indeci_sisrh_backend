package com.indeci.rrhh.dto.ir4ta;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** DTO de lectura de configuración anual IR4ta. */
@Data
public class Ir4taConfigDto {
    private Long id;
    private Integer anioFiscal;
    private LocalDate vigenciaInicio;
    private LocalDate vigenciaFin;
    private BigDecimal uitVigente;
    private BigDecimal tasaIr4ta;
    private BigDecimal baseInafectaIr4ta;
    private String fuenteOficial;
    private String urlFuenteOficial;
    private LocalDate fechaPublicacion;
    private String observacion;
    // ── V010_93 — Límites de suspensión y reglas ────────────────────────────
    private BigDecimal topeAnualGeneral;
    private BigDecimal topeAnualDirector;
    private boolean aplicaCasGeneral;
    private boolean aplicaCasDirector;
    private BigDecimal pctAlertaPrev;
    private BigDecimal pctAlertaCrit;
    private String codigoSunatPlame;
    private boolean flgCalcAcumulado;
    private boolean flgAlerta80;
    private boolean flgAlerta90;
    private boolean flgMarcarValidacion;
    private boolean flgRetencionAuto;
    private String estado;
    private boolean bloqueadoPorPlanilla;
    private String creadoPor;
    private LocalDateTime creadoEn;
    private String modificadoPor;
    private LocalDateTime modificadoEn;
}
