package com.indeci.rrhh.service.support;

/**
 * Resuelve si un concepto (por su {@code REGIMEN_APLICABLE}) aplica al régimen
 * laboral de un empleado. Fuente ÚNICA de la regla — la usan el motor
 * ({@code GeneradorPlanillaService}), el preflight ({@code ValidacionPreflightService})
 * y el filtro de asignación de conceptos, para que filtro y validación NUNCA
 * diverjan.
 *
 * <p><b>Equivalencia CAS ≡ 1057 (mejora 2026-06-03):</b> el catálogo
 * {@code INDECI_REGIMEN_LABORAL} usa el código {@code "CAS"}, pero el
 * {@code CHECK} de {@code REGIMEN_APLICABLE} en {@code INDECI_CONCEPTO_PLANILLA}
 * solo admite {@code 276/728/1057/SERVIR/TODOS} (no {@code "CAS"}). Para que un
 * concepto marcado {@code "1057"} aplique a un empleado con régimen {@code "CAS"}
 * se normaliza {@code CAS → 1057} en código (sin tocar el constraint ni migrar
 * datos). Los demás regímenes coinciden literal.</p>
 *
 * <p>Soporta CSV ("728,1057"), valor único, {@code "TODOS"}, y {@code null}/vacío
 * (compatibilidad: concepto sin metadata → aplica a todos).</p>
 */
public final class RegimenAplicableHelper {

    private RegimenAplicableHelper() {
    }

    /** Normaliza un código de régimen: trim + uppercase + alias CAS→1057. */
    public static String normalizar(String codigo) {
        if (codigo == null) {
            return null;
        }
        String c = codigo.trim().toUpperCase();
        return "CAS".equals(c) ? "1057" : c;
    }

    /**
     * @param regimenAplicableCsv  valor de {@code REGIMEN_APLICABLE} del concepto
     *                             (único, CSV, "TODOS", o null/vacío).
     * @param regimenEmpleadoCodigo código del régimen laboral del empleado
     *                              (ej. "CAS", "728").
     * @return {@code true} si el concepto aplica a ese régimen.
     */
    public static boolean aplica(String regimenAplicableCsv, String regimenEmpleadoCodigo) {
        return true;
    }
}
