package com.indeci.rrhh.dto.subsidio;

import java.time.LocalDate;

public record SubsidioTramoResponseDto(
        Long id,
        Long casoId,
        String periodo,
        LocalDate fechaDesde,
        LocalDate fechaHasta,
        Integer diasSubsidio,
        Integer diasLaborados,
        String estadoTramo,
        Integer versionTramo) {}
