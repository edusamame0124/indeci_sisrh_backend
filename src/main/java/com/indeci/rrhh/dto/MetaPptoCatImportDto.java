package com.indeci.rrhh.dto;

import lombok.Data;

import java.util.List;

/** Request para importar masivamente el catálogo desde Excel. */
@Data
public class MetaPptoCatImportDto {

    private Integer anioFiscal;
    private List<MetaPptoCatDto> filas;
    private boolean sobreescribir;
}
