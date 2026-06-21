package com.indeci.rrhh.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * F2 — Resumen liviano de una importación para la banda de estado del paso "Validar"
 * (SpecAsistencia.md sección I/13). Separado del preview pesado.
 */
@Data
public class AsistenciaImportResumenDto {

    private Long importacionId;
    private String nombreArchivo;
    private String periodo;
    private LocalDate periodoDetectadoIni;
    private LocalDate periodoDetectadoFin;

    private int filasLeidas;
    private int filasValidas;
    private int filasObservadas;
    private int filasError;
    private int empleadosDetectados;

    private String estado;
    private String hashArchivo;
    private Long tamanoBytes;
    private boolean duplicadoHashPrevio;

    // Auditoría de ciclo
    private String usuario;
    private LocalDateTime fechaImportacion;
    private String usuarioValidacion;
    private LocalDateTime fechaValidacion;
    private String usuarioConfirmacion;
    private LocalDateTime fechaConfirmacion;
}
