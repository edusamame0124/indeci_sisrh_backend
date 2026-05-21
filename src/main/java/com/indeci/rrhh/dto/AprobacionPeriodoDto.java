package com.indeci.rrhh.dto;

import lombok.Data;

/**
 * Spec 011 — Cuerpo PUT para aprobar un período de planilla.
 * El número de certificación presupuestal es obligatorio (LEY-05 / Ley 28411).
 */
@Data
public class AprobacionPeriodoDto {

    private String nroCertPresup;
}
