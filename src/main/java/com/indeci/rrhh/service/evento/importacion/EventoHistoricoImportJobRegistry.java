package com.indeci.rrhh.service.evento.importacion;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * V012_42 F2 — Registro en memoria de jobs de import histórico (thread-safe). Mismo patrón y
 * misma limitación documentada que {@code AsistenciaImportJobRegistry}: sirve para una instancia;
 * con varias instancias detrás de un balanceador habría que migrar a tabla Oracle o Redis.
 * Aceptable aquí: es una carga histórica de una sola vez, no un flujo operativo recurrente.
 */
@Component
public class EventoHistoricoImportJobRegistry {

    private final Map<String, EventoHistoricoImportJob> jobs = new ConcurrentHashMap<>();

    public EventoHistoricoImportJob crear() {
        EventoHistoricoImportJob job = new EventoHistoricoImportJob(UUID.randomUUID().toString());
        jobs.put(job.getJobId(), job);
        return job;
    }

    public EventoHistoricoImportJob get(String jobId) {
        return jobs.get(jobId);
    }

    @Scheduled(fixedDelay = 300_000)
    public void limpiarTerminados() {
        LocalDateTime limite = LocalDateTime.now().minusMinutes(10);
        jobs.values().removeIf(j ->
                j.getFinalizadoEn() != null && j.getFinalizadoEn().isBefore(limite));
    }
}
