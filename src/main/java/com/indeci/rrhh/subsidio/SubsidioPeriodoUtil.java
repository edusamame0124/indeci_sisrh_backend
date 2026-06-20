package com.indeci.rrhh.subsidio;

import java.time.LocalDate;
import java.time.YearMonth;

/** Conversión entre periodo planilla (YYYY-MM) y periodo subsidio (YYYYMM). */
public final class SubsidioPeriodoUtil {

    private SubsidioPeriodoUtil() {}

    public static String aPlanilla(String periodoSubsidio) {
        if (periodoSubsidio == null || periodoSubsidio.isBlank()) {
            return null;
        }
        String p = periodoSubsidio.replace("-", "").trim();
        if (p.length() != 6) {
            return periodoSubsidio;
        }
        return p.substring(0, 4) + "-" + p.substring(4, 6);
    }

    public static String aSubsidio(String periodoPlanilla) {
        if (periodoPlanilla == null || periodoPlanilla.isBlank()) {
            return null;
        }
        return periodoPlanilla.replace("-", "").trim();
    }

    public static String deFecha(LocalDate fecha) {
        YearMonth ym = YearMonth.from(fecha);
        return String.format("%04d%02d", ym.getYear(), ym.getMonthValue());
    }

    public static YearMonth parseSubsidio(String periodo) {
        if (periodo == null) {
            return null;
        }
        String p = periodo.replace("-", "").trim();
        if (p.length() != 6) {
            return null;
        }
        try {
            return YearMonth.of(
                    Integer.parseInt(p.substring(0, 4)),
                    Integer.parseInt(p.substring(4, 6)));
        } catch (NumberFormatException | java.time.DateTimeException ex) {
            return null;
        }
    }
}
