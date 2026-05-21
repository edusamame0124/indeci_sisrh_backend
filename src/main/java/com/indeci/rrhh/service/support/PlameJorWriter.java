package com.indeci.rrhh.service.support;

import java.util.List;

/**
 * B3 / M09 — Serializador del archivo PLAME .jor (jornada laboral, PDT 601).
 *
 * <p>Formato por línea (campos separados por pipe, con pipe final, CRLF):
 * <pre>01|DNI|HORAS_ORD|MINUTOS_ORD|HORAS_EXTRA|MINUTOS_EXTRA|</pre>
 *
 * <p>Verificado contra los .jor reales de INDECI (marzo 2026): todas las filas
 * son {@code 01|DNI|176|0|0|0|} → 176 = horas ordinarias del mes (22 días × 8h).
 *
 * <p>CORRECCIÓN v3: el .jor registra EXCLUSIVAMENTE horas y minutos efectivos de
 * la jornada. NO inyecta códigos de tipo de día (esos van al .snl). Los tipos de
 * suspensión/licencia se declaran aparte en {@link PlameSnlWriter}.
 *
 * <p>ASUNCIÓN PENDIENTE DE VALIDAR: la semántica exacta de los 4 campos numéricos
 * (horas/min ordinarios vs sobretiempo) se infiere; todos los .jor de muestra
 * tienen sobretiempo = 0. Confirmar contra el instructivo SUNAT o un .jor con
 * sobretiempo antes de producción.
 */
public final class PlameJorWriter {

    private static final String SEP = "|";
    private static final String EOL = "\r\n";
    private static final String TIPO_REGISTRO = "01";

    private PlameJorWriter() {
    }

    /** Jornada mensual de un trabajador: horas y minutos efectivos. */
    public record Row(
            String dni,
            int horasOrdinarias,
            int minutosOrdinarios,
            int horasSobretiempo,
            int minutosSobretiempo) {
    }

    public static String write(List<Row> rows) {
        StringBuilder sb = new StringBuilder();
        for (Row r : rows) {
            sb.append(TIPO_REGISTRO).append(SEP)
              .append(r.dni()).append(SEP)
              .append(r.horasOrdinarias()).append(SEP)
              .append(r.minutosOrdinarios()).append(SEP)
              .append(r.horasSobretiempo()).append(SEP)
              .append(r.minutosSobretiempo()).append(SEP)
              .append(EOL);
        }
        return sb.toString();
    }
}
