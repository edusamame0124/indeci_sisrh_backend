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
    // V010_95 — reglas de descuento por tardanza (Descuento 1 / Descuento 2).
    private Integer umbralTardanzaDiariaMin;
    private Integer topeTardanzaMensualMin;
    private BigDecimal jornadaHoras;
}
