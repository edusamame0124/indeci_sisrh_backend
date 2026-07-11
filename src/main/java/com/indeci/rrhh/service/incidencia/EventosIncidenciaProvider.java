package com.indeci.rrhh.service.incidencia;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import com.indeci.rrhh.entity.EmpleadoEvento;
import com.indeci.rrhh.repository.EmpleadoEventoRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * SPEC_VACACIONES F9.1 — días no computables al récord provenientes de <b>Eventos del
 * período</b> validados que restan días laborados ({@code afectaDiasLaborados='S'}):
 * licencias sin goce (materializadas desde la papeleta) y suspensiones no subsidiadas.
 *
 * <p>Fuente única con el motor de planilla (ambos leen INDECI_EMPLEADO_EVENTO). Recorta
 * cada evento al rango [desde, hasta] para no sobre-contar.
 */
@Service
@RequiredArgsConstructor
public class EventosIncidenciaProvider implements IncidenciaLaboralProvider {

    private final EmpleadoEventoRepository empleadoEventoRepository;

    @Override
    public int obtenerDiasNoComputables(Long empleadoId, LocalDate desde, LocalDate hasta) {
        if (empleadoId == null || desde == null || hasta == null || desde.isAfter(hasta)) {
            return 0;
        }
        int total = 0;
        for (EmpleadoEvento e : empleadoEventoRepository.findNoComputablesRecord(empleadoId, desde, hasta)) {
            final LocalDate ini = e.getFechaInicio().isAfter(desde) ? e.getFechaInicio() : desde;
            final LocalDate fin = e.getFechaFin().isBefore(hasta) ? e.getFechaFin() : hasta;
            if (!ini.isAfter(fin)) {
                total += (int) ChronoUnit.DAYS.between(ini, fin) + 1;
            }
        }
        return total;
    }
}
