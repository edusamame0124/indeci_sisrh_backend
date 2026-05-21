package com.indeci.rrhh.service;

import com.indeci.audit.annotation.Auditable;
import com.indeci.audit.context.AuditoriaContext;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.VacacionSaldoDto;
import com.indeci.rrhh.dto.VacacionSaldoResponseDto;
import com.indeci.rrhh.entity.VacacionSaldo;
import com.indeci.rrhh.repository.VacacionSaldoRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * Saldo de vacaciones por empleado y año (SPEC §12.2 PANTALLA-08).
 * UPSERT por (empleadoId, año); el saldo de días se deriva
 * (DIAS_GANADOS − DIAS_GOZADOS).
 */
@Service
@RequiredArgsConstructor
public class VacacionSaldoService {

    private final VacacionSaldoRepository repository;
    private final AuditoriaContext auditoriaContext;

    // ============================ GUARDAR (UPSERT) ============================

    @Auditable(accion = "GUARDAR_VACACION_SALDO")
    public void guardar(VacacionSaldoDto dto) {
        if (dto.getEmpleadoId() == null || dto.getAnio() == null) {
            throw new NegocioException("El saldo de vacaciones requiere empleadoId y año");
        }
        double ganados = dto.getDiasGanados() != null ? dto.getDiasGanados() : 0d;
        double gozados = dto.getDiasGozados() != null ? dto.getDiasGozados() : 0d;
        if (ganados < 0 || gozados < 0) {
            throw new NegocioException("Los días de vacaciones no pueden ser negativos");
        }

        VacacionSaldo entity = repository
                .findByEmpleadoIdAndAnioAndActivo(dto.getEmpleadoId(), dto.getAnio(), 1)
                .orElseGet(() -> {
                    VacacionSaldo nuevo = new VacacionSaldo();
                    nuevo.setEmpleadoId(dto.getEmpleadoId());
                    nuevo.setAnio(dto.getAnio());
                    nuevo.setActivo(1);
                    nuevo.setCreatedAt(LocalDateTime.now());
                    return nuevo;
                });

        entity.setDiasGanados(ganados);
        entity.setDiasGozados(gozados);
        entity.setObservacion(dto.getObservacion());

        repository.save(entity);
        auditoriaContext.setDetalle(
                "Saldo de vacaciones guardado — empleado " + dto.getEmpleadoId()
                        + ", año " + dto.getAnio());
    }

    // ============================ LISTAR ============================

    public List<VacacionSaldoResponseDto> listarPorEmpleado(Long empleadoId) {
        return repository.findByEmpleadoIdAndActivo(empleadoId, 1)
                .stream()
                .sorted(Comparator.comparing(VacacionSaldo::getAnio).reversed())
                .map(this::toResponse)
                .toList();
    }

    // ============================ HELPER ============================

    private VacacionSaldoResponseDto toResponse(VacacionSaldo e) {
        VacacionSaldoResponseDto dto = new VacacionSaldoResponseDto();
        dto.setId(e.getId());
        dto.setEmpleadoId(e.getEmpleadoId());
        dto.setAnio(e.getAnio());
        dto.setDiasGanados(e.getDiasGanados());
        dto.setDiasGozados(e.getDiasGozados());
        dto.setObservacion(e.getObservacion());

        double ganados = e.getDiasGanados() != null ? e.getDiasGanados() : 0d;
        double gozados = e.getDiasGozados() != null ? e.getDiasGozados() : 0d;
        dto.setDiasSaldo(
                BigDecimal.valueOf(ganados - gozados)
                        .setScale(2, RoundingMode.HALF_UP)
                        .doubleValue());
        return dto;
    }
}
