package com.indeci.rrhh.dto.subsidio;

import java.time.LocalDateTime;

public record SubsidioTimelineEventoDto(
        Long id,
        String tipoEvento,
        String descripcion,
        String usuario,
        LocalDateTime createdAt) {}
