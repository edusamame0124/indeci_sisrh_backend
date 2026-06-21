package com.indeci.rrhh.dto.subsidio;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record SubsidioCittResponseDto(
        Long id,
        Long casoId,
        String nroCitt,
        LocalDate fechaEmision,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        String estado,
        String tipoDocumento,
        String accesoRestringido,
        LocalDateTime createdAt) {}
