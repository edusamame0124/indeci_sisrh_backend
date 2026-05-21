package com.indeci.rrhh.service.support;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * B3 / M09 — Consolidación de conceptos por DNI para el archivo PLAME .rem.
 *
 * <p>SUNAT rechaza el .rem si un mismo {@code Tipo_Doc + Nro_Doc} aparece duplicado.
 * Cuando un empleado tiene planilla regular y de vacaciones truncas en el mismo
 * período (movimientos distintos), sus montos por el mismo código PLAME deben
 * sumarse y exportarse en una sola línea.
 *
 * <p>Estrategia de bucket por defecto (Anexo 2 SUNAT): cada concepto ya trae su
 * {@code CODIGO_PLAME_SUNAT}; la consolidación agrupa por {@code (DNI, código)} y
 * suma. La regla de redistribución de la remuneración base CAS en buckets
 * {@code 0601/1028/1030/2039} observada en archivos legacy NO se aplica aquí —
 * queda pendiente de validación con RRHH/SUNAT (ver diseño B3-5).
 *
 * <p>Precisión estricta: la suma se hace en {@link BigDecimal} SIN redondear; el
 * redondeo a 2 decimales (HALF_UP) ocurre solo al serializar, en
 * {@link PlameRemWriter}. Orden determinístico por (DNI asc, código asc) para
 * salida byte-estable.
 */
public final class PlameConsolidator {

    private PlameConsolidator() {
    }

    /** Concepto ya mapeado a su código PLAME, antes de consolidar. */
    public record RawConcepto(String dni, String codigoPlame, BigDecimal monto) {
    }

    /**
     * Agrupa por (DNI, código PLAME) y suma los montos en BigDecimal.
     * Devuelve filas listas para {@link PlameRemWriter}, con devengado = pagado = suma.
     *
     * @param rows conceptos con código PLAME no nulo (el caller valida el mapeo).
     */
    public static List<PlameRemWriter.Row> consolidar(List<RawConcepto> rows) {
        // TreeMap anidado: orden natural por DNI y luego por código → salida estable.
        Map<String, Map<String, BigDecimal>> porDni = new TreeMap<>();

        for (RawConcepto r : rows) {
            porDni
                .computeIfAbsent(r.dni(), k -> new TreeMap<>())
                .merge(r.codigoPlame(), r.monto(), BigDecimal::add);
        }

        List<PlameRemWriter.Row> salida = new ArrayList<>();
        for (Map.Entry<String, Map<String, BigDecimal>> dniEntry : porDni.entrySet()) {
            for (Map.Entry<String, BigDecimal> codeEntry : dniEntry.getValue().entrySet()) {
                BigDecimal suma = codeEntry.getValue();
                salida.add(new PlameRemWriter.Row(
                        dniEntry.getKey(), codeEntry.getKey(), suma, suma));
            }
        }
        return salida;
    }
}
