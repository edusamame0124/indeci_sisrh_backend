package com.indeci.rrhh.dto;

import lombok.Data;

/**
 * Spec 010 / M14 — Request para registrar una fila de abono bancario.
 */
@Data
public class AbonoBancoDto {

    private Long movimientoPlanillaId;
    private Long empleadoId;
    private String banco;
    private String nroCuenta;
    private String cci;
    private String meta;
    private Double montoNeto;
}
