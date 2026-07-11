package com.indeci.rrhh.service.incidencia;

import java.time.LocalDate;

/**
 * Proveedor de incidencias laborales (e.g. Licencias Sin Goce, Suspensiones imperfectas)
 * para descontar del récord vacacional u otros cálculos.
 */
public interface IncidenciaLaboralProvider {

    /**
     * Obtiene el total de días no computables para el récord vacacional en un rango de fechas.
     */
    int obtenerDiasNoComputables(Long empleadoId, LocalDate desde, LocalDate hasta);
}
