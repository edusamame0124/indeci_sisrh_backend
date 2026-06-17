package com.indeci.rrhh.dto;

import lombok.Data;

@Data
public class ConocimientoInformaticoResponseDto {

    private Long id;

    private Long empleadoId;

    private String herramienta;

    private String nivel;

    private Integer certificado;

    private Long legajoDocumentoId;
}