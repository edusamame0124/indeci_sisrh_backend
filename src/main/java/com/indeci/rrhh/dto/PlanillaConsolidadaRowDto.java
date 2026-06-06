package com.indeci.rrhh.dto;

import lombok.Data;

/**
 * Fila de la tabla consolidada de Configuración de planilla (todos los empleados
 * activos). Si el empleado no tiene planilla activa, {@code tieneConfig=false} y
 * los campos de planilla van en null (la UI muestra "Sin configurar").
 */
@Data
public class PlanillaConsolidadaRowDto {

    private Long empleadoId;
    private Long personaId;
    private String nombreCompleto;
    private String dni;
    private String codigoInterno;

    private boolean tieneConfig;
    private Long planillaId;

    private String regimenLaboral;   // código (CAS, 728…)
    private String tipoContrato;     // nombre
    private String condicionLaboral; // nombre
    private Double sueldoBasico;
    private Double movilidad;
    private Double alimentacion;
    private Integer tieneAsignacionFamiliar;
    private Integer numHijos;
}
