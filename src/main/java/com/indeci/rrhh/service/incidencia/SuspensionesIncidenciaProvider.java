package com.indeci.rrhh.service.incidencia;

import com.indeci.rrhh.entity.CatSuspensionSunat;
import com.indeci.rrhh.entity.Suspension;
import com.indeci.rrhh.repository.CatSuspensionSunatRepository;
import com.indeci.rrhh.repository.SuspensionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SuspensionesIncidenciaProvider implements IncidenciaLaboralProvider {

    private final SuspensionRepository suspensionRepository;
    private final CatSuspensionSunatRepository catSuspensionSunatRepository;

    @Override
    public int obtenerDiasNoComputables(Long empleadoId, LocalDate desde, LocalDate hasta) {
        List<Suspension> suspensiones = suspensionRepository.findByEmpleadoIdAndEstadoOrderByFechaInicio(empleadoId, "ACTIVO");
        
        int diasNoComputables = 0;
        for (Suspension s : suspensiones) {
            // Intersect [s.fechaInicio, s.fechaFin] with [desde, hasta]
            LocalDate overlapStart = s.getFechaInicio().isAfter(desde) ? s.getFechaInicio() : desde;
            LocalDate overlapEnd = s.getFechaFin().isBefore(hasta) ? s.getFechaFin() : hasta;
            
            if (!overlapStart.isAfter(overlapEnd)) {
                CatSuspensionSunat cat = catSuspensionSunatRepository.findById(s.getCodSuspension()).orElse(null);
                if (cat != null && "NO_LABORADO_NO_SUB".equals(cat.getTipoPlame())) {
                    diasNoComputables += (int) ChronoUnit.DAYS.between(overlapStart, overlapEnd) + 1;
                }
            }
        }
        return diasNoComputables;
    }
}
