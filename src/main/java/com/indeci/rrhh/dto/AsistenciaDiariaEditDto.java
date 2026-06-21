package com.indeci.rrhh.dto;

import lombok.Data;

/**
 * Cuerpo PATCH de edición de un día en consulta diaria (M04).
 */
@Data
public class AsistenciaDiariaEditDto {

    private String tipoDia;
    private String marcaEntrada;
    private String marcaSalida;
    private Integer minutosTardanza;
    private String observacion;

    /** true = autorizada (Presente), false = no autorizada (Observado descontable). */
    private Boolean papeletaAutorizada;
    private String papeletaMotivoRechazo;
}
