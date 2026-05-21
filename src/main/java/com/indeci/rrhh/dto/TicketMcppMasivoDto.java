package com.indeci.rrhh.dto;

import lombok.Data;

import java.util.List;

/**
 * Spec 010 / M14 — Request para registrar un mismo ticket MCPP en varios
 * abonos a la vez (SPEC §12.2 PANTALLA-07 — ingreso masivo).
 */
@Data
public class TicketMcppMasivoDto {

    /** Abonos a los que se aplica el ticket. */
    private List<Long> abonoIds;

    private String nroTicketMcpp;
}
