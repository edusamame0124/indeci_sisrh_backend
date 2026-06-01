package com.indeci.rrhh.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * F3.1 — Respuesta del endpoint
 * {@code GET /api/rrhh/empleado/{id}/explicacion/{periodo}}.
 *
 * <p>Vista agregada lista para Ficha 360 (UI F3.1c/d). Cabecera con
 * identificación + totales + lista de líneas detalladas. NO modifica el motor
 * de cálculo; solo lee {@link com.indeci.rrhh.entity.MovimientoPlanilla} y
 * {@link com.indeci.rrhh.entity.MovimientoPlanillaDetalle} ya grabados y los
 * enriquece con info del catálogo de conceptos.</p>
 *
 * <p>Si el movimiento no existe → {@code aplica = false}; el resto en null/0
 * y la UI muestra estado "Sin planilla generada" con CTA "Generar planilla".</p>
 */
public record ExplicacionPlanillaDto(
        boolean aplica,
        Long empleadoId,
        String periodo,
        ExplicacionCabeceraDto cabecera,
        ExplicacionTotalesDto totales,
        List<ExplicacionLineaDto> lineas) {

    public static ExplicacionPlanillaDto noAplica(Long empleadoId, String periodo) {
        return new ExplicacionPlanillaDto(
                false, empleadoId, periodo, null, null, List.of());
    }

    /** Bloque de identificación que va en el header de Ficha 360. */
    public record ExplicacionCabeceraDto(
            String nombreCompleto,
            String dni,
            String regimenLaboralCodigo,
            String regimenLaboralNombre,
            String meta,
            String banco,
            String numeroCuenta,
            String cci) {}

    /** KPIs que van en las 6 cards superiores. */
    public record ExplicacionTotalesDto(
            BigDecimal totalIngresos,
            BigDecimal totalDescuentos,
            BigDecimal aporteTrabajador,
            BigDecimal aporteEmpleador,
            BigDecimal netoPagar,
            /** 'BIEN' | 'NETO_NO_VA'. */
            String estadoNeto,
            BigDecimal neto50pctMinimo,
            BigDecimal montoSistemaAirhsp,
            BigDecimal montoAirhsp,
            BigDecimal diferenciaAirhsp,
            /** 'CONCILIADO' | 'PENDIENTE' | null si no hay AIRHSP cargado. */
            String estadoAirhsp) {}
}
