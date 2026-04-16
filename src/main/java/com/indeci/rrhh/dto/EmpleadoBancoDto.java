package com.indeci.rrhh.dto;

import lombok.Data;

@Data
public class EmpleadoBancoDto {

    private Long empleadoId;
    private Long bankId;
    private Long accountTypeId;
    private String numeroCuenta;
    private String cci;
    private Integer esCuentaPlanilla;
}