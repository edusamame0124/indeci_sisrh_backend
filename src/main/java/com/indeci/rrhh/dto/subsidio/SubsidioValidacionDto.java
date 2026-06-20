package com.indeci.rrhh.dto.subsidio;

public record SubsidioValidacionDto(
        String codigo,
        String severidad,
        String mensaje,
        Long casoId,
        Long tramoId,
        Long liquidacionId) {}
