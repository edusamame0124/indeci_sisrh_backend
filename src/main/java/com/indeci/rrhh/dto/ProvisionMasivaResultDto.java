package com.indeci.rrhh.dto;

import java.util.List;

/**
 * Resultado del botón "Provisionar para todos" (Padrón Vacacional).
 * Resume la provisión masiva de los empleados con baseline importado.
 *
 * @param total         empleados importados procesados
 * @param provisionados empleados con al menos un cambio aplicado
 * @param sinCambios    empleados cuyo saldo ya estaba correcto
 * @param errores       mensajes por empleado que falló (el lote no se aborta)
 */
public record ProvisionMasivaResultDto(
        int total,
        int provisionados,
        int sinCambios,
        List<String> errores) {
}
