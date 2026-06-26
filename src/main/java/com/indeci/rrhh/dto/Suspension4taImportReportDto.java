package com.indeci.rrhh.dto;

import java.util.List;

import lombok.Data;

@Data
public class Suspension4taImportReportDto {
    private int totalFilas;
    private int totalProcesados;
    private int totalErrores;
    private List<Suspension4taImportCsvRow> filasError;
}
