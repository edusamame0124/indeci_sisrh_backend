package com.indeci.auth.job;



import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.indeci.auth.repository.AuthRefreshTokenRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TokenCleanupJob {

    private final AuthRefreshTokenRepository refreshRepo;

    // 🔥 se ejecuta automáticamente
    @Scheduled(cron = "0 0 * * * *") // cada hora
    public void limpiarTokensExpirados() {

        refreshRepo.deleteByFechaExpiracionBefore(LocalDateTime.now());

        System.out.println("🧹 Tokens expirados eliminados");
    }
}