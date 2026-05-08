package com.indeci.auth.job;

import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.indeci.auth.repository.AuthRefreshTokenRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Limpieza programada de refresh tokens expirados.
 *
 * Se ejecuta automáticamente cada hora (cron expression "0 0 * * * *")
 * vía {@link Scheduled}. La eliminación física de filas requiere un
 * EntityManager con transacción activa: por eso el método está marcado
 * con {@link Transactional}, ya que los métodos {@link Scheduled}
 * NO heredan transacción del contexto de invocación de Spring.
 *
 * Auditoría: este job NO está anotado con @Auditable porque la limpieza
 * es operación interna de mantenimiento (no acción de usuario). Si se
 * requiere trazabilidad, agregar logging estructurado.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TokenCleanupJob {

    private final AuthRefreshTokenRepository refreshRepo;

    @Scheduled(cron = "0 0 * * * *") // al inicio de cada hora
    @Transactional
    public void limpiarTokensExpirados() {

        LocalDateTime corte = LocalDateTime.now();
        refreshRepo.deleteByFechaExpiracionBefore(corte);

        log.info("Refresh tokens expirados eliminados (corte={})", corte);
    }
}
