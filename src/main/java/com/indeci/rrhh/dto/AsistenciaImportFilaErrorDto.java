package com.indeci.rrhh.dto;

import lombok.Data;

@Data
public class AsistenciaImportFilaErrorDto {

    private int linea;
    private String dni;
    private String fecha;
    private String severidad;
    private String mensaje;
    private String contenido;
}
