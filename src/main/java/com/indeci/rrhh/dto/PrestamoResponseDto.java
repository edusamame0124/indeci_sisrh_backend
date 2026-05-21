package com.indeci.rrhh.dto;

import lombok.Data;

import java.time.LocalDate;

/** Response de un préstamo con el saldo pendiente derivado. */
@Data
public class PrestamoResponseDto {

    private Long id;

    private Long empleadoId;

    private String descripcion;

    private Double montoTotal;

    private Integer numeroCuotas;

    private Double cuotaMensual;

    private Integer cuotasPagadas;

    /** MONTO_TOTAL − CUOTAS_PAGADAS × CUOTA_MENSUAL (nunca negativo). */
    private Double saldoPendiente;

    /** ACTIVO | CANCELADO. */
    private String estado;

    private LocalDate fechaInicio;
}
