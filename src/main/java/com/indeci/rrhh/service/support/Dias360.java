package com.indeci.rrhh.service.support;

import java.time.LocalDate;

/**
 * Cómputo de días en base comercial <b>30/360 método US/NASD</b>, equivalente a
 * {@code DAYS360(inicio, fin)} de Microsoft Excel con el método por defecto
 * (Method = FALSE). Base regulatoria del devengo de beneficios sociales en el
 * sector público peruano (MEF/PLAME) — SPEC_VACACIONES F1 (D2).
 *
 * <p>Reglas del método US/NASD implementadas:
 * <ul>
 *   <li>Si el día de inicio es 31 → se ajusta a 30.</li>
 *   <li>Si el día de fin es 31 <b>y</b> el día de inicio (ya ajustado) es 30 → fin se ajusta a 30.</li>
 * </ul>
 *
 * <p><b>Decisión D-F1-2 (regla de fin de febrero):</b> el método US/NASD de Excel
 * NO aplica la regla "último día de febrero → 30". Se verificó contra el padrón real
 * del especialista (0 de 660 fechas de ingreso caen en 28/29-feb), por lo que la regla
 * es inerte para todos los empleados actuales. Se omite deliberadamente para preservar
 * la regresión 1:1 con Excel (criterio de éxito de F1). Si en el futuro se exige la
 * variante con regla de febrero, se añade aquí en un único punto.
 *
 * <p>Esta clase NO aplica el "+1" inclusivo del cómputo de servicio: devuelve el
 * DAYS360 puro (idéntico a Excel). El conteo inclusivo de extremos vive en
 * {@code TiempoServicioService} (ver SPEC F1 §4).
 */
public final class Dias360 {

    private Dias360() {
    }

    /**
     * Días 30/360 (US/NASD) entre dos fechas. Si {@code fin} es anterior a
     * {@code ini}, devuelve el valor negativo simétrico.
     */
    public static int entre(LocalDate ini, LocalDate fin) {
        if (ini == null || fin == null) {
            throw new IllegalArgumentException("Dias360.entre requiere ambas fechas no nulas");
        }
        if (fin.isBefore(ini)) {
            return -entre(fin, ini);
        }
        int d1 = ini.getDayOfMonth();
        int d2 = fin.getDayOfMonth();
        final int m1 = ini.getMonthValue();
        final int m2 = fin.getMonthValue();
        final int y1 = ini.getYear();
        final int y2 = fin.getYear();

        if (d1 == 31) {
            d1 = 30;
        }
        if (d2 == 31 && d1 == 30) {
            d2 = 30;
        }
        return (y2 - y1) * 360 + (m2 - m1) * 30 + (d2 - d1);
    }
}
