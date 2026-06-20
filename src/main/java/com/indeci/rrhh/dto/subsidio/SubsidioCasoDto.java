package com.indeci.rrhh.dto.subsidio;

import java.time.LocalDate;

public record SubsidioCasoDto(
        Long empleadoId,
        String tipoCaso,
        LocalDate fechaContingencia,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        String observacion,
        String modoCalculo) {}
