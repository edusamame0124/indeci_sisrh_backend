package com.indeci.rrhh.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * F2.5 — Request para crear/actualizar un evento del período del empleado.
 *
 * <p>Campos opcionales con default:</p>
 * <ul>
 *   <li>{@code periodo}: si null, se deriva de {@code fechaInicio}
 *       ({@code "YYYYMM"}).</li>
 *   <li>{@code diasAfectos}: si null, se deriva de
 *       {@code fechaFin - fechaInicio + 1}.</li>
 *   <li>{@code sustentoLegajoDocId}: el service exige no-null si
 *       {@code tipoEvento.requiereAdjunto = 'S'}.</li>
 * </ul>
 */
@Data
public class EventoPeriodoDto {

    private Long empleadoId;
    private Long tipoEventoId;

    /** Formato {@code "YYYYMM"} o {@code "YYYY-MM"}. Opcional. */
    private String periodo;

    private LocalDate fechaInicio;
    private LocalDate fechaFin;

    /** Si null → derivado de {@code fechaFin - fechaInicio + 1}. */
    private Integer diasAfectos;

    /** Sustento documental opcional. FK a {@code INDECI_LEGAJO_DOCUMENTO}. */
    private Long sustentoLegajoDocId;

    private String observacion;
}
