package com.indeci.rrhh.dto;

import lombok.Data;

/**
 * SPEC_CONCEPTOS_PLANILLA P1 — fila del catálogo RTPS (PDT 601) expuesta a la UI.
 *
 * <p>{@code seleccionable} se deriva de {@code esGrupo}: solo los items
 * ({@code esGrupo='N'}) pueden asignarse como RTPS de un concepto; las cabeceras
 * de grupo ({@code esGrupo='S'}) sirven únicamente para agrupar visualmente.</p>
 */
@Data
public class ConceptoRtpsDto {

    private String codigo;
    private String descripcion;
    private String grupoCodigo;
    private String grupoDescripcion;
    private String esGrupo;
    private Integer orden;

    /** Conveniencia para la UI: {@code true} si {@code esGrupo != 'S'}. */
    private boolean seleccionable;
}
