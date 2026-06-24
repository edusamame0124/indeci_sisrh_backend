package com.indeci.rrhh.dto;

import lombok.Data;

/**
 * SPEC_CONCEPTOS_PLANILLA §15 / Fase A — fila del catálogo de tipos de planilla.
 *
 * <p>Sirve tanto de request (alta/edición admin) como de response (listado). En el
 * alta el {@code codigo} es obligatorio (PK natural); en la edición se toma del path.</p>
 */
@Data
public class PlanillaTipoDto {

    private String codigo;
    private String nombre;
    private Integer orden;
    private Integer activo;
}
