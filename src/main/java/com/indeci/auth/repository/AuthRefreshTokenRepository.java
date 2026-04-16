package com.indeci.auth.repository;

import com.indeci.auth.entity.AuthRefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AuthRefreshTokenRepository extends JpaRepository<AuthRefreshToken, Long> {

    Optional<AuthRefreshToken> findByTokenAndActivo(String token, String activo);
    
    List<AuthRefreshToken> findByUsuarioAndActivo(String usuario, String activo);
    
    void deleteByFechaExpiracionBefore(LocalDateTime fecha);

  

}