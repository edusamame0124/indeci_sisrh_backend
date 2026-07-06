package com.indeci.rrhh.vinculacion;

import java.time.LocalDate;

/**
 * Deriva el estado canónico del vínculo laboral a partir de HECHOS registrados por
 * RR.HH. (fechas + cese + anulación). NO existe un selector libre de estado: el
 * estado se calcula. Decisión de RR.HH. (2026-07-02):
 *
 * <ul>
 *   <li>{@code ANULADO} — el vínculo fue anulado (activo = 0).</li>
 *   <li>{@code CESADO} — hay fecha efectiva de cese registrada.</li>
 *   <li>{@code PROGRAMADO} — el inicio del contrato es futuro.</li>
 *   <li>{@code VENCIDO_PENDIENTE_DE_REGULARIZACION} — venció la fecha fin y no hay
 *       cese formal (no se genera LBS automáticamente).</li>
 *   <li>{@code VIGENTE} — en curso.</li>
 * </ul>
 *
 * <p>La LBS solo se habilita con estado {@code CESADO} + fecha de cese + motivo +
 * documento de sustento.</p>
 */
public final class VinculoEstadoResolver {

    private VinculoEstadoResolver() {}

    public enum VinculoEstado {
        PROGRAMADO,
        VIGENTE,
        VENCIDO_PENDIENTE_DE_REGULARIZACION,
        CESADO,
        ANULADO
    }

    public static VinculoEstado derivar(
            Integer activo, LocalDate fechaInicio, LocalDate fechaFin,
            LocalDate fechaCese, LocalDate hoy) {
        if (activo != null && activo == 0) {
            return VinculoEstado.ANULADO;
        }
        if (fechaCese != null) {
            return VinculoEstado.CESADO;
        }
        if (fechaInicio != null && fechaInicio.isAfter(hoy)) {
            return VinculoEstado.PROGRAMADO;
        }
        if (fechaFin != null && fechaFin.isBefore(hoy)) {
            return VinculoEstado.VENCIDO_PENDIENTE_DE_REGULARIZACION;
        }
        return VinculoEstado.VIGENTE;
    }

    /**
     * La LBS solo se habilita con cese formal completo: estado CESADO + fecha de
     * cese + motivo + documento de sustento. Un vínculo con fin vencido pero sin
     * cese formal (VENCIDO_PENDIENTE_DE_REGULARIZACION) NO habilita LBS.
     */
    public static boolean habilitaLbs(
            VinculoEstado estado, LocalDate fechaCese, String motivoCese, String documentoCese) {
        return estado == VinculoEstado.CESADO
                && fechaCese != null
                && motivoCese != null && !motivoCese.isBlank()
                && documentoCese != null && !documentoCese.isBlank();
    }
}
