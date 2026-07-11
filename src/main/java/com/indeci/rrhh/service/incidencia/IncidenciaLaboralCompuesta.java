package com.indeci.rrhh.service.incidencia;

import java.time.LocalDate;

import com.indeci.rrhh.dto.DiasNoComputablesDto;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * SPEC_VACACIONES F9.1 — proveedor compuesto de días no computables al récord vacacional.
 * Suma las dos fuentes vigentes y NO solapadas:
 * <ul>
 *   <li><b>Eventos del período</b> ({@link EventosIncidenciaProvider}) — LSG + suspensiones
 *       no subsidiadas (fuente única con el motor de planilla).</li>
 *   <li><b>Inasistencias</b> ({@link InasistenciasIncidenciaProvider}) — faltas de asistencia.</li>
 * </ul>
 *
 * <p>Marcado {@code @Primary} → es el que consume {@code VacacionCalculoService}. La antigua
 * {@link SuspensionesIncidenciaProvider} (tabla INDECI_SUSPENSION) queda como bean legacy y NO
 * se incluye aquí para evitar doble conteo de LSG (que ahora fluye por Eventos).
 */
@Service
@Primary
@RequiredArgsConstructor
public class IncidenciaLaboralCompuesta implements IncidenciaLaboralProvider {

    private final EventosIncidenciaProvider eventos;
    private final InasistenciasIncidenciaProvider inasistencias;

    @Override
    public int obtenerDiasNoComputables(Long empleadoId, LocalDate desde, LocalDate hasta) {
        return eventos.obtenerDiasNoComputables(empleadoId, desde, hasta)
                + inasistencias.obtenerDiasNoComputables(empleadoId, desde, hasta);
    }

    /**
     * Igual que {@link #obtenerDiasNoComputables}, pero devuelve el desglose LSG vs faltas
     * para la trazabilidad de RR.HH. (Padrón + Config Remunerativa + detalle vacacional).
     */
    public DiasNoComputablesDto calcularDesglose(Long empleadoId, LocalDate desde, LocalDate hasta) {
        int lsg = eventos.obtenerDiasNoComputables(empleadoId, desde, hasta);
        int faltas = inasistencias.obtenerDiasNoComputables(empleadoId, desde, hasta);
        return DiasNoComputablesDto.of(lsg, faltas);
    }
}
