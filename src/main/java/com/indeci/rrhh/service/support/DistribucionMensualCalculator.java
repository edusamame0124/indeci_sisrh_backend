package com.indeci.rrhh.service.support;

import com.indeci.rrhh.dto.EventoDistribucionMesDto;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Calcula el desglose mensual de un descanso por días naturales contiguos.
 * CONSTRAINT: do NOT modify existing business logic fuera de este alcance.
 */
public final class DistribucionMensualCalculator {

    private DistribucionMensualCalculator() {
    }

    public static LocalDate calcularFechaFin(LocalDate fechaInicio, int duracionLegal) {
        return fechaInicio.plusDays(duracionLegal - 1L);
    }

    public static List<EventoDistribucionMesDto> calcular(
            LocalDate fechaInicio,
            LocalDate fechaFin) {
        List<EventoDistribucionMesDto> tramos = new ArrayList<>();
        LocalDate cursor = fechaInicio;
        while (!cursor.isAfter(fechaFin)) {
            YearMonth ym = YearMonth.from(cursor);
            LocalDate finMes = ym.atEndOfMonth();
            LocalDate tramoHasta = fechaFin.isBefore(finMes) ? fechaFin : finMes;
            int dias = (int) ChronoUnit.DAYS.between(cursor, tramoHasta) + 1;

            EventoDistribucionMesDto dto = new EventoDistribucionMesDto();
            dto.setPeriodo(String.format("%04d%02d", ym.getYear(), ym.getMonthValue()));
            dto.setFechaDesde(cursor);
            dto.setFechaHasta(tramoHasta);
            dto.setDiasSubsidio(dias);
            dto.setAfectaDiasLaborados("S");
            dto.setEstadoTramo("PENDIENTE_IMPUTACION");
            tramos.add(dto);

            cursor = tramoHasta.plusDays(1);
        }
        return tramos;
    }

    public static int sumarDias(List<EventoDistribucionMesDto> tramos) {
        return tramos.stream()
                .mapToInt(t -> t.getDiasSubsidio() != null ? t.getDiasSubsidio() : 0)
                .sum();
    }
}
