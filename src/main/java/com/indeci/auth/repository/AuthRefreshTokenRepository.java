package com.indeci.auth.repository;

import com.indeci.auth.entity.AuthRefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AuthRefreshTokenRepository extends JpaRepository<AuthRefreshToken, Long> {

    Optional<AuthRefreshToken> findByToken(String token);

    Optional<AuthRefreshToken> findByTokenAndActivo(String token, String activo);

    List<AuthRefreshToken> findByUsuarioAndActivo(String usuario, String activo);

    /**
     * Elimina todos los refresh tokens cuya fecha_expiracion sea anterior a la
     * fecha proporcionada. Operación destructiva — debe ejecutarse dentro de
     * una transacción activa (defense in depth: el caller declara
     * {@code @Transactional}, pero también lo declaramos aquí por si en el
     * futuro alguien llama el método desde otro contexto sin transacción).
     */
    @Transactional
    void deleteByFechaExpiracionBefore(LocalDateTime fecha);

}
