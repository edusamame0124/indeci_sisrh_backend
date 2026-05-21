package com.indeci.rrhh.dto;

import lombok.Data;

/** Cuerpo POST para registrar/actualizar el saldo de vacaciones (UPSERT por año). */
@Data
public class VacacionSaldoDto {

    private Long empleadoId;

    private Integer anio;

    private Double diasGanados;

    private Double diasGozados;

    private String observacion;
}
