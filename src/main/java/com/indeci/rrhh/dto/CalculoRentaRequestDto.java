package com.indeci.rrhh.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class CalculoRentaRequestDto {
    private Long empleadoId;
    private Integer anioFiscal;
    private Integer mes;
    private String regimenLaboralCodigo;
    private BigDecimal ingresosMesActual;
}
