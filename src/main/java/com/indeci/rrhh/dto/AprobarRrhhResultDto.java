package com.indeci.rrhh.dto;

import java.util.List;

/**
 * P3 (2026-08-07) — Resultado de aprobar una solicitud/papeleta por RR.HH.: advertencias
 * no bloqueantes que RR.HH. debe conocer aunque la aprobación en sí haya sido exitosa.
 * Hoy la única advertencia posible es que la papeleta cubría un período cuya planilla ya
 * está CERRADA/APROBADA (LEY-05, inmutable) — se aprueba igual, pero no reconcilia esa
 * asistencia. Mismo patrón que {@link AguinaldoResultDto}.
 */
public record AprobarRrhhResultDto(List<String> advertencias) {}
