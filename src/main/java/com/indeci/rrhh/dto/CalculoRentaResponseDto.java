package com.indeci.rrhh.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class CalculoRentaResponseDto {
    private BigDecimal retencionCalculada;
    private String categoria;
    private String observacion;
}
