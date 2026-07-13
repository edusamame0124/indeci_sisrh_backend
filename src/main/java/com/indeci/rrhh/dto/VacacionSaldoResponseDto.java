package com.indeci.rrhh.dto;

import lombok.Data;

/** Saldo de vacaciones de un año — para el listado del Portal del Empleado (Spec 011/B5). */
@Data
public class VacacionSaldoResponseDto {
    private Long id;
    private Long empleadoId;
    private Integer anio;
    private Double diasGanados;
    private Double diasGozados;
    private Double diasSaldo;
    private String observacion;
}
