package com.indeci.rrhh.service.support;

import java.util.List;

/**
 * B3 / M09 — Serializador del archivo PLAME .snl (subsidios y días no laborados).
 *
 * <p>Mandatorio para SUNAT cuando hay suspensiones en el período (faltas,
 * licencias, descansos médicos). Formato por línea (pipe, con pipe final, CRLF):
 * <pre>TIPO_DOC|NRO_DOC|COD_SUSPENSION|DIAS_AFECTOS|</pre>
 *
 * <p>FORMATO NO VALIDADO BYTE-A-BYTE: INDECI no entregó un .snl de muestra. La
 * estructura sigue la especificación funcional de B3 v3. Confirmar contra el
 * validador SUNAT antes de producción.
 *
 * <p>Serializador puro: el filtrado de qué suspensiones van al .snl lo hace el
 * service. En particular, la suspensión código 21 (Lactancia, TIPO_PLAME=ESPECIAL,
 * VA_EN_SNL=N) NO debe llegar aquí — solo afecta horas en el .jor.
 */
public final class PlameSnlWriter {

    private static final String SEP = "|";
    private static final String EOL = "\r\n";

    private PlameSnlWriter() {
    }

    /** Una suspensión declarable: días afectos por código SUNAT. */
    public record Row(
            String tipoDoc,
            String nroDoc,
            String codSuspension,
            int diasAfectos) {
    }

    public static String write(List<Row> rows) {
        StringBuilder sb = new StringBuilder();
        for (Row r : rows) {
            sb.append(r.tipoDoc()).append(SEP)
              .append(r.nroDoc()).append(SEP)
              .append(r.codSuspension()).append(SEP)
              .append(r.diasAfectos()).append(SEP)
              .append(EOL);
        }
        return sb.toString();
    }
}
