package com.indeci.rrhh.dto;

import java.util.List;

/**
 * Track B — Resultado de la generación del AGUINALDO: cuántos se generaron, los
 * excluidos con su motivo (auditoría, requisito #4) y advertencias no bloqueantes
 * (p. ej. orden de proceso vs planilla regular, requisito #A).
 */
public record AguinaldoResultDto(
        int generados,
        List<Excluido> excluidos,
        List<String> advertencias) {

    /** Trabajador excluido del aguinaldo con el motivo (auditoría). */
    public record Excluido(Long empleadoId, String motivo) {}
}
