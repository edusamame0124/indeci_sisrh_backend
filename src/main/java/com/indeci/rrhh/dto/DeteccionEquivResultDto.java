package com.indeci.rrhh.dto;

import lombok.Data;

/**
 * Resultado de la detección automática de equivalencias para una meta origen.
 * estadoDeteccion: OK_AUTOMATICO | SIN_COINCIDENCIA | COINCIDENCIA_MULTIPLE | OBSERVADO
 */
@Data
public class DeteccionEquivResultDto {

    private Integer anioOrigen;
    private Long metaOrigenId;
    private String metaOrigenCodigo;

    private String centroCosto;
    private String categoriaPresupuestal;
    private String producto;
    private String actividad;
    private String finalidad;

    private long trabajadoresAsignados;

    /** Nulo cuando estadoDeteccion es SIN_COINCIDENCIA o COINCIDENCIA_MULTIPLE */
    private Long metaDestinoId;
    private String metaDestinoCodigo;

    /** OK_AUTOMATICO | SIN_COINCIDENCIA | COINCIDENCIA_MULTIPLE | OBSERVADO */
    private String estadoDeteccion;

    private String observacion;

    /** ID de la equivalencia creada/encontrada; nulo si no se creó. */
    private Long equivalenciaId;
}
