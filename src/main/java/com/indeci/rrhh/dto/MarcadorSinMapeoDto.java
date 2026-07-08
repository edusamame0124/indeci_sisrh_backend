package com.indeci.rrhh.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Nombre del marcador (reporte COEN) que no pudo mapearse a un empleado en una
 * importación, con la cantidad de días afectados. Alimenta el panel de mapeo (SPEC D1).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MarcadorSinMapeoDto {
    private String nombreMarcador;
    private long dias;
}
