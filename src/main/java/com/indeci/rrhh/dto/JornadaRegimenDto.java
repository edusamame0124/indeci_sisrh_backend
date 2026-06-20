package com.indeci.rrhh.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Configuración de jornada por régimen (request/response). Las horas en formato 'HH:mm'.
 */
@Data
public class JornadaRegimenDto {

    private Long id;
    private Long regimenLaboralId;
    private String regimenCodigo;   // solo respuesta (1057, 276, ...)
    private String regimenNombre;   // solo respuesta

    private String horaIngreso;
    private String horaSalida;
    private String refrigerioInicio;
    private String refrigerioFin;
    private Integer toleranciaIngresoMin;
    private Integer toleranciaAlmuerzoMin;
    private BigDecimal jornadaHoras;
}
