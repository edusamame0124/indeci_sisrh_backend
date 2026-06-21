package com.indeci.rrhh.dto.subsidio;

import java.time.LocalDate;

public record SubsidioCittDto(
        String nroCitt,
        LocalDate fechaEmision,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        String tipoDocumento,
        String hashDocumento,
        Long legajoDocId,
        String accesoRestringido) {}
