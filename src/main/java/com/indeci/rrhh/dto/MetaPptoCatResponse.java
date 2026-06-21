package com.indeci.rrhh.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** Response de una entrada del catálogo de metas presupuestales. */
@Data
public class MetaPptoCatResponse {

    private Long id;
    private Integer anioFiscal;
    private String metaCodigo;
    private String centroCosto;
    private String categoriaPresupuestal;
    private String producto;
    private String actividad;
    private String finalidad;
    private String metaHash;
    private String estado;
    private Integer activo;
    private String fuente;
    private String observacion;
    private String creadoPor;
    private LocalDateTime creadoEn;
    private String modificadoPor;
    private LocalDateTime modificadoEn;
    private String motivoAnulacion;
}
