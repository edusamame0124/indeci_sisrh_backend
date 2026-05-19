package com.indeci.rrhh.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SolicitudRrhhDocResponseDto {

    private Long id;

    private Long solicitudId;

    private String etapa;

    private String nombreArchivo;

    private String rutaArchivo;

    private Integer versionDoc;

    private String observacion;

    private String usuarioUpload;

    private LocalDateTime createdAt;
}