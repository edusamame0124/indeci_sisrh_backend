package com.indeci.rrhh.dto;

import lombok.Data;

/**
 * Spec 011 / B4 — Período publicado en transparencia (Ley 27806).
 * Solo aparecen aquí los períodos APROBADO o CERRADO.
 */
@Data
public class TransparenciaPeriodoDto {

    private String periodo;

    private String estado;
}
