package com.indeci.rrhh.dto;

import java.time.LocalDate;

import lombok.Data;

@Data
public class AsistenciaImportConfirmRequest {

    private Long importacionId;

    /**
     * Rango de días efectivamente CUBIERTO por esta carga (editable por RR.HH.; el sistema lo
     * sugiere con el mín/máx detectado en el archivo). Las FALTAS por "día laborable sin marca"
     * se generan SOLO dentro de [diaInicio, diaFin]; los días del mes fuera del rango quedan
     * SIN REGISTRO (no se asume falta sin datos). Si viene null, se usa el rango detectado y, en
     * su defecto, el mes completo (compatibilidad hacia atrás).
     */
    private LocalDate diaInicio;
    private LocalDate diaFin;

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
