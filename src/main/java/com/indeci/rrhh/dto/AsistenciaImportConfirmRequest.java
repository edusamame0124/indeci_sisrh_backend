package com.indeci.rrhh.dto;

import lombok.Data;

@Data
public class AsistenciaImportConfirmRequest {

    private Long importacionId;

    /**
     * OMITIR_EXISTENTES | REEMPLAZAR_EMPLEADOS_ARCHIVO | REEMPLAZAR_PERIODO_COMPLETO | CANCELAR
     */
    private String estrategiaConflicto;

    /**
     * F5 / P4 — motivo de rectificación. Obligatorio cuando se reemplaza una asistencia
     * ya VALIDADA o cuando el periodo está GENERADO. La autorización NO depende de este
     * campo: el backend valida el rol del usuario autenticado (PLA_APPROVE).
     */
    private String motivoRectificacion;
}
