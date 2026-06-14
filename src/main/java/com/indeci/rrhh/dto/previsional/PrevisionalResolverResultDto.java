package com.indeci.rrhh.dto.previsional;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PrevisionalResolverResultDto {
    private boolean    encontrado;
    private String     sistemaPensionario;
    private String     afpNombre;
    private String     tipoComision;
    private BigDecimal aporteOnpPct;
    private BigDecimal aporteObligatorioPct;
    private BigDecimal comisionFlujoPct;
    private BigDecimal comisionSaldoAnualPct;
    private BigDecimal primaSeguroPct;
    private BigDecimal remuneracionMaximaAsegurable;
    private String     vigenciaInicio;
    private String     vigenciaFin;
    private String     fuente;
    private String     mensaje;
}
