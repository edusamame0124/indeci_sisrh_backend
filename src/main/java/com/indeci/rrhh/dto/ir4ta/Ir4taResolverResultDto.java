package com.indeci.rrhh.dto.ir4ta;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Resultado del resolver de configuración IR4ta por período. */
@Data
public class Ir4taResolverResultDto {
    private boolean encontrado;
    private String periodoConsultado;
    private Integer anioFiscal;
    private Long vigenciaId;
    private LocalDate vigenciaInicio;
    private LocalDate vigenciaFin;
    private BigDecimal uitVigente;
    private BigDecimal tasaIr4ta;
    private BigDecimal baseInafectaIr4ta;
    private String fuenteOficial;
    private String estadoValidacion;
    private String mensaje;
}
