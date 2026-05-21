package com.indeci.rrhh.service.support;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

/**
 * B3 / M14 — Serializador del archivo MCPP Web (PLL*.TXT).
 *
 * <p>Verificado byte-a-byte contra los PLL*.TXT reales de INDECI (marzo 2026).
 * CRLF en todas las líneas, sin pipe final.
 *
 * <p>Cabecera (línea 1):
 * <pre>ENTIDAD|AÑO|MES|TIPO_DOC|TIPO_PLANILLA|NRO_PLANILLA|TOTAL_REG|TOT_ING|TOT_DESC|RESERVADO</pre>
 * Ej: {@code 000009|2026|04|01|03|0038|96|114337.16|20690.95|0.00}
 *
 * <p>Detalle (líneas 2+):
 * <pre>2|DNI|00|TIPO_CONCEPTO|COD_CONCEPTO|DESCRIPCION|MONTO|REGIMEN|NRO_AIRHSP</pre>
 * Ej: {@code 2|02835030|00|1|0131|DS 279-2024-EF|2764.19|4|000511}
 *
 * <p>Montos en formato {@code %.2f} plano (incluido el cero como "0.00" — a
 * diferencia del .rem, MCPP no usa el quirk "00.00"). Los códigos zero-padded
 * (entidad, tipoDoc, tipoPlanilla, DNI, nroAirhsp) los provee el caller ya
 * formateados; el writer formatea los numéricos (año, mes, nroPlanilla).
 */
public final class McppTxtWriter {

    private static final String SEP = "|";
    private static final String EOL = "\r\n";
    /** Tipo de registro de detalle. */
    private static final String TIPO_DETALLE = "2";
    /** Constante posicional 3 observada en todos los detalles. */
    private static final String FIJO_00 = "00";

    private McppTxtWriter() {
    }

    /** Cabecera del archivo MCPP. Los totales los calcula el service. */
    public record Header(
            String entidad,
            int anio,
            int mes,
            String tipoDoc,
            String tipoPlanilla,
            int nroPlanilla,
            int totalRegistros,
            BigDecimal totalIngresos,
            BigDecimal totalDescuentos,
            BigDecimal reservado) {
    }

    /** Una línea de detalle: un concepto de un trabajador. */
    public record Detail(
            String dni,
            String tipoConcepto,
            String codConcepto,
            String descripcion,
            BigDecimal monto,
            String regimen,
            String nroAirhsp) {
    }

    public static String write(Header h, List<Detail> detalles) {
        StringBuilder sb = new StringBuilder();

        sb.append(h.entidad()).append(SEP)
          .append(String.format(Locale.ROOT, "%04d", h.anio())).append(SEP)
          .append(String.format(Locale.ROOT, "%02d", h.mes())).append(SEP)
          .append(h.tipoDoc()).append(SEP)
          .append(h.tipoPlanilla()).append(SEP)
          .append(String.format(Locale.ROOT, "%04d", h.nroPlanilla())).append(SEP)
          .append(h.totalRegistros()).append(SEP)
          .append(monto(h.totalIngresos())).append(SEP)
          .append(monto(h.totalDescuentos())).append(SEP)
          .append(monto(h.reservado()))
          .append(EOL);

        for (Detail d : detalles) {
            sb.append(TIPO_DETALLE).append(SEP)
              .append(d.dni()).append(SEP)
              .append(FIJO_00).append(SEP)
              .append(d.tipoConcepto()).append(SEP)
              .append(d.codConcepto()).append(SEP)
              .append(d.descripcion()).append(SEP)
              .append(monto(d.monto())).append(SEP)
              .append(d.regimen()).append(SEP)
              .append(d.nroAirhsp())
              .append(EOL);
        }
        return sb.toString();
    }

    /** {@code %.2f} plano, locale-independiente (el cero es "0.00"). */
    static String monto(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
