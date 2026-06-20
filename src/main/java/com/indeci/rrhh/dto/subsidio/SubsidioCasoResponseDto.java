package com.indeci.rrhh.dto.subsidio;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record SubsidioCasoResponseDto(
        Long id,
        Long empleadoId,
        String codigoCaso,
        String tipoCaso,
        String estado,
        LocalDate fechaContingencia,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        Integer diasContingencia,
        Integer versionCaso,
        Long reglaVigenciaId,
        String modoCalculo,
        String observacion,
        String nombreEmpleado,
        String dniEmpleado,
        LocalDateTime createdAt,
        List<SubsidioCittResponseDto> citts,
        List<SubsidioTramoResponseDto> tramos) {}
