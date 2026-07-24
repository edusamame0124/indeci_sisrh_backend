package com.indeci.rrhh.service.evento.importacion;

import org.springframework.core.NestedExceptionUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.indeci.rrhh.service.evento.importacion.EventoHistoricoImportDtos.ImportResultDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * V012_42 F2 — Ejecuta el import histórico en el pool compartido {@code importExecutor} (el mismo
 * que ya usa Asistencia — {@code AsyncImportConfig}, sin crear un pool nuevo). Bean separado del
 * servicio para que el proxy de {@code @Async} aplique.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class EventoHistoricoImportAsyncRunner {

    private final EventoHistoricoImportService importService;

    @Async("importExecutor")
    public void ejecutar(EventoHistoricoImportJob job, byte[] contenidoXlsx) {
        try {
            ImportResultDto resultado = importService.importar(
                    contenidoXlsx, (pct, fase) -> job.avanzar(pct, fase));
            job.completar(resultado);
        } catch (Exception e) {
            Throwable causa = NestedExceptionUtils.getMostSpecificCause(e);
            String msg = causa.getMessage() != null
                    ? causa.getMessage().split("\n")[0]
                    : e.getClass().getSimpleName();
            log.error("[IMPORT-HISTORICO] Job {} falló: {}", job.getJobId(), msg, e);
            job.fallar(msg);
        }
    }
}
