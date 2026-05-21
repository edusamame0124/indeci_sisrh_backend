package com.indeci.rrhh.dto;

import lombok.Data;

/**
 * Spec 012 / C3 (BKD-006) — Estado agregado del flujo de configuración de un
 * empleado (pasos 1–5: puesto, banco, pensión, planilla, conceptos).
 *
 * <p>Reemplaza las 5 peticiones GET paralelas que el frontend disparaba para
 * deducir qué pasos del flujo RRHH ya tienen registros. El paso 0 («datos
 * personales») lo resuelve el frontend a partir de la ficha de persona.
 */
@Data
public class EmpleadoFlowStatusDto {

    private Long empleadoId;

    /** Tiene al menos un registro de puesto laboral. */
    private boolean puesto;

    /** Tiene al menos una cuenta bancaria activa. */
    private boolean banco;

    /** Tiene configuración de pensión activa. */
    private boolean pension;

    /** Tiene configuración de planilla activa. */
    private boolean planilla;

    /** Tiene al menos un concepto asignado activo. */
    private boolean conceptos;
}
