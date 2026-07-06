package com.indeci.rrhh.entity;

/**
 * Track B — Motivo normativo de un reintegro/devengado de {@code INDECI_REINTEGRO_MONTO}
 * (Modelo B, pagado por Planilla Adicional). Contrato cerrado: cualquier valor fuera
 * de este enum es rechazado con 400 antes de tocar la base de datos.
 *
 * <ul>
 *   <li>{@code DEVENGADO_JUDICIAL} — pago por sentencia/laudo/acta de conciliación.</li>
 *   <li>{@code REPOSICION} — reincorporación: remuneraciones dejadas de percibir.</li>
 *   <li>{@code RETROACTIVO} — asignación/bonificación reconocida tardíamente.</li>
 *   <li>{@code DIFERENCIA_REMUNERATIVA} — corrección/actualización de escala o rol.</li>
 * </ul>
 */
public enum MotivoReintegro {
    DEVENGADO_JUDICIAL,
    REPOSICION,
    RETROACTIVO,
    DIFERENCIA_REMUNERATIVA
}
