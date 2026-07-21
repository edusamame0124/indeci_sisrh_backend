package com.indeci.rrhh.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AsistenciaImportHistorialDto {

    private Long id;
    private String periodo;
    private String nombreArchivo;
    private String usuario;
    private LocalDateTime fechaImportacion;
    private String estado;
    private int filasTotal;
    private int filasValidas;
    private int filasError;
    private int empleadosProcesados;

    /** REQUIERE_CALCULO | PARCIAL | VALIDADO | null (no aplica: borrador/anulada). Solo lectura. */
    private String estadoValidacion;

    // --- Desglose de validación (badge tri-estado del Historial) ---
    /** Cabeceras activas totales de la importación. */
    private long cabecerasTotal;
    /** Cabeceras ya VALIDADA (consumibles por el motor). */
    private long cabecerasValidadas;
    /** Cabeceras OBSERVADA (con error/observación por corregir; no se auto-validan). */
    private long cabecerasObservadas;
    /** Cabeceras PREVALIDADA/LISTA_PARA_VALIDAR (aún sin ejecutar el cálculo). */
    private long cabecerasPendientes;
}
