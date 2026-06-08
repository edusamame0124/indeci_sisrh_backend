package com.indeci.rrhh.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AsistenciaImportHistorialDto {

    private Long id;
    private String periodo;
    private String nombreArchivo;
    private String usuario;
    private LocalDateTime fechaImportacion;
    private String estado;
    private int filasTotal;
    private int filasValidas;
    private int filasError;
    private int empleadosProcesados;
}
