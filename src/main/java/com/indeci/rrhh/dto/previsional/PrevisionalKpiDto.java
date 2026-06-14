package com.indeci.rrhh.dto.previsional;

import lombok.Data;

@Data
public class PrevisionalKpiDto {
    private long   afpVigentes;
    private long   onpVigente;
    private long   proximaVigencia;
    private String ultimaActualizacionSbs;
}
