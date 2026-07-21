package com.indeci.rrhh.vinculacion.importacion;

import java.util.List;

/**
 * Contratos de salida del import de vinculación (lo que consume el frontend).
 *
 * <p>Se agrupan aquí por cohesión: son records pequeños que solo existen para esta API.
 */
public final class VinculacionImportDtos {

    private VinculacionImportDtos() {}

    /**
     * Un hallazgo listo para pintar en la tabla de previsualización.
     *
     * @param celda     referencia Excel (p. ej. {@code "BF375"}) para ubicar el dato
     * @param columna   cabecera legible de la columna
     * @param severidad SANEADO | ADVERTENCIA | ERROR
     * @param mensaje   explicación accionable
     */
    public record IssueDto(String celda, String columna, String severidad, String mensaje) {

        static IssueDto de(RowIssue issue, int numeroFila) {
            return new IssueDto(
                    issue.celda(numeroFila),
                    issue.columna() != null ? issue.columna().getCabecera() : "—",
                    issue.severidad().name(),
                    issue.mensaje());
        }
    }

    /**
     * Estado de una fila en la previsualización.
     *
     * @param numeroFila fila tal como se ve en Excel
     * @param dni        para que RR.HH. identifique a quién corresponde
     * @param nombre     nombre del servidor
     * @param estado     OK | ADVERTENCIA | ERROR
     * @param issues     hallazgos de la fila
     */
    public record FilaPreviewDto(
            int numeroFila, String dni, String nombre, String estado, List<IssueDto> issues) {}

    /**
     * Resultado de la previsualización. No se escribió nada en la base.
     *
     * @param total        filas leídas
     * @param importables  filas sin errores
     * @param conError     filas que RR.HH. debe corregir
     * @param conAdvertencia filas que se importan pero conviene revisar
     * @param filas        detalle fila por fila
     */
    public record PreviewDto(
            int total, int importables, int conError, int conAdvertencia,
            List<FilaPreviewDto> filas) {}

    /**
     * Resultado de la importación efectiva.
     *
     * @param total        filas leídas
     * @param creados      vínculos nuevos
     * @param actualizados vínculos que ya existían (llave DNI + N.° de contrato)
     * @param anulados     vínculos activos que el Excel ya no declara y se anularon (huérfanos)
     * @param omitidos     filas con error que no se importaron
     * @param errores      detalle de lo omitido
     */
    public record CommitDto(
            int total, int creados, int actualizados, int anulados, int omitidos,
            List<FilaPreviewDto> errores) {}
}
