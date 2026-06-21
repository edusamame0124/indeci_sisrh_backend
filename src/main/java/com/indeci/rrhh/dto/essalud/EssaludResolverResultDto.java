package com.indeci.rrhh.dto.essalud;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class EssaludResolverResultDto {
    private boolean    encontrado;
    private Long       empleadoId;
    private String     empleadoNombre;
    private String     documento;
    private String     periodoConsultado;
    private String     regimenLaboral;

    // Parámetros de vigencia usados
    private Long       vigenciaId;
    private LocalDate  vigenciaInicio;
    private LocalDate  vigenciaFin;
    private BigDecimal uitVigente;
    private BigDecimal pctBaseCas;
    private BigDecimal pctEssalud;
    private BigDecimal pctEssaludEps;
    private BigDecimal pctCreditoEps;
    private String     fuenteOficial;

    // Datos del empleado
    private BigDecimal remuneracionCas;
    private boolean    tieneEps;

    // Cálculo
    private BigDecimal limiteUit;        // UIT × pctBaseCas/100
    private BigDecimal baseAplicable;    // min(remuneracion, limiteUit)
    private BigDecimal essalud9;         // base × pctEssalud/100
    private BigDecimal essaludEps675;    // base × pctEssaludEps/100 (si EPS)
    private BigDecimal creditoEps225;    // base × pctCreditoEps/100 (si EPS)

    private String estadoValidacion;    // VALIDO | SIN_VIGENCIA | CONFIG_INCOMPLETA
    private String mensaje;
}
