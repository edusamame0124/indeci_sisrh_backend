package com.indeci.rrhh.service;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.PersonaEmpleadoResponseDto;
import com.indeci.rrhh.dto.TransparenciaPeriodoDto;
import com.indeci.rrhh.dto.TransparenciaRemuneracionDto;
import com.indeci.rrhh.entity.EmpleadoPlanilla;
import com.indeci.rrhh.entity.MovimientoPlanilla;
import com.indeci.rrhh.entity.PeriodoPlanilla;
import com.indeci.rrhh.entity.RegimenLaboral;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.MovimientoPlanillaRepository;
import com.indeci.rrhh.repository.PeriodoPlanillaRepository;
import com.indeci.rrhh.repository.RegimenLaboralRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Spec 011 / B4 — M10 Transparencia (Ley 27806).
 *
 * Expone, sin autenticación, las remuneraciones del personal de períodos ya
 * finalizados. Solo se publican los períodos en estado APROBADO o CERRADO:
 * una planilla en borrador o en revisión NO es información pública todavía.
 */
@Service
@RequiredArgsConstructor
public class TransparenciaService {

    /** Estados de período cuya planilla ya es información pública. */
    private static final Set<String> PUBLICOS = Set.of("APROBADO", "CERRADO");

    private final PeriodoPlanillaRepository periodoRepository;
    private final MovimientoPlanillaRepository movimientoRepository;
    private final EmpleadoPlanillaRepository planillaRepository;
    private final RegimenLaboralRepository regimenLaboralRepository;
    private final PersonaService personaService;

    // ============================ PERÍODOS PUBLICADOS ============================

    /** Períodos visibles en transparencia (APROBADO / CERRADO), más reciente primero. */
    public List<TransparenciaPeriodoDto> periodosPublicados() {
        return periodoRepository.findByActivo(1)
                .stream()
                .filter(p -> PUBLICOS.contains(p.getEstado()))
                .sorted(Comparator.comparing(PeriodoPlanilla::getPeriodo).reversed())
                .map(p -> {
                    TransparenciaPeriodoDto dto = new TransparenciaPeriodoDto();
                    dto.setPeriodo(p.getPeriodo());
                    dto.setEstado(p.getEstado());
                    return dto;
                })
                .toList();
    }

    // ============================ REMUNERACIONES ============================

    /**
     * Remuneraciones públicas de un período. Falla si el período no existe o
     * todavía no está publicado (no es APROBADO ni CERRADO).
     */
    public List<TransparenciaRemuneracionDto> remuneraciones(String periodo) {

        PeriodoPlanilla per = periodoRepository
                .findByPeriodoAndActivo(periodo, 1)
                .orElseThrow(() -> new NegocioException(
                        "Período no encontrado: " + periodo));

        if (!PUBLICOS.contains(per.getEstado())) {
            throw new NegocioException(
                    "El período " + periodo + " aún no está publicado en "
                            + "transparencia (estado " + per.getEstado() + ")");
        }

        Map<Long, String> nombres = new HashMap<>();
        for (PersonaEmpleadoResponseDto p : personaService.listar()) {
            if (p.getEmpleadoId() != null) {
                nombres.put(p.getEmpleadoId(), p.getNombreCompleto());
            }
        }

        List<TransparenciaRemuneracionDto> filas = new ArrayList<>();
        for (MovimientoPlanilla mov :
                movimientoRepository.findByPeriodoAndActivo(periodo, 1)) {

            TransparenciaRemuneracionDto dto = new TransparenciaRemuneracionDto();
            dto.setEmpleado(nombres.getOrDefault(
                    mov.getEmpleadoId(), "Empleado " + mov.getEmpleadoId()));
            dto.setRegimen(regimenDe(mov.getEmpleadoId()));
            dto.setRemuneracionBruta(mov.getTotalIngresos());
            filas.add(dto);
        }

        filas.sort(Comparator.comparing(TransparenciaRemuneracionDto::getEmpleado));
        return filas;
    }

    // ============================ HELPER ============================

    private String regimenDe(Long empleadoId) {
        return planillaRepository.findFirstByEmpleadoIdAndActivo(empleadoId, 1)
                .map(EmpleadoPlanilla::getRegimenLaboralId)
                .flatMap(rid -> rid == null
                        ? Optional.empty()
                        : regimenLaboralRepository.findById(rid))
                .map(RegimenLaboral::getCodigo)
                .orElse("—");
    }
}
