package com.indeci.rrhh.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Spec 013 / C1 — Tasas pensionarias vigentes para un (régimen, tipo de
 * comisión, año). Alimenta el autocomplete del formulario "Registrar pensión".
 *
 * <p>Para ONP: {@code aporte} traerá la tasa ONP (típicamente 13%);
 * {@code comision} y {@code prima} quedan en {@code null} (no aplican).
 *
 * <p>Para AFP: {@code aporte} = TASA_AFP_APORTE (10% global), {@code prima} =
 * PRIMA_AFP (1.37% global). {@code comision} sale del parámetro específico
 * por AFP+tipo (ver V010_26). Si ese parámetro no está sembrado para esa
 * combinación, {@code comision} es {@code null} y
 * {@code comisionParametrizada = false} — el frontend muestra el campo
 * editable para que el operador lo complete.
 */
@Data
public class TasasVigentesPensionDto {

    /** "ONP" o "AFP". */
    private String tipoRegimen;

    /** Tasa de aporte obligatorio (ej. 0.1300 ONP, 0.1000 AFP). */
    private BigDecimal aporte;

    /** Tasa de comisión AFP. {@code null} para ONP o cuando no está parametrizada. */
    private BigDecimal comision;

    /** Tasa de prima de seguro AFP. {@code null} para ONP. */
    private BigDecimal prima;

    /**
     * {@code true} si la comisión vino del catálogo; {@code false} si no estaba
     * parametrizada y el frontend debe pedirla a mano. Siempre {@code true}
     * para ONP (no aplica el concepto).
     */
    private boolean comisionParametrizada;
}
