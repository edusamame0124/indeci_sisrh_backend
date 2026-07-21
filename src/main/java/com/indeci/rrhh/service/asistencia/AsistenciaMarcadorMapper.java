package com.indeci.rrhh.service.asistencia;

import com.indeci.rrhh.dto.AsistenciaDiaDto;

import java.util.Locale;

/**
 * Mapea filas del marcador a días tipificados de asistencia.
 */
public final class AsistenciaMarcadorMapper {

    private AsistenciaMarcadorMapper() {}

    public static AsistenciaDiaDto toDia(
            String diaSemana,
            java.time.LocalDate fecha,
            String marca1,
            String marca2,
            String marca3,
            String marca4,
            String horaEntrada,
            int minutosTardanza,
            int minutosSalidaAnticipada,
            String observacionMarcador) {

        AsistenciaDiaDto dia = new AsistenciaDiaDto();
        dia.setDia(fecha);
        dia.setDiaSemana(diaSemana);
        dia.setMarcaEntrada(marca1);
        dia.setMarcaSalida(marca2);
        dia.setMarca3(marca3);
        dia.setMarca4(marca4);
        dia.setHoraEntradaEsperada(horaEntrada);
        dia.setMinutosSalidaAnticipada(minutosSalidaAnticipada);
        dia.setHorasTrabajadasMin(null);
        dia.setOrigen("IMPORT_MARCADOR");

        String obs = observacionMarcador != null ? observacionMarcador.trim() : "";
        String tipo = resolverTipo(obs, marca1, marca2, minutosTardanza, minutosSalidaAnticipada);
        dia.setTipoDia(tipo);
        dia.setMinutosTardanza("TARDANZA".equals(tipo) ? minutosTardanza : 0);

        String detalleObs = construirObservacion(obs, minutosSalidaAnticipada);
        dia.setObservacion(detalleObs.isBlank() ? null : detalleObs);
        return dia;
    }

    private static String resolverTipo(
            String obs,
            String marca1,
            String marca2,
            int minutosTardanza,
            int minutosSalidaAnticipada) {

        if (obs.isBlank()) {
            boolean entrada = AsistenciaTiempoUtil.tieneMarca(marca1);
            boolean salida = AsistenciaTiempoUtil.tieneMarca(marca2);
            if (entrada && salida) {
                return minutosTardanza > 0 ? "TARDANZA" : "LABORAL";
            }
            // Regla SERVIR/INDECI: una sola marca (entrada XOR salida) = OMISION_MARCACION,
            // NO falta. Tiene gracia para presentar la papeleta 004; recién al cierre penaliza.
            if (entrada || salida) {
                return "OMISION_MARCACION";
            }
            return "OBSERVADO";
        }

        String normalizada = obs.toLowerCase(Locale.ROOT);
        if (normalizada.contains("falta")) {
            return "FALTA";
        }
        if (normalizada.contains("descanso")) {
            if (normalizada.contains("marc") || normalizada.contains("inc")) {
                return "OBSERVADO";
            }
            return "DESCANSO";
        }
        if (normalizada.contains("jueves santo") || normalizada.contains("viernes santo")) {
            return "FERIADO";
        }
        if (normalizada.contains("marca incompleta")) {
            // El marcador rotulo "marca incompleta" = omision de marcacion (no falta).
            return "OMISION_MARCACION";
        }
        if (minutosTardanza > 0) {
            return "TARDANZA";
        }
        if (minutosSalidaAnticipada > 0) {
            return "OBSERVADO";
        }
        return "LABORAL";
    }

    private static String construirObservacion(String obs, int minutosSalidaAnticipada) {
        if (!obs.isBlank()) {
            return obs;
        }
        if (minutosSalidaAnticipada > 0) {
            return "Salida anticipada registrada (S/A.t)";
        }
        return "";
    }
}
