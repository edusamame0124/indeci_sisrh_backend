package com.indeci.rrhh.dto;

import lombok.Data;

@Data
public class AsistenciaImportConfirmRequest {

    private Long importacionId;

    /**
     * OMITIR_EXISTENTES | REEMPLAZAR_EMPLEADOS_ARCHIVO | REEMPLAZAR_PERIODO_COMPLETO | CANCELAR
     */
    private String estrategiaConflicto;
}
