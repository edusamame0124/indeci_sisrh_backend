package com.indeci.rrhh.service.support;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * B3 / M09 — Serializador del archivo PLAME .rem (remuneraciones, PDT 601).
 *
 * <p>Formato por línea (campos separados por pipe, con pipe final, CRLF):
 * <pre>01|DNI|CODIGO_PLAME|DEVENGADO|PAGADO|</pre>
 *
 * <p>Reglas de formato verificadas byte-a-byte contra los .rem reales de INDECI
 * (marzo 2026):
 * <ul>
 *   <li>Fin de línea: CRLF (\r\n).</li>
 *   <li>Pipe final tras el monto pagado.</li>
 *   <li>Montos: {@code %.2f} plano (236.42, 2000.00) EXCEPTO el cero, que se
 *       escribe {@code 00.00} (quirk del sistema fuente: nunca aparece "0.00").</li>
 * </ul>
 *
 * <p>Serializador puro: recibe filas YA consolidadas por DNI (la agregación
 * BigDecimal vive en el service, no aquí). El charset de escritura (ISO-8859-1)
 * lo aplica el controller al producir los bytes.
 */
public final class PlameRemWriter {

    private static final String SEP = "|";
    private static final String EOL = "\r\n";
    /** Tipo de registro fijo observado en todos los .rem reales. */
    private static final String TIPO_REGISTRO = "01";

    private PlameRemWriter() {
    }

    /** Una línea del .rem: un concepto PLAME para un trabajador. */
    public record Row(
            String dni,
            String codigoPlame,
            BigDecimal devengado,
            BigDecimal pagado) {
    }

    public static String write(List<Row> rows) {
        StringBuilder sb = new StringBuilder();
        for (Row r : rows) {
            sb.append(TIPO_REGISTRO).append(SEP)
              .append(r.dni()).append(SEP)
              .append(r.codigoPlame()).append(SEP)
              .append(monto(r.devengado())).append(SEP)
              .append(monto(r.pagado())).append(SEP)
              .append(EOL);
        }
        return sb.toString();
    }

    /** {@code %.2f} plano salvo el cero, que el sistema fuente escribe "00.00". */
    static String monto(BigDecimal valor) {
        BigDecimal escalado = valor.setScale(2, RoundingMode.HALF_UP);
        if (escalado.compareTo(BigDecimal.ZERO) == 0) {
            return "00.00";
        }
        return escalado.toPlainString();
    }
}
