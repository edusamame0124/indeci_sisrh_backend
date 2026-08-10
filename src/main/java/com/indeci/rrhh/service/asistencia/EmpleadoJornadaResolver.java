package com.indeci.rrhh.service.asistencia;

import com.indeci.rrhh.entity.EmpleadoJornadaExcepcion;
import com.indeci.rrhh.entity.EmpleadoPlanilla;
import com.indeci.rrhh.entity.JornadaRegimen;
import com.indeci.rrhh.repository.EmpleadoJornadaExcepcionRepository;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.JornadaRegimenRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Fuente única de la jornada efectiva de un empleado (M04). Reemplaza los dos
 * métodos privados duplicados {@code jornadaDeEmpleado} que existían en
 * {@code AsistenciaService} y {@code AsistenciaImportService}.
 *
 * <p>Patrón Fallback (decisión RR.HH. 2026-08-08 — Horario Especial): si el
 * empleado tiene una {@link EmpleadoJornadaExcepcion} ACTIVA vigente en la
 * fecha consultada, sus horas de ingreso/salida/refrigerio mandan sobre las
 * del régimen; tolerancias, umbral/tope de tardanza y jornada en horas
 * SIEMPRE se heredan del régimen — la excepción no los redefine.
 */
@Component
@RequiredArgsConstructor
public class EmpleadoJornadaResolver {

    private final EmpleadoPlanillaRepository empleadoPlanillaRepository;
    private final JornadaRegimenRepository jornadaRegimenRepository;
    private final EmpleadoJornadaExcepcionRepository excepcionRepository;

    /** Jornada del RÉGIMEN del empleado, sin excepción. */
    public JornadaRegimen regimenDe(Long empleadoId) {
        if (empleadoId == null) {
            return null;
        }
        Long regimenId = empleadoPlanillaRepository.findFirstByEmpleadoIdAndActivo(empleadoId, 1)
                .map(EmpleadoPlanilla::getRegimenLaboralId)
                .orElse(null);
        return regimenId == null ? null : jornadaRegimenRepository.findByRegimenLaboralId(regimenId).orElse(null);
    }

    /** Excepciones ACTIVAS del empleado — se resuelve una sola vez y se reusa por día en un batch. */
    public List<EmpleadoJornadaExcepcion> excepcionesActivas(Long empleadoId) {
        if (empleadoId == null) {
            return List.of();
        }
        return excepcionRepository.findByEmpleadoIdAndActivoOrderByFechaInicioDesc(empleadoId, 1);
    }

    /**
     * Jornada efectiva de un día concreto: la excepción vigente ese día si existe,
     * si no el régimen tal cual. No persiste nada — el objeto devuelto es transitorio.
     */
    public JornadaRegimen resolverParaFecha(
            JornadaRegimen base, List<EmpleadoJornadaExcepcion> excepciones, LocalDate fecha) {
        if (base == null || fecha == null || excepciones == null) {
            return base;
        }
        return excepciones.stream()
                .filter(e -> e.cubre(fecha))
                .findFirst()
                .map(e -> aplicarExcepcion(base, e))
                .orElse(base);
    }

    /** Atajo sin caché — para resoluciones puntuales fuera de un loop por día. */
    public JornadaRegimen resolverParaFecha(Long empleadoId, LocalDate fecha) {
        JornadaRegimen base = regimenDe(empleadoId);
        return resolverParaFecha(base, excepcionesActivas(empleadoId), fecha);
    }

    private JornadaRegimen aplicarExcepcion(JornadaRegimen base, EmpleadoJornadaExcepcion exc) {
        JornadaRegimen efectiva = new JornadaRegimen();
        efectiva.setRegimenLaboralId(base.getRegimenLaboralId());
        efectiva.setHoraIngreso(exc.getHoraIngreso());
        efectiva.setHoraSalida(exc.getHoraSalida());
        efectiva.setRefrigerioInicio(exc.getRefrigerioInicio());
        efectiva.setRefrigerioFin(exc.getRefrigerioFin());
        // Heredado del régimen: la excepción es solo desplazamiento del bloque horario.
        efectiva.setToleranciaIngresoMin(base.getToleranciaIngresoMin());
        efectiva.setToleranciaAlmuerzoMin(base.getToleranciaAlmuerzoMin());
        efectiva.setUmbralTardanzaDiariaMin(base.getUmbralTardanzaDiariaMin());
        efectiva.setTopeTardanzaMensualMin(base.getTopeTardanzaMensualMin());
        efectiva.setUmbralSalidaAnticDiariaMin(base.getUmbralSalidaAnticDiariaMin());
        efectiva.setTopeSalidaAnticMensualMin(base.getTopeSalidaAnticMensualMin());
        efectiva.setJornadaHoras(base.getJornadaHoras());
        efectiva.setDiasSemana(base.getDiasSemana());
        return efectiva;
    }
}
