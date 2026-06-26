package com.indeci.rrhh.dto.ir4ta;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class Ir4taAcumuladoDetalleDto {
    private String periodo;
    private BigDecimal ingresosBrutos;
    private BigDecimal deducciones;
    private BigDecimal baseAfecta;
}
