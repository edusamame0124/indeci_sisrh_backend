package com.indeci.rrhh.dto;

import lombok.Data;

import java.util.List;

/** Body del endpoint PATCH /catalogo/estado */
@Data
public class CambioEstadoMasivoDto {
    private List<Long> ids;
    private String nuevoEstado;
    private String motivo;
}
