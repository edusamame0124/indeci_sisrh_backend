package com.indeci.rrhh.service.evento.importacion;

import java.util.List;

/**
 * V012_42 F2 — DTOs de resultado del importador histórico de eventos. Un solo archivo, como
 * {@code VinculacionImportDtos}, porque son formas pequeñas y estrictamente acopladas entre sí.
 */
public class EventoHistoricoImportDtos {

    private EventoHistoricoImportDtos() {
    }

    /** Estados posibles de una fila procesada. */
    public static final class Estado {
        public static final String OK = "OK";
        public static final String DUPLICADO_OMITIDO = "DUPLICADO_OMITIDO";
        public static final String ERROR = "ERROR";

        private Estado() {
        }
    }

    public record FilaResultadoDto(
            int numeroFila,
            String dni,
            String nombre,
            String motivoExcel,
            String estado,
            String mensaje) {
    }

    public record PreviewDto(
            int total,
            int validas,
            int duplicadas,
            int errores,
            List<FilaResultadoDto> filas) {
    }

    public record ImportResultDto(
            int total,
            int insertados,
            int duplicadosOmitidos,
            int rechazados,
            List<FilaResultadoDto> filas) {
    }
}
