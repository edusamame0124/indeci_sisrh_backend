package com.indeci.rrhh.dto;

import java.util.List;

/**
 * F3.4 — Criterio de selección de empleados para el Asistente de Recálculo.
 *
 * <p>Tipos soportados:</p>
 * <ul>
 *   <li>{@code TODOS} — todos los empleados activos con planilla configurada.</li>
 *   <li>{@code REGIMEN_LABORAL} — filtra por
 *       {@code valorString} = código del régimen ("276", "728", "CAS", "SERVIR").</li>
 *   <li>{@code EMPLEADOS_LISTA} — usa {@code valorListaIds} (lista de
 *       {@code empleadoId}s explícitos).</li>
 *   <li>{@code CON_PREFLIGHT_PENDIENTE} — invoca internamente al servicio del
 *       Centro de Validaciones (F3.3) y selecciona empleados con cualquier
 *       hallazgo BLOQUEO o ALERTA cuyo {@code empleadoId} no sea null.</li>
 * </ul>
 *
 * <p>{@code valorString} y {@code valorListaIds} son opcionales según el tipo.
 * El service valida que el campo correspondiente venga seteado.</p>
 */
public record RecalculoCriterioDto(
        String tipo,
        String valorString,
        List<Long> valorListaIds) {
}
