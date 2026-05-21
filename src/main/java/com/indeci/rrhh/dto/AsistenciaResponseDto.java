package com.indeci.rrhh.dto;

import lombok.Data;

import java.util.List;

/**
 * Asistencia de un empleado en un período (M04 / SPEC §12.2 PANTALLA-02).
 * Si la asistencia aún no existe, {@code id} llega null y {@code dias} vacío:
 * el frontend arma un calendario nuevo.
 */
@Data
public class AsistenciaResponseDto {

    private Long id;

    private Long empleadoId;

    private String periodo;

    private Double remuneracionBase;

    private Integer diasLaborados;

    private Integer diasFalta;

    private Integer totalMinTardanza;

    /** REGLA 276-02: ROUND((remuneracionBase/30/8/60) * totalMinTardanza, 2). */
    private Double descuentoTardanza;

    /** REGLA 276-02: ROUND((remuneracionBase/30) * diasFalta, 2). */
    private Double descuentoFalta;

    private String estado;

    private String observacion;

    private List<AsistenciaDiaDto> dias;
}
