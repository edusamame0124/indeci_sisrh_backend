package com.indeci.rrhh.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Habilita el procesamiento asíncrono ({@code @Async}) y define el pool dedicado para los jobs de
 * import de asistencia (Opción B). {@code @EnableScheduling} ya está en la app principal.
 */
@Configuration
@EnableAsync
public class AsyncImportConfig {

    /**
     * Pool acotado (2 base / 4 máx / cola 10) envuelto en {@link DelegatingSecurityContextAsyncTaskExecutor}
     * para <b>propagar el SecurityContext</b> del hilo de la request al hilo del job. Así la
     * autorización por rol (PLA_APPROVE) y el usuario funcionan dentro de {@code confirmarConProgreso}.
     */
    @Bean("importExecutor")
    public Executor importExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(2);
        ex.setMaxPoolSize(4);
        ex.setQueueCapacity(10);
        ex.setThreadNamePrefix("import-");
        ex.initialize();
        return new DelegatingSecurityContextAsyncTaskExecutor(ex);
    }
}
