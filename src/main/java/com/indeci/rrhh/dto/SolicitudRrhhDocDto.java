package com.indeci.rrhh.dto;

import lombok.Data;

@Data
public class SolicitudRrhhDocDto {

    private Long solicitudId;

    private String etapa;

    private String nombreArchivo;

    private String rutaArchivo;

    private Integer versionDoc;

    private String observacion;
}