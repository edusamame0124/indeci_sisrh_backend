package com.indeci.rrhh.service.incidencia;

import java.time.LocalDate;

import com.indeci.rrhh.repository.AsistenciaDetalleRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * SPEC_VACACIONES F9.1 — inasistencias (faltas y sanciones PAD) no computables al récord
 * vacacional. Cuenta {@code TIPO_DIA IN ('FALTA','SANCION_PAD')} del empleado en el rango.
 * Las ausencias justificadas se registran como LICENCIA (no como FALTA), por lo que no
 * restan aquí.
 */
@Service
@RequiredArgsConstructor
public class InasistenciasIncidenciaProvider implements IncidenciaLaboralProvider {

    private final AsistenciaDetalleRepository asistenciaDetalleRepository;

    @Override
    public int obtenerDiasNoComputables(Long empleadoId, LocalDate desde, LocalDate hasta) {
        if (empleadoId == null || desde == null || hasta == null || desde.isAfter(hasta)) {
            return 0;
        }
        return (int) asistenciaDetalleRepository.contarFaltas(empleadoId, desde, hasta);
    }
}
