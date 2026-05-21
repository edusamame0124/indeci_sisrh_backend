package com.indeci.rrhh.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * Spec 010 / M14 — Response de una fila de abono bancario.
 */
@Data
public class AbonoBancoResponseDto {

    private Long id;
    private Long movimientoPlanillaId;
    private Long empleadoId;
    private String banco;
    private String nroCuenta;
    private String cci;
    private String meta;
    private Double montoNeto;
    private String estado;
    private String nroTicketMcpp;
    private LocalDate fechaProcesado;
}
