package com.indeci.rrhh.service.incidencia;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import com.indeci.rrhh.entity.EmpleadoEvento;
import com.indeci.rrhh.repository.EmpleadoEventoRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * SPEC_VACACIONES F9.1 / V012_42 F1 — días no computables al tiempo de servicio / récord
 * provenientes de <b>Eventos del período</b> validados con {@code afectaTiempoServicio='S'}:
 * licencias sin goce (materializadas desde la papeleta) y el histórico migrado
 * ({@code FALTA_HISTORICA}, {@code SUSPENSION_HISTORICA}).
 *
 * <p>Fuente única con el motor de planilla para LSG operativa (ambos leen
 * {@code INDECI_EMPLEADO_EVENTO}), pero desde V012_42 el filtro de este provider
 * ({@code afectaTiempoServicio}) es independiente del que usa el motor
 * ({@code afectaDiasLaborados}) — el histórico migrado no debe reabrir boletas ya
 * emitidas. Recorta cada evento al rango [desde, hasta] para no sobre-contar.</p>
 */
@Service
@RequiredArgsConstructor
public class EventosIncidenciaProvider implements IncidenciaLaboralProvider {

    private static final String COD_FALTA_HISTORICA = "FALTA_HISTORICA";
    private static final String COD_SUSPENSION_HISTORICA = "SUSPENSION_HISTORICA";

    private final EmpleadoEventoRepository empleadoEventoRepository;

    @Override
    public int obtenerDiasNoComputables(Long empleadoId, LocalDate desde, LocalDate hasta) {
        final Desglose d = obtenerDesglose(empleadoId, desde, hasta);
        return d.lsg() + d.faltasHistoricas() + d.suspensiones();
    }

    /**
     * V012_42 F1 — desglosa los días no computables de Eventos por tipo, para que
     * {@link IncidenciaLaboralCompuesta} pueda componer el {@code DiasNoComputablesDto}
     * de 3 categorías (lsg / faltas / suspensiones) sin consultar dos veces.
     */
    Desglose obtenerDesglose(Long empleadoId, LocalDate desde, LocalDate hasta) {
        if (empleadoId == null || desde == null || hasta == null || desde.isAfter(hasta)) {
            return new Desglose(0, 0, 0);
        }
        int lsg = 0;
        int faltasHistoricas = 0;
        int suspensiones = 0;
        for (EmpleadoEvento e : empleadoEventoRepository.findNoComputablesRecord(empleadoId, desde, hasta)) {
            final LocalDate ini = e.getFechaInicio().isAfter(desde) ? e.getFechaInicio() : desde;
            final LocalDate fin = e.getFechaFin().isBefore(hasta) ? e.getFechaFin() : hasta;
            if (ini.isAfter(fin)) {
                continue;
            }
            final int dias = (int) ChronoUnit.DAYS.between(ini, fin) + 1;
            final String codigo = e.getTipoEvento() != null ? e.getTipoEvento().getCodigo() : null;
            if (COD_FALTA_HISTORICA.equals(codigo)) {
                faltasHistoricas += dias;
            } else if (COD_SUSPENSION_HISTORICA.equals(codigo)) {
                suspensiones += dias;
            } else {
                // LICENCIA_SIN_GOCE (operativa o histórica migrada) + otros tipos legacy con
                // afectaTiempoServicio='S' (CESE, PERMISO_PERSONAL) — mismo bucket que antes
                // de V012_42, para no cambiar la etiqueta visible a RR.HH. sin necesidad.
                lsg += dias;
            }
        }
        return new Desglose(lsg, faltasHistoricas, suspensiones);
    }

    record Desglose(int lsg, int faltasHistoricas, int suspensiones) {
    }
}
