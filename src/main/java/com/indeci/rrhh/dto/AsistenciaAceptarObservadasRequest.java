package com.indeci.rrhh.dto;

import lombok.Data;

import java.util.List;

/**
 * F5 / P3 — aceptación expresa de filas OBSERVADAS por RRHH.
 * Si {@code idsFilas} viene vacío/nulo, se aceptan todas las observadas de la importación.
 * El {@code motivo} es obligatorio.
 */
@Data
public class AsistenciaAceptarObservadasRequest {

    private List<Long> idsFilas;
    private String motivo;
}
