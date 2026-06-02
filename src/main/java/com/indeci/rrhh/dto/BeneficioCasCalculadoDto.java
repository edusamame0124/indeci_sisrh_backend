package com.indeci.rrhh.dto;

import java.math.BigDecimal;

/**
 * F2bis — Resultado del cálculo de Beneficios CAS 2026 (aguinaldos Fiestas
 * Patrias / Navidad + bonificación extraordinaria asociada).
 *
 * <p>Campos:</p>
 * <ul>
 *   <li>{@code aplica} — false si:
 *     <ul>
 *       <li>El feature flag {@code feature.beneficios-cas-2026.enabled} está OFF.</li>
 *       <li>El régimen del empleado NO es CAS.</li>
 *       <li>El período NO es julio (07) ni diciembre (12).</li>
 *     </ul>
 *     Cuando false, todos los montos van en 0.
 *   </li>
 *   <li>{@code tipo} — {@code "AGUINALDO_JULIO"}, {@code "AGUINALDO_DICIEMBRE"} o
 *       {@code "NO_APLICA"}.</li>
 *   <li>{@code montoAguinaldo} — valor del parámetro {@code AGUINALDO_CAS_*}
 *       vigente al período. Si el parámetro no está sembrado en BD → 0
 *       (defensivo, evita romper cálculo si el MEF aún no publica el DS).</li>
 *   <li>{@code montoBonifExtraord} — {@code aguinaldo × BONIF_EXTRAORD_PCT}.
 *       0 si el parámetro porcentual no está cargado.</li>
 *   <li>{@code total} — {@code montoAguinaldo + montoBonifExtraord}.</li>
 * </ul>
 *
 * <p>F2bis NO graba detalles MEF — espera CODIGO_MEF oficiales de RRHH
 * para "Aguinaldo CAS" y "Bonificación Extraordinaria" (LEY-01).</p>
 */
public record BeneficioCasCalculadoDto(
        boolean aplica,
        String tipo,
        BigDecimal montoAguinaldo,
        BigDecimal montoBonifExtraord,
        BigDecimal total) {

    public static BeneficioCasCalculadoDto noAplica() {
        return new BeneficioCasCalculadoDto(
                false, "NO_APLICA",
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
