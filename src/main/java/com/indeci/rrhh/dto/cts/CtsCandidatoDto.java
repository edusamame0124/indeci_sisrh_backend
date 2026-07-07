package com.indeci.rrhh.dto.cts;

import java.time.LocalDate;

/**
 * Feature 016 — fila de la grilla de cesantes aptos para liquidación.
 * {@code aptoParaCalcular=false} + {@code bloqueoMotivo} habilitan el Poka-Yoke
 * visual (badge naranja "Falta Fecha de Cese en Ficha").
 */
public record CtsCandidatoDto(
        Long empleadoId,
        Long empleadoPlanillaId,
        String dni,
        String nombre,
        String regimenCodigo,
        LocalDate fechaCese,
        String motivoCese,
        String estado,
        boolean aptoParaCalcular,
        String bloqueoMotivo) {
}
