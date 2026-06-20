package com.indeci.rrhh.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Resumen de estado de metas para un año fiscal. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MetaPptoResumenDto {

    private Integer anioFiscal;
    private long totalMetasCatalogo;
    private long totalMetasPublicadas;
    private long totalEmpleadosAsignados;
    private long totalEmpleadosPublicados;
    private long totalEmpleadosSinMeta;
    private long totalEquivalencias;
    private String estadoGeneral;
}
