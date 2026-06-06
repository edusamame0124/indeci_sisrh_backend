package com.indeci.rrhh.dto;

import lombok.Data;

@Data
public class EmpleadoPlanillaResponseDto {

    private Long id;

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
    // CONFIGURACIÓN LABORAL (mejora 2026-06-03 — para prefill en edición)
    // =====================================

    private Long regimenLaboralId;

    private Long tipoContratoId;

    private Long condicionLaboralId;

    // Etiquetas resueltas para mostrar en el listado (no requieren catálogos en UI).
    private String regimenLaboral;   // código (ej. CAS, 728)

    private String tipoContrato;     // nombre

    private String condicionLaboral; // nombre

    // =====================================
    // ESTADO
    // =====================================

    private Integer activo;
}