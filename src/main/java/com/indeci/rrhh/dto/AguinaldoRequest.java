package com.indeci.rrhh.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Data;

/**
 * Track B — Solicitud de generación del AGUINALDO (proceso aparte).
 *
 * @see com.indeci.rrhh.service.AguinaldoService
 */
@Data
public class AguinaldoRequest {

    /** Período de pago del aguinaldo ("YYYYMM" o "YYYY-MM"); solo julio (07) o diciembre (12). */
    private String periodo;

    /** % manual de RR.HH. para CAS (0..100). Input del proceso; no se persiste. */
    private BigDecimal pctCas;

    /** Fecha de corte de elegibilidad (ej. 30/06). */
    private LocalDate fechaCorte;

    /** Filtro opcional por régimen laboral (null = todos los regímenes con aguinaldo). */
    private Long regimenLaboralId;
}
