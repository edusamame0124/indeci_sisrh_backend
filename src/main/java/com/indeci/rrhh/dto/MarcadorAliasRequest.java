package com.indeci.rrhh.dto;

import lombok.Data;

/**
 * Petición para mapear un nombre del marcador (COEN) a un empleado del sistema
 * (crea el alias de identidad, SPEC D1). El nombre se normaliza en el servicio.
 */
@Data
public class MarcadorAliasRequest {
    private Long empleadoId;
    private String nombreMarcador;
    /** Reloj de origen; por defecto "COEN". */
    private String origen;
}
