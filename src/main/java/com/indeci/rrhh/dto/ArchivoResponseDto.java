package com.indeci.rrhh.dto;

import lombok.Data;

@Data
public class ArchivoResponseDto {

    private String rutaArchivo;

    private String nombreArchivo;

    private String mimeType;

    private Long tamanioBytes;
}