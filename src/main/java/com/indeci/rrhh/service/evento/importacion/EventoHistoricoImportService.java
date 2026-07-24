package com.indeci.rrhh.service.evento.importacion;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.indeci.rrhh.service.evento.importacion.EventoHistoricoImportDtos.Estado;
import com.indeci.rrhh.service.evento.importacion.EventoHistoricoImportDtos.FilaResultadoDto;
import com.indeci.rrhh.service.evento.importacion.EventoHistoricoImportDtos.ImportResultDto;

import lombok.RequiredArgsConstructor;

/**
 * V012_42 F2 — Orquesta el import del Excel histórico "DEDUCCIONES DEL TIEMPO DE SERVICIOS":
 * lee la hoja "sistema" y procesa cada fila de forma aislada (una fila mala nunca bloquea a las
 * demás — mismo principio que {@code VinculacionImportService}).
 *
 * <p>Sin fase de "preview" separada: es una carga histórica de una sola vez (no un flujo
 * operativo recurrente), así que el reporte fila-por-fila se entrega al terminar el import, no
 * antes. El progreso en tiempo real lo consume {@link EventoHistoricoImportAsyncRunner} vía el
 * callback {@code onProgreso}.</p>
 *
 * <p><b>Sin {@code @Auditable}, a propósito:</b> este método corre siempre en el hilo del pool
 * {@code importExecutor} (no hay variante síncrona), y {@code AuditoriaAspect} depende del bean
 * request-scoped {@code HttpServletRequest} — fuera del hilo de la petición HTTP original lanza
 * {@code IllegalStateException: No thread-bound request found}. Mismo motivo documentado en
 * {@code AsistenciaImportService} ("la auditoría se hace solo en la vía SÍNCRONA; en el hilo
 * async el aspecto no corre"). El resumen se deja en el log de aplicación en su lugar.</p>
 */
@Service
@RequiredArgsConstructor
public class EventoHistoricoImportService {

    private static final Logger log = LoggerFactory.getLogger(EventoHistoricoImportService.class);

    private final EventoHistoricoSheetReader reader;
    private final EventoHistoricoRowProcessor processor;

    public ImportResultDto importar(byte[] contenidoXlsx, BiConsumer<Integer, String> onProgreso) {
        final List<EventoHistoricoRowRaw> filas = reader.leer(contenidoXlsx);
        final List<FilaResultadoDto> resultados = new ArrayList<>(filas.size());

        int insertados = 0;
        int duplicados = 0;
        int rechazados = 0;

        for (int i = 0; i < filas.size(); i++) {
            final EventoHistoricoRowRaw fila = filas.get(i);
            final FilaResultadoDto resultado = procesarConAislamiento(fila);
            resultados.add(resultado);

            switch (resultado.estado()) {
                case Estado.OK -> insertados++;
                case Estado.DUPLICADO_OMITIDO -> duplicados++;
                default -> rechazados++;
            }

            if (onProgreso != null) {
                final int pct = (int) Math.round((i + 1) * 100.0 / filas.size());
                onProgreso.accept(pct, "Fila " + (i + 1) + " de " + filas.size());
            }
        }

        log.info(
                "[IMPORT-HISTORICO] {} filas; {} insertadas; {} duplicadas omitidas; {} rechazadas.",
                filas.size(), insertados, duplicados, rechazados);

        return new ImportResultDto(filas.size(), insertados, duplicados, rechazados, resultados);
    }

    /**
     * Red de seguridad adicional: si {@link EventoHistoricoRowProcessor#procesar} lanza una
     * excepción NO capturada internamente (p. ej. una violación de constraint que solo aparece
     * al hacer commit de la transacción REQUIRES_NEW, después de que el método ya devolvió su
     * resultado — caso conocido de Spring), esta fila se reporta como error y el import continúa
     * con las siguientes. Mismo patrón que {@code VinculacionImportService.importar}.
     */
    private FilaResultadoDto procesarConAislamiento(EventoHistoricoRowRaw fila) {
        try {
            return processor.procesar(fila);
        } catch (RuntimeException e) {
            final Throwable causa = org.springframework.core.NestedExceptionUtils.getMostSpecificCause(e);
            final String msg = causa.getMessage() != null
                    ? causa.getMessage().split("\n")[0]
                    : e.getClass().getSimpleName();
            return new FilaResultadoDto(
                    fila.getNumeroFila(),
                    fila.digitos(EventoHistoricoColumna.DNI),
                    fila.texto(EventoHistoricoColumna.SERVIDOR),
                    fila.texto(EventoHistoricoColumna.MOTIVO),
                    Estado.ERROR,
                    "No se pudo guardar: " + msg);
        }
    }
}
