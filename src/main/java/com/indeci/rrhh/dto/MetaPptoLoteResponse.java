package com.indeci.rrhh.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** Response del lote de proceso masivo. */
@Data
public class MetaPptoLoteResponse {

    private Long id;
    private String codigoLote;
    private Integer anioOrigen;
    private Integer anioDestino;
    private String tipoProceso;
    private String estado;
    private Integer totalEmpleados;
    private Integer totalAsignados;
    private Integer totalObservados;
    private Integer totalErrores;
    private Integer totalSinEquiv;
    private Integer totalInactivos;
    private Integer totalDuplicados;
    private String archivoOrigen;
    private String observacion;
    private String creadoPor;
    private LocalDateTime creadoEn;
    private LocalDateTime finalizadoEn;
    private String motivoAnulacion;
}
