package com.indeci.rrhh.dto;

import lombok.Data;

import java.util.List;

/**
 * Spec 011 / C2 (BKD-001) — Resultado de la generación masiva de planilla.
 *
 * Reemplaza el antiguo {@code Void}: la generación masiva ya no aborta cuando
 * un empleado falla — procesa todos y reporta exitosos vs fallidos.
 */
@Data
public class GeneracionMasivaResultDto {

    /** Empleados con configuración de planilla considerados. */
    private Integer total;

    /** Empleados cuya planilla se generó correctamente. */
    private Integer exitosos;

    /** Empleados que fallaron, con el motivo. */
    private List<GeneracionFallidaDto> fallidos;
}
