package com.indeci.rrhh.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * B3 / M14 — Planilla MCPP disponible para exportar en un período (resumen).
 * NRO_PLANILLA = 0 mientras no se haya emitido (se asigna al generar el archivo).
 */
@Data
public class McppPlanillaDisponibleDto {

    /** 01 SERVIR | 03 CAS | 12 Judiciales. */
    private String tipoPlanilla;
    private int nroPlanilla;
    private int totalRegistros;
    private BigDecimal totalIngresos;
    private BigDecimal totalDescuentos;
}
