package com.indeci.rrhh.dto;

import java.math.BigDecimal;

/**
 * F3.1 — Línea individual del desglose de la planilla para Ficha 360.
 *
 * <p>Cada {@code MovimientoPlanillaDetalle} se proyecta como una línea que la
 * UI muestra en el desglose ("¿de dónde sale este monto?"):</p>
 *
 * <pre>
 *   + Sueldo CAS                  S/ 5,500.00   30 días     [Ver fuente]
 *   + DS 313-2023-EF              S/    50.00   prorrateado [Ver concepto]
 *   − Tardanza 18 min             S/    42.00               [Ver asistencia]
 *   − Judicial 45 %               S/ 2,475.00               [Ver resolución]
 * </pre>
 */
public record ExplicacionLineaDto(
        /** "INGRESO" | "DESCUENTO" | "APORTE_TRABAJADOR" | "APORTE_EMPLEADOR" | "INFO". */
        String grupo,
        Long conceptoPlanillaId,
        String codigoMef,
        String codigoSisper,
        String descripcion,
        BigDecimal monto,
        /** Texto humano: "30 días", "10% RMV", "45% sobre 5500", "prorrateado", etc. */
        String detalle,
        /** Observación grabada en MovimientoPlanillaDetalle. */
        String observacion,
        /** "CONCEPTO_AUTO" | "EMPLEADO_CONCEPTO" | "ASISTENCIA" | "PARAMETRO" | null. */
        String fuenteTipo,
        /** Id de la fuente, si aplica (ConceptoPlanilla.id, etc.). */
        Long fuenteId) {

    /** Marca si el grupo es de tipo "ingreso" para que la UI ponga +. */
    public boolean isIngreso() {
        return "INGRESO".equalsIgnoreCase(grupo);
    }

    /** Marca si el grupo descuenta al neto del trabajador. */
    public boolean isDescuento() {
        return "DESCUENTO".equalsIgnoreCase(grupo)
                || "APORTE_TRABAJADOR".equalsIgnoreCase(grupo);
    }
}
