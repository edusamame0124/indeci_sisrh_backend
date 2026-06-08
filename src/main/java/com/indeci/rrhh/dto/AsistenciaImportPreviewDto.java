package com.indeci.rrhh.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AsistenciaImportPreviewDto {

    private Long importacionId;
    private String periodo;
    private String nombreArchivo;
    private String encoding;
    private String hashArchivo;
    private int filasTotal;
    private int filasValidas;
    private int filasValidasLimpias;
    private int filasAdvertencia;
    private int filasError;
    private int filasObservadas;
    private int empleadosDetectados;
    private int empleadosConError;
    private String estadoImportacion;
    private String mensaje;
    private List<AsistenciaImportEmpleadoResumenDto> empleados = new ArrayList<>();
    private List<AsistenciaImportFilaErrorDto> errores = new ArrayList<>();
}
