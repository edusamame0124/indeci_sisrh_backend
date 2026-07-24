package com.indeci.rrhh.service.evento.importacion;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.indeci.rrhh.vinculacion.importacion.TextoNormalizador;

/**
 * V012_42 F2 — Diccionario MOTIVO (Excel) → {@code INDECI_TIPO_EVENTO.CODIGO}, decisión RR.HH.
 * 2026-07-23:
 * <ul>
 *   <li>{@code LICENCIA SIN GOCE} → {@code LICENCIA_SIN_GOCE} (tipo operativo existente: parte
 *       del Excel son licencias vigentes/futuras, no solo histórico cerrado).</li>
 *   <li>{@code INASISTENCIA INJUSTIFICADA} → {@code FALTA_HISTORICA}.</li>
 *   <li>{@code SUSPENSION PAD} y {@code SANCION PAD} → {@code SUSPENSION_HISTORICA} (agrupados:
 *       una sanción disciplinaria de cese temporal se ejecuta en la práctica como suspensión sin
 *       goce). El texto original ("SANCION PAD") se preserva en la OBSERVACION del evento junto
 *       al N° de resolución — ver {@link EventoHistoricoRowValidator}.</li>
 * </ul>
 */
@Component
public class EventoHistoricoMotivoMapper {

    public static final String COD_LICENCIA_SIN_GOCE = "LICENCIA_SIN_GOCE";
    public static final String COD_FALTA_HISTORICA = "FALTA_HISTORICA";
    public static final String COD_SUSPENSION_HISTORICA = "SUSPENSION_HISTORICA";

    private static final Map<String, String> MAPEO = Map.of(
            "LICENCIA SIN GOCE", COD_LICENCIA_SIN_GOCE,
            "INASISTENCIA INJUSTIFICADA", COD_FALTA_HISTORICA,
            "SUSPENSION PAD", COD_SUSPENSION_HISTORICA,
            "SANCION PAD", COD_SUSPENSION_HISTORICA);

    /**
     * @param motivoExcel texto crudo de la columna MOTIVO.
     * @return el código de {@code INDECI_TIPO_EVENTO} destino, o {@code null} si el motivo no
     *         está en el diccionario (fila a rechazar, no a adivinar).
     */
    public String resolverCodigo(String motivoExcel) {
        final String clave = TextoNormalizador.clave(motivoExcel);
        return clave != null ? MAPEO.get(clave) : null;
    }
}
