package com.indeci.rrhh.dto;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class SaldoVacacionalDto {

    private BigDecimal diasGanados;

    private BigDecimal diasGozados;

    private BigDecimal saldo;
}