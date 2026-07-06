package com.indeci.rrhh.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.ElegibilidadVinculoDto;
import com.indeci.rrhh.entity.EmpleadoPlanilla;
import com.indeci.rrhh.repository.EmpleadoBancoRepository;
import com.indeci.rrhh.repository.EmpleadoPensionRepository;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.vinculacion.VinculoEstadoResolver;
import com.indeci.rrhh.vinculacion.VinculoEstadoResolver.VinculoEstado;

import lombok.RequiredArgsConstructor;

/**
 * Elegibilidad calculada del vínculo para planilla (F4a). Agrega los hechos del
 * vínculo + banco + pensión + AIRHSP. AIRHSP no bloquea la planilla interna pero
 * sí es requisito para MCPP (regla de la spec).
 */
@Service
@RequiredArgsConstructor
public class ElegibilidadVinculoService {

    private final EmpleadoPlanillaRepository planillaRepository;
    private final EmpleadoBancoRepository bancoRepository;
    private final EmpleadoPensionRepository pensionRepository;

    @Transactional(readOnly = true)
    public ElegibilidadVinculoDto evaluar(Long empleadoPlanillaId) {
        EmpleadoPlanilla pl = planillaRepository.findById(empleadoPlanillaId)
                .orElseThrow(() -> new NegocioException("Configuración de planilla no encontrada"));

        Long empId = pl.getEmpleadoId();
        List<String> cumple = new ArrayList<>();
        List<String> pendientes = new ArrayList<>();

        LocalDate inicio = pl.getFechaInicioContrato() != null
                ? pl.getFechaInicioContrato() : pl.getFechaInicio();
        VinculoEstado estado = VinculoEstadoResolver.derivar(
                pl.getActivo(), inicio, pl.getFechaFin(), pl.getFechaCese(), LocalDate.now());
        boolean vinculoVigente = estado == VinculoEstado.VIGENTE || estado == VinculoEstado.PROGRAMADO;
        if (vinculoVigente) {
            cumple.add("Vínculo " + estado.name().toLowerCase().replace('_', ' '));
        } else {
            pendientes.add("Vínculo no vigente (" + estado.name().replace('_', ' ') + ")");
        }

        boolean regimen = pl.getRegimenLaboralId() != null;
        (regimen ? cumple : pendientes).add(regimen ? "Régimen laboral configurado" : "Falta régimen laboral");

        boolean remuneracion = pl.getSueldoBasico() != null && pl.getSueldoBasico() > 0;
        (remuneracion ? cumple : pendientes).add(
                remuneracion ? "Remuneración vigente" : "Falta remuneración vigente");

        boolean banco = empId != null && bancoRepository.existsByEmpleadoIdAndActivo(empId, 1);
        (banco ? cumple : pendientes).add(banco ? "Cuenta bancaria registrada" : "Falta cuenta bancaria");

        boolean pension = empId != null && pensionRepository.existsByEmpleadoIdAndActivo(empId, 1);
        (pension ? cumple : pendientes).add(
                pension ? "Régimen pensionario configurado" : "Falta régimen pensionario");

        boolean airhsp = (pl.getRegistroPlazaAirhsp() != null && !pl.getRegistroPlazaAirhsp().isBlank())
                || (pl.getCodigoAirhsp() != null && !pl.getCodigoAirhsp().isBlank()
                    && !"000000".equals(pl.getCodigoAirhsp()));
        if (airhsp) {
            cumple.add("Registro AIRHSP presente");
        } else {
            pendientes.add("Falta registro AIRHSP válido (requerido para MCPP)");
        }

        boolean elegiblePlanilla = vinculoVigente && regimen && remuneracion && banco && pension;
        boolean elegibleMcpp = elegiblePlanilla && airhsp;

        return new ElegibilidadVinculoDto(elegiblePlanilla, elegibleMcpp, cumple, pendientes);
    }
}
