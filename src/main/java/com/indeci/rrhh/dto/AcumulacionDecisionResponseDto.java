package com.indeci.rrhh.dto;

import java.time.LocalDateTime;

/** F9.3 — decisión de RR.HH. registrada (auditoría, append-only). */
public record AcumulacionDecisionResponseDto(
        Long id,
        Long empleadoId,
        Integer periodosPendientesAlMomento,
        String motivoDecision,
        String documentoSustento,
        String usuarioRegistro,
        LocalDateTime createdAt) {
}
