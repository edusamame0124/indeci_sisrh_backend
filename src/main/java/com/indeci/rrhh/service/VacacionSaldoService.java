package com.indeci.rrhh.service;

import com.indeci.rrhh.dto.HistorialSaldoDto;
import com.indeci.rrhh.dto.VacacionSaldoResponseDto;
import com.indeci.rrhh.entity.VacacionSaldo;
import com.indeci.rrhh.repository.VacacionSaldoRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

/**
 * Lectura del saldo de vacaciones por empleado y año (SPEC §12.2 PANTALLA-08) — consumido
 * por el Portal del Empleado (self-service). El registro/corrección de saldo se hace desde
 * el Padrón Vacacional ("Provisionar Auto", {@link VacacionProvisionService}); este servicio
 * es solo lectura.
 */
@Service
@RequiredArgsConstructor
public class VacacionSaldoService {

    private final VacacionSaldoRepository repository;

    public List<VacacionSaldoResponseDto> listarPorEmpleado(Long empleadoId) {
        return repository.findByEmpleadoIdAndActivo(empleadoId, 1)
                .stream()
                .sorted(Comparator.comparing(VacacionSaldo::getAnio).reversed())
                .map(this::toResponse)
                .toList();
    }

    /**
     * Trazabilidad Visual (Padrón Vacacional) — ciclo de vida COMPLETO del saldo de un
     * empleado, incluyendo las filas anuladas por "Provisionar Auto" (activo=0). Consumido
     * por el modal "Historial de Recálculos", nunca por cálculos de saldo.
     */
    public List<HistorialSaldoDto> listarHistorialCompleto(Long empleadoId) {
        return repository.findByEmpleadoIdOrderByAnioDescCreatedAtDesc(empleadoId)
                .stream()
                .map(this::toHistorial)
                .toList();
    }

    private HistorialSaldoDto toHistorial(VacacionSaldo e) {
        double ganados = e.getDiasGanados() != null ? e.getDiasGanados() : 0d;
        double gozados = e.getDiasGozados() != null ? e.getDiasGozados() : 0d;
        double saldo = BigDecimal.valueOf(ganados - gozados)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();

        return new HistorialSaldoDto(
                e.getId(), e.getAnio(), e.getDiasGanados(), e.getDiasGozados(), saldo,
                e.getOrigen(), e.getActivo(), e.getObservacion(), e.getCreatedAt());
    }

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
