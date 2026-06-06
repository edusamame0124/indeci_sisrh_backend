package com.indeci.rrhh.dto;

import java.util.List;

import lombok.Data;

/**
 * Conceptos que se le pueden asignar manualmente a un empleado, ya filtrados por
 * su régimen laboral (mejora 2026-06-03). El frontend usa {@code regimenLaboral}
 * para el encabezado del modal y {@code conceptos} para el dropdown.
 */
@Data
public class ConceptosAsignablesDto {

    /** Código del régimen laboral vigente del empleado (CAS, 728…); null si no tiene planilla. */
    private String regimenLaboral;

    /** Conceptos activos aplicables a ese régimen (o 'TODOS'). */
    private List<ConceptoPlanillaResponseDto> conceptos;
}
