package com.indeci.rrhh.dto;

/**
 * F3.3 — Hallazgo individual del Centro de Validaciones (preflight).
 *
 * <p>Representa un problema detectable ANTES de correr el motor de planilla.
 * La UI lo agrupa por {@code severidad} en 3 KPI cards (BLOQUEO / ALERTA / INFO)
 * y permite filtrar por {@code modulo}. Cuando el hallazgo tiene
 * {@code empleadoId}, la UI lo enlaza a la Ficha 360 del empleado.</p>
 *
 * <p>Campos:</p>
 * <ul>
 *   <li>{@code codigo} — V1..V10 (estable, sirve a la UI para tooltip/links).</li>
 *   <li>{@code severidad} — BLOQUEO | ALERTA | INFO.</li>
 *   <li>{@code modulo} — Periodo | Asistencia | Empleado | Concepto | Evento.</li>
 *   <li>{@code mensaje} — texto en español, formal, listo para mostrar.</li>
 *   <li>{@code empleadoId} / {@code empleadoNombre} — opcionales.</li>
 *   <li>{@code referenciaId} — opcional, id de la entidad subyacente
 *       (ConceptoPlanilla, EmpleadoConcepto, EmpleadoEvento) para que el frontend
 *       deep-link al módulo correspondiente si lo soporta.</li>
 * </ul>
 */
public record ValidacionHallazgoDto(
        String codigo,
        String severidad,
        String modulo,
        String mensaje,
        Long empleadoId,
        String empleadoNombre,
        Long referenciaId) {

    public static ValidacionHallazgoDto bloqueo(
            String codigo, String modulo, String mensaje,
            Long empleadoId, String empleadoNombre, Long referenciaId) {
        return new ValidacionHallazgoDto(
                codigo, "BLOQUEO", modulo, mensaje,
                empleadoId, empleadoNombre, referenciaId);
    }

    public static ValidacionHallazgoDto alerta(
            String codigo, String modulo, String mensaje,
            Long empleadoId, String empleadoNombre, Long referenciaId) {
        return new ValidacionHallazgoDto(
                codigo, "ALERTA", modulo, mensaje,
                empleadoId, empleadoNombre, referenciaId);
    }

    public static ValidacionHallazgoDto info(
            String codigo, String modulo, String mensaje,
            Long empleadoId, String empleadoNombre, Long referenciaId) {
        return new ValidacionHallazgoDto(
                codigo, "INFO", modulo, mensaje,
                empleadoId, empleadoNombre, referenciaId);
    }
}
