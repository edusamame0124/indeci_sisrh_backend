package com.indeci.rrhh.service.support;

/**
 * Regla única de aplicabilidad de incrementos DS (negociación colectiva centralizada).
 *
 * <p>Flujo normativo acordado con RR.HH. (plan config-remunerativa-ds):</p>
 * <ul>
 *   <li>Régimen {@code 276} → nunca aplica (Nombrado ni Contratado).</li>
 *   <li>Condición {@code CAS} → activa universo D.L. 1057 → aplica.</li>
 *   <li>Régimen {@code 728} → aplica con cualquier condición.</li>
 *   <li>Regímenes elegibles: {@code 728}, {@code 1057}/{@code CAS}, {@code SERVIR},
 *       {@code 29709} (INPE), {@code 28091} (Servicio Diplomático).</li>
 * </ul>
 *
 * <p>Reutiliza {@link RegimenAplicableHelper#normalizar(String)} para alias
 * {@code CAS → 1057}.</p>
 */
public final class IncrementosDsAplicabilidadHelper {

    private IncrementosDsAplicabilidadHelper() {
    }

    /**
     * @param regimenCodigo   código del catálogo {@code INDECI_REGIMEN_LABORAL}
     * @param condicionCodigo código del catálogo {@code INDECI_CONDICION_LABORAL}
     *                        (puede ser {@code null})
     */
    public static boolean aplica(String regimenCodigo, String condicionCodigo) {
        if (regimenCodigo == null || regimenCodigo.isBlank()) {
            return false;
        }

        String regimen = RegimenAplicableHelper.normalizar(regimenCodigo);

        if ("276".equals(regimen)) {
            return false;
        }

        if (esCondicionCas(condicionCodigo)) {
            return true;
        }

        return esRegimenElegible(regimen);
    }

    private static boolean esCondicionCas(String condicionCodigo) {
        if (condicionCodigo == null || condicionCodigo.isBlank()) {
            return false;
        }
        return "CAS".equalsIgnoreCase(condicionCodigo.trim());
    }

    private static boolean esRegimenElegible(String regimenNormalizado) {
        return switch (regimenNormalizado) {
            case "728", "1057", "SERVIR", "29709", "28091" -> true;
            default -> false;
        };
    }
}
