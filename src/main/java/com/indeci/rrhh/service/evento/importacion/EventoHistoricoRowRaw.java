package com.indeci.rrhh.service.evento.importacion;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;

import com.indeci.rrhh.vinculacion.importacion.TextoNormalizador;

/**
 * V012_42 F2 — Una fila cruda de la hoja {@code sistema}, leída pero sin validar ni resolver
 * (DNI→empleado, MOTIVO→tipo de evento). Reutiliza {@link TextoNormalizador} (utilidad genérica
 * de saneo de texto, ya probada por el importador de Vinculación) en vez de duplicarla.
 */
public class EventoHistoricoRowRaw {

    private final int numeroFila;
    private final Map<EventoHistoricoColumna, Object> valores = new EnumMap<>(EventoHistoricoColumna.class);

    public EventoHistoricoRowRaw(int numeroFila) {
        this.numeroFila = numeroFila;
    }

    void put(EventoHistoricoColumna columna, Object valor) {
        if (valor != null) {
            valores.put(columna, valor);
        }
    }

    public int getNumeroFila() {
        return numeroFila;
    }

    public boolean estaVacia() {
        return valores.isEmpty();
    }

    public String texto(EventoHistoricoColumna columna) {
        final Object v = valores.get(columna);
        if (v == null) {
            return null;
        }
        if (v instanceof String s) {
            return TextoNormalizador.limpiar(s);
        }
        if (v instanceof Double d) {
            return d == Math.floor(d) && !d.isInfinite()
                    ? String.valueOf(d.longValue())
                    : String.valueOf(d);
        }
        return TextoNormalizador.limpiar(String.valueOf(v));
    }

    /** Clave normalizada (MAYÚSCULAS, sin tildes) — para comparar MOTIVO contra el diccionario. */
    public String clave(EventoHistoricoColumna columna) {
        return TextoNormalizador.clave(texto(columna));
    }

    /** Solo dígitos, preserva ceros a la izquierda — para DNI. */
    public String digitos(EventoHistoricoColumna columna) {
        return TextoNormalizador.soloDigitos(texto(columna));
    }

    /** Fecha real de Excel (columnas FECHA_INICIO/FECHA_FIN ya vienen tipadas, no como texto). */
    public LocalDate fecha(EventoHistoricoColumna columna) {
        final Object v = valores.get(columna);
        if (v instanceof LocalDateTime dt) {
            return dt.toLocalDate();
        }
        if (v instanceof LocalDate d) {
            return d;
        }
        return null;
    }

    /** Entero de la celda (TOTAL_DIAS), o {@code null} si no es numérica. */
    public Integer entero(EventoHistoricoColumna columna) {
        final Object v = valores.get(columna);
        if (v instanceof Number n) {
            return n.intValue();
        }
        return null;
    }
}
