package com.indeci.rrhh.dto.previsional;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PrevisionalResolverResultDto {
    private boolean    encontrado;
    // Contexto del empleado
    private Long       empleadoId;
    private String     empleadoNombre;
    private String     documento;
    private String     periodoConsultado;
    // Resultado de validación: VALIDO | CONFIG_INCOMPLETA | SIN_VIGENCIA
    private String     estadoValidacion;
    // Datos previsionales
    private Long       vigenciaId;
    private String     sistemaPensionario;
    private Long       afpId;
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
