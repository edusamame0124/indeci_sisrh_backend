package com.indeci.rrhh.dto;

import lombok.Data;

@Data
public class EmpleadoBancoResponseDto {

    private Long id;

    private Long bankId;

    private Long accountTypeId;

    private String numeroCuenta;

    private String cci;

    private Integer esCuentaPlanilla;

    private Integer activo;
    
    private String bank;

    private String accountType;
    
}