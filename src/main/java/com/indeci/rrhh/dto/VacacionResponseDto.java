package com.indeci.rrhh.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Data;

@Data
public class VacacionResponseDto {

    private Long id;

    private Long empleadoId;

    private String empleado;

    private String periodo;

    private LocalDate periodoDesde;

    private LocalDate periodoHasta;

    private BigDecimal diasGanados;

    private String observacion;

    private Integer activo;
}