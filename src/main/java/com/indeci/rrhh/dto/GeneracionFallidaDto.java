package com.indeci.rrhh.dto;

import lombok.Data;

/**
 * Spec 011 / C2 (BKD-001) — Empleado cuya generación de planilla falló
 * durante la generación masiva, con el motivo del fallo.
 */
@Data
public class GeneracionFallidaDto {

    private Long empleadoId;

    private String razon;
}
