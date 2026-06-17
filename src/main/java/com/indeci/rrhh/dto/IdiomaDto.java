package com.indeci.rrhh.dto;

import lombok.Data;

@Data
public class IdiomaDto {

    private Long empleadoId;

    private String idioma;

    private String nivelLectura;

    private String nivelEscritura;

    private String nivelHabla;

    private Integer certificado;

    private Long legajoDocumentoId;
}