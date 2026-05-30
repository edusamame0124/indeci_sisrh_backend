package com.indeci.rrhh.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * B3 / M09 — Fila del historial de exportaciones PLAME/MCPP (log INDECI_EXPORT_ARCHIVO).
 */
@Data
public class ExportArchivoResponseDto {

    private Long id;
    private String periodo;
    private String tipoArchivo;
    private String nombreArchivo;
    private String hashSha256;
    private int nroLineas;
    private BigDecimal totalIngresos;
    private BigDecimal totalDescuentos;
    private Long generadoPor;
    private LocalDateTime fechaGenerado;
    private String nroTicketMcpp;
}
