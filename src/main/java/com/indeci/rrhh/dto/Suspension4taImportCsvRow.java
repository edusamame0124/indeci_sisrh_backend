package com.indeci.rrhh.dto;

import java.time.LocalDate;

import lombok.Data;

@Data
public class Suspension4taImportCsvRow {
    private int numeroFila;
    private String tipoDoc;
    private String nroDoc;
    private String nroConstancia;
    private LocalDate fechaEmision;
    private LocalDate fechaVigIni;
    private LocalDate fechaVigFin;
    private String observacion;
    
    // Resultados de la validación
    private boolean valido = true;
    private String mensajeError;
    private Long empleadoId;
}
