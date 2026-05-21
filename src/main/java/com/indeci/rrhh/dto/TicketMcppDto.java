package com.indeci.rrhh.dto;

import lombok.Data;

/**
 * Spec 010 / M14 — Request para que Tesorería registre el ticket MCPP
 * de un abono bancario (lo pasa a estado PROCESADO).
 */
@Data
public class TicketMcppDto {

    private String nroTicketMcpp;
}
