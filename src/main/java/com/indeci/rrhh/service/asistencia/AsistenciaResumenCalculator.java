package com.indeci.rrhh.service.asistencia;

import com.indeci.rrhh.dto.AsistenciaDiaDto;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;

/**
 * Agregados y descuentos de asistencia — fuente de verdad backend (REGLA 276-02).
 */
public final class AsistenciaResumenCalculator {

    // TELETRABAJO = trabajo efectivo remoto (Ley 31572): cuenta como laborado, no descuenta.
    // PERMISO (con goce aprobado) NO está aquí: justificado, no descuenta y no cuenta laborado.
    private static final Set<String> TIPOS_LABORADOS = Set.of("LABORAL", "TARDANZA", "TELETRABAJO");

    private AsistenciaResumenCalculator() {}

    public static Resumen calcular(List<AsistenciaDiaDto> dias, double remuneracionBase) {
        int diasLaborados = 0;
        int diasFalta = 0;
        int totalMinTardanza = 0;
        int minutosSalidaAnticipada = 0;
        int marcasIncompletas = 0;

        for (AsistenciaDiaDto d : dias) {
            String tipo = d.getTipoDia();
            if (tipo != null && TIPOS_LABORADOS.contains(tipo)) {
                diasLaborados++;
            }
            if ("FALTA".equals(tipo) || "SANCION_PAD".equals(tipo) || esObservadoNoAutorizado(d)) {
                diasFalta++;
            }
            if ("TARDANZA".equals(tipo) && d.getMinutosTardanza() != null) {
                totalMinTardanza += Math.max(0, d.getMinutosTardanza());
            }
            if (d.getMinutosSalidaAnticipada() != null) {
                minutosSalidaAnticipada += Math.max(0, d.getMinutosSalidaAnticipada());
            }
            if ("OBSERVADO".equals(tipo) && contieneMarcaIncompleta(d.getObservacion())) {
                marcasIncompletas++;
            }
        }

        double descTardanza = calcularDescuentoTardanza(remuneracionBase, totalMinTardanza);
        double descFalta = calcularDescuentoFalta(remuneracionBase, diasFalta);

        Resumen resumen = new Resumen();
        resumen.setDiasLaborados(diasLaborados);
        resumen.setDiasFalta(diasFalta);
        resumen.setTotalMinTardanza(totalMinTardanza);
        resumen.setMinutosSalidaAnticipada(minutosSalidaAnticipada);
        resumen.setMarcasIncompletas(marcasIncompletas);
        resumen.setDescuentoTardanza(descTardanza);
        resumen.setDescuentoFalta(descFalta);
        resumen.setDescuentoTotal(round2(descTardanza + descFalta));
        return resumen;
    }

    public static double calcularDescuentoTardanza(double remuneracion, int minutos) {
        if (remuneracion <= 0 || minutos <= 0) {
            return 0.0;
        }
        return BigDecimal.valueOf(remuneracion)
                .multiply(BigDecimal.valueOf(minutos))
                .divide(BigDecimal.valueOf(30L * 8 * 60), 2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    public static double calcularDescuentoFalta(double remuneracion, int diasFalta) {
        if (remuneracion <= 0 || diasFalta <= 0) {
            return 0.0;
        }
        return BigDecimal.valueOf(remuneracion)
                .multiply(BigDecimal.valueOf(diasFalta))
                .divide(BigDecimal.valueOf(30), 2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    /** Observado por papeleta no autorizada: descuenta como falta (base/30). */
    private static boolean esObservadoNoAutorizado(AsistenciaDiaDto d) {
        return "OBSERVADO".equals(d.getTipoDia())
                && d.getPapeletaAutorizada() != null
                && d.getPapeletaAutorizada() == 0;
    }

    private static boolean contieneMarcaIncompleta(String observacion) {
        if (observacion == null) {
            return false;
        }
        return observacion.toLowerCase().contains("marca incompleta");
    }

    private static double round2(double valor) {
        return BigDecimal.valueOf(valor).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    @Data
    public static class Resumen {
        private int diasLaborados;
        private int diasFalta;
        private int totalMinTardanza;
        private int minutosSalidaAnticipada;
        private int marcasIncompletas;
        private double descuentoTardanza;
        private double descuentoFalta;
        private double descuentoTotal;
    }
}
