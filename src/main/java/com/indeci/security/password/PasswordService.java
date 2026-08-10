package com.indeci.security.password;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.indeci.user.entity.User;

import lombok.RequiredArgsConstructor;

/**
 * Verificación/codificación de clave de {@link User}, compartida entre el flujo
 * de sesión (login, cambio forzado) y el autoservicio de Mi Perfil — antes vivía
 * duplicable como métodos privados de AuthService.
 */
@Service
@RequiredArgsConstructor
public class PasswordService {

    private final PasswordEncoder passwordEncoder;

    /** Valida contra PASSWORD y, si aplica, PASSWORD_HASH (cuentas legacy o migradas). */
    public boolean verificarClave(User user, String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            return false;
        }
        if (coincideHash(user.getPassword(), rawPassword)) {
            return true;
        }
        return coincideHash(user.getPasswordHash(), rawPassword);
    }

    private boolean coincideHash(String encoded, String rawPassword) {
        if (encoded == null || encoded.isBlank()) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, encoded);
    }

    public void aplicarClaveCodificada(User user, String rawPassword) {
        String encoded = passwordEncoder.encode(rawPassword);
        user.setPassword(encoded);
        user.setPasswordHash(encoded);
    }
}
