package com.indeci.rrhh.dto;

import java.util.List;

/**
 * Resultado del importador de línea base vacacional — SPEC_VACACIONES F8.
 *
 * @param totalFilas     filas de datos leídas del Excel
 * @param importados     saldos creados/actualizados con éxito
 * @param noEncontrados  DNIs del Excel sin empleado en el sistema (no se importan)
 * @param errores        filas con datos inválidos (mensaje por fila)
 * @param fechaCorte     fecha de corte detectada en el Excel (col K), ISO
 * @param origen         etiqueta de origen aplicada (MIGRACION_INICIAL_2026)
 */
public record ImportacionVacacionesResultDto(
        int totalFilas,
        int importados,
        List<String> noEncontrados,
        List<String> errores,
        String fechaCorte,
        String origen) {
}
