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

    // =====================================
    // CONFIGURACIÓN LABORAL (mejora 2026-06-03)
    // Régimen es clave para el motor (decide 5ta/4ta, asig. familiar, topes).
    // =====================================

    private Long regimenLaboralId;

    private Long tipoContratoId;

    private Long condicionLaboralId;
}