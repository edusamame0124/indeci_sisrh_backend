package com.indeci.rrhh.service.asistencia;

import com.indeci.rrhh.entity.EmpleadoPlanilla;
import com.indeci.rrhh.entity.RegimenLaboral;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.RegimenLaboralRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Orquesta la resolucion de base remunerativa para descuentos de asistencia.
 * La regla por regimen vive en strategies para mantener el servicio extensible.
 */
@Component
@RequiredArgsConstructor
public class BaseAsistenciaResolver {

    private final EmpleadoPlanillaRepository empleadoPlanillaRepository;
    private final RegimenLaboralRepository regimenLaboralRepository;
    private final List<BaseAsistenciaStrategy> strategies;

    public BaseAsistenciaResult resolver(Long empleadoId) {
        Optional<EmpleadoPlanilla> planillaOpt =
                empleadoPlanillaRepository.findFirstByEmpleadoIdAndActivo(empleadoId, 1);

        if (planillaOpt.isEmpty()) {
            BaseAsistenciaResult vacio = BaseAsistenciaResult.vacio();
            vacio.getAdvertencias().add(
                    "No hay planilla vigente para el empleado; descuentos en 0.");
            return vacio;
        }

        EmpleadoPlanilla planilla = planillaOpt.get();
        String codigoRegimen = resolverCodigoRegimen(planilla.getRegimenLaboralId());
        return strategies.stream()
                .filter(strategy -> strategy.soporta(codigoRegimen))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No existe strategy fallback de asistencia."))
                .resolver(planilla, codigoRegimen);
    }

    private String resolverCodigoRegimen(Long regimenLaboralId) {
        if (regimenLaboralId == null) {
            return null;
        }
        return regimenLaboralRepository.findById(regimenLaboralId)
                .map(RegimenLaboral::getCodigo)
                .orElse(null);
    }
}
