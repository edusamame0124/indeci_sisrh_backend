package com.indeci.rrhh.service.asistencia;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registro en memoria de los jobs de validación asíncrona (thread-safe).
 *
 * <p><b>Escalabilidad:</b> sirve para <b>una instancia</b>. Con varias instancias detrás de un
 * balanceador, un poll podría caer en otra instancia sin el job → migrar a tabla Oracle o Redis.
 * Se deja como punto de evolución (decisión de arquitectura aprobada).
 *
 * <p>Limpieza automática: los jobs terminados (COMPLETADO/ERROR) se purgan a los 10 min
 * ({@code @Scheduled}; {@code @EnableScheduling} ya está activo en la app).
 */
@Component
public class AsistenciaImportJobRegistry {

    private final Map<String, AsistenciaImportJob> jobs = new ConcurrentHashMap<>();

    public AsistenciaImportJob crear() {
        AsistenciaImportJob job = new AsistenciaImportJob(UUID.randomUUID().toString());
        jobs.put(job.getJobId(), job);
        return job;
    }

    public AsistenciaImportJob get(String jobId) {
        return jobs.get(jobId);
    }

    @Scheduled(fixedDelay = 300_000)
    public void limpiarTerminados() {
        LocalDateTime limite = LocalDateTime.now().minusMinutes(10);
        jobs.values().removeIf(j ->
                j.getFinalizadoEn() != null && j.getFinalizadoEn().isBefore(limite));
    }
}
