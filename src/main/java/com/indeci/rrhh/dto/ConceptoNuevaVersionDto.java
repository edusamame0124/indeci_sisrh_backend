package com.indeci.rrhh.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * SPEC_CONCEPTOS_PLANILLA §12 / P3 — body de
 * {@code POST /api/rrhh/concepto-planilla/{id}/nueva-version}.
 * Solicita la fecha de inicio de vigencia de la nueva versión.
 */
@Data
public class ConceptoNuevaVersionDto {

    /** Fecha de inicio de vigencia de la nueva versión (obligatoria). */
    private LocalDate fechaVigIni;
}
