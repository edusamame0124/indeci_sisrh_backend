package com.indeci.rrhh.dto;

import lombok.Data;

@Data
public class DocumentoRequeridoDto {

    private Long id;

    private String nombre;

    private Boolean obligatorio;
}