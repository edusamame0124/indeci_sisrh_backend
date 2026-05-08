package com.indeci.rrhh.dto;

import lombok.Data;

@Data
public class EmpleadoPlanillaDto {

    private Long empleadoId;

    // =====================================
    // CONFIGURACION BASE
    // =====================================

    private Double sueldoBasico;

    private Double movilidad;

    private Double alimentacion;

    // =====================================
    // ASIGNACION FAMILIAR
    // =====================================

    private Integer tieneAsignacionFamiliar;

    private Integer numHijos;

    // =====================================
    // DESCUENTOS FIJOS
    // =====================================

    private Double descuentoBanco;

    private Double descuentoInstitucion;
}