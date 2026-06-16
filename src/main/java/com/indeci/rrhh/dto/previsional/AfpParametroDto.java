package com.indeci.rrhh.dto.previsional;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class AfpParametroDto {
    private Long         id;
    private Long         afpId;
    private String       afpNombre;
    private String       periodoInicio;
    private String       periodoFin;
    private BigDecimal   aporteObligatorioPct;
    private BigDecimal   comisionFlujoPct;
    private BigDecimal   comisionSaldoAnualPct;
    private BigDecimal   primaSeguroPct;
    private BigDecimal   remuneracionMaximaAseg;
    private String       fuenteOficial;
    private String       urlFuenteOficial;
    private LocalDate    fechaPublicacion;
    private String       observacion;
    private String       estado;
    private boolean      bloqueadoPorPlanilla;
    private String       creadoPor;
    private LocalDateTime creadoEn;
}
