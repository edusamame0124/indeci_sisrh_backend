package com.indeci.rrhh.dto;

import lombok.Data;

/** Request para crear/editar una entrada del catálogo de metas presupuestales. */
@Data
public class MetaPptoCatDto {

    private Integer anioFiscal;
    private String metaCodigo;
    private String centroCosto;
    private String categoriaPresupuestal;
    private String producto;
    private String actividad;
    private String finalidad;
    private String fuente;
    private String observacion;
}
