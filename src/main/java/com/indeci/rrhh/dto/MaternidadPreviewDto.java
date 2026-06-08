package com.indeci.rrhh.dto;

import lombok.Data;

import java.util.List;

/** Resultado del preview de impacto en planilla (sin persistir). */
@Data
public class MaternidadPreviewDto {

    private boolean cruzaMeses;
    private int cantidadPeriodos;
    private String codigoPlameSunat;
    private boolean afectaDiasLaborados;
    private boolean generaSubsidio;
    private boolean sumaAlNeto;
    private String mensajeGuardrail;
    private List<EventoDistribucionMesDto> distribucionMensual;
}
