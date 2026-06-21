package com.indeci.rrhh.dto.essalud;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class EssaludVigenciaDto {
    private Long        id;
    private Integer     anioVigencia;
    private LocalDate   vigenciaInicio;
    private LocalDate   vigenciaFin;
    private BigDecimal  uitVigente;
    private BigDecimal  pctBaseCas;
    private BigDecimal  pctEssalud;
    private BigDecimal  pctEssaludEps;
    private BigDecimal  pctCreditoEps;
    // Campos calculados
    private BigDecimal  baseMaximaCas;        // uitVigente × pctBaseCas/100
    private BigDecimal  essaludMaximoCas;     // baseMaximaCas × pctEssalud/100
    private BigDecimal  essaludConEpsMax;     // baseMaximaCas × pctEssaludEps/100
    private BigDecimal  creditoEpsMax;        // baseMaximaCas × pctCreditoEps/100
    private String      fuenteOficial;
    private String      urlFuenteOficial;
    private LocalDate   fechaPublicacion;
    private String      observacion;
    private String      estado;
    private boolean     bloqueadoPorPlanilla;
    private String      creadoPor;
    private LocalDateTime creadoEn;
}
