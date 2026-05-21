package com.indeci.rrhh.dto;

import lombok.Data;

/** Response del saldo de vacaciones de un año, con el saldo de días derivado. */
@Data
public class VacacionSaldoResponseDto {

    private Long id;

    private Long empleadoId;

    private Integer anio;

    private Double diasGanados;

    private Double diasGozados;

    /** DIAS_GANADOS − DIAS_GOZADOS. */
    private Double diasSaldo;

    private String observacion;
}
