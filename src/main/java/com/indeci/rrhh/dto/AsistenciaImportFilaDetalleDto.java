package com.indeci.rrhh.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * F2 — Fila del detalle de importación para la tabla paginada server-side
 * (SpecAsistencia.md sección J: 24 columnas + metadatos de soporte).
 * Duraciones expuestas como minutos numéricos (req 21/22).
 */
@Data
public class AsistenciaImportFilaDetalleDto {

    private Long id;
    private Integer numeroFila;
    private Long empleadoId;

    private String estado;            // estadoFila: VALIDA | WARN | OBSERVADA | ERROR
    private String dni;
    private String empleadoSistema;   // nombre en el sistema
    private String nombreCsv;         // nombre tal como vino del marcador
    private LocalDate fecha;
    private String dia;               // día de la semana

    private String entradaProg;
    private String salidaProg;
    private String marca1;
    private String marca2;
    private String marca3;
    private String marca4;

    private Integer tardanzaMin;
    private Integer refrigerioMin;
    private Integer excesoRefrigMin;
    private Integer tiempoRefrigMin;
    private Integer tiempoAntesSalMin;
    private Integer horasTrabMin;
    private Integer horasExtra25Min;
    private Integer horasExtra35Min;
    private Integer horasExtra100Min;
    private Integer horasExtraTotalMin;

    private String observaciones;
    private String mensajeValidacion;
    private boolean aceptadaObservada;
}
