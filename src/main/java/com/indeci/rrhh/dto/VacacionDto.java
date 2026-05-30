package com.indeci.rrhh.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Data;

@Data
public class VacacionDto {

    private Long empleadoId;

    private String periodo;

    private LocalDate periodoDesde;

    private LocalDate periodoHasta;

    private String observacion;
}