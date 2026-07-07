package com.indeci.rrhh.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Proyección ligera para el listado de personas — 1 query JOIN sin N+1. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PersonaResumenDto {
    private Long id;
    private Long empleadoId;
    private String nombreCompleto;
    private String dni;
    private String codigoInterno;
    private String estado;
    /** Código del régimen laboral vigente (CAS, 728, 276, SERVIR) — puede ser null. */
    private String regimenLaboral;
    private String ruc;
    private String estadoCivil;
    private String profesion;
    private String gradoAcademico;
}
