package com.indeci.rrhh.dto;

import java.time.LocalDate;

/**
 * Resultado del cómputo de tiempo de servicio de un empleado — SPEC_VACACIONES F1.
 *
 * <p>Desglose 30/360 (US/NASD) con conteo inclusivo de extremos (reproduce las
 * columnas L/M/N de la MATRIZ_VACACIONES del especialista). Read-only; no persiste.
 *
 * @param empleadoId    empleado consultado
 * @param fechaIngreso  ancla de inicio usada (primer inicio de vínculo)
 * @param fechaCorte    fecha hasta la que se computa (HOY si no se especifica)
 * @param anios         años completos de servicio (30/360)
 * @param meses         meses (0-11)
 * @param dias          días (0-29)
 * @param totalDias360  total en días comerciales (base 30/360, inclusivo)
 * @param numVinculos   número de vínculos activos considerados (trazabilidad)
 * @param tieneTraslape true si se fusionaron intervalos solapados (auditoría rotación CAS)
 */
public record TiempoServicioDto(
        Long empleadoId,
        LocalDate fechaIngreso,
        LocalDate fechaCorte,
        int anios,
        int meses,
        int dias,
        int totalDias360,
        int numVinculos,
        boolean tieneTraslape) {
}
