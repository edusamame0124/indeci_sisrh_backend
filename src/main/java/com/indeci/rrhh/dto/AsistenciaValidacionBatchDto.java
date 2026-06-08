package com.indeci.rrhh.dto;

import lombok.Data;

@Data
public class AsistenciaValidacionBatchDto {

    private Long importacionId;
    private String periodo;
    private int totalCabeceras;
    private int validadas;
    private int omitidas;
    private int observadas;
    private int yaValidadas;
}
