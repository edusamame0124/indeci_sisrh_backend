package com.indeci.rrhh.service.asistencia;

import com.indeci.exception.NegocioException;

public enum AsistenciaImportEstrategia {
    OMITIR_EXISTENTES,
    REEMPLAZAR_EMPLEADOS_ARCHIVO,
    REEMPLAZAR_PERIODO_COMPLETO,
    CANCELAR;

    public static AsistenciaImportEstrategia desde(String value) {
        if (value == null || value.isBlank()) {
            return OMITIR_EXISTENTES;
        }
        try {
            return AsistenciaImportEstrategia.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new NegocioException("Estrategia de conflicto de asistencia no reconocida.");
        }
    }

    public boolean omiteExistente() {
        return this == OMITIR_EXISTENTES;
    }

    public boolean cancelaConConflicto() {
        return this == CANCELAR;
    }

    public boolean reemplazaExistente() {
        return this == REEMPLAZAR_EMPLEADOS_ARCHIVO
                || this == REEMPLAZAR_PERIODO_COMPLETO;
    }
}
