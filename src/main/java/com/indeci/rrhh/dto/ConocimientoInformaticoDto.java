package com.indeci.rrhh.dto;

import lombok.Data;

@Data
public class ConocimientoInformaticoDto {

    private Long empleadoId;

    private String herramienta;

    private String nivel;

    private Integer certificado;

    private Long legajoDocumentoId;
}