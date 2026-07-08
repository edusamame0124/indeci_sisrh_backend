package com.indeci.rrhh.dto;

import lombok.Data;

/** Alias de marcador creado/existente (respuesta del mapeo). */
@Data
public class MarcadorAliasDto {
    private Long id;
    private Long empleadoId;
    private String nombreMarcadorNorm;
    private String nombreMarcadorOriginal;
    private String origen;
}
