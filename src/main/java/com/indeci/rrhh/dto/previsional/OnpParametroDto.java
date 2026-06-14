package com.indeci.rrhh.dto.previsional;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class OnpParametroDto {
    private Long         id;
    private String       periodoInicio;
    private String       periodoFin;
    private BigDecimal   aporteOnpPct;
    private String       fuenteOficial;
    private String       urlFuenteOficial;
    private LocalDate    fechaPublicacion;
    private String       observacion;
    private String       estado;
    private boolean      bloqueadoPorPlanilla;
    private String       creadoPor;
    private LocalDateTime creadoEn;
}
