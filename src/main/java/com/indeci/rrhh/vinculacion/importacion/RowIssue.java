package com.indeci.rrhh.vinculacion.importacion;

/**
 * Un hallazgo sobre una celda/fila del Excel, anclado a la columna para que RR.HH.
 * sepa exactamente qué corregir.
 *
 * <p>Clasificación (ver INFORME_VERIFICACION_EXCEL_OFICIAL.md):
 * <ul>
 *   <li>{@link Severidad#SANEADO} — Clase A: ruido de formato corregido automáticamente
 *       (apóstrofe en AIRHSP, {@code S/.} en monto, separadores en CCI). Informativo.</li>
 *   <li>{@link Severidad#ADVERTENCIA} — Clase B: el dato se importa pero conviene revisarlo
 *       (catálogo dado de alta automáticamente, vínculo que se actualizará).</li>
 *   <li>{@link Severidad#ERROR} — Clase C: no se puede adivinar la corrección. La fila
 *       <b>no se importa</b> hasta que RR.HH. la corrija.</li>
 * </ul>
 *
 * @param columna   columna afectada ({@code null} si el hallazgo es de la fila completa)
 * @param severidad gravedad del hallazgo
 * @param mensaje   texto para el usuario, en español y accionable
 */
public record RowIssue(VinculacionColumna columna, Severidad severidad, String mensaje) {

    public enum Severidad {
        SANEADO,
        ADVERTENCIA,
        ERROR
    }

    public static RowIssue saneado(VinculacionColumna columna, String mensaje) {
        return new RowIssue(columna, Severidad.SANEADO, mensaje);
    }

    public static RowIssue advertencia(VinculacionColumna columna, String mensaje) {
        return new RowIssue(columna, Severidad.ADVERTENCIA, mensaje);
    }

    public static RowIssue error(VinculacionColumna columna, String mensaje) {
        return new RowIssue(columna, Severidad.ERROR, mensaje);
    }

    /** Celda en notación Excel (p. ej. {@code "BF375"}) para ubicar el dato de un vistazo. */
    public String celda(int numeroFila) {
        return columna == null ? "Fila " + numeroFila : columna.getLetra() + numeroFila;
    }
}
