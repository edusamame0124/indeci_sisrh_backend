package com.indice.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Configuración de beans de seguridad del sistema SIGCO.
 *
 * Define componentes reutilizables relacionados con seguridad.
 *
 * Actualmente:
 * - PasswordEncoder basado en BCrypt.
 *
 * BCrypt:
 * - Algoritmo de hashing seguro.
 * - Incluye salt automático.
 * - Resistente a ataques de fuerza bruta.
 * - Recomendado por Spring Security.
 *
 * Este bean es utilizado en:
 * - Login (validación de contraseña).
 * - Cambio de contraseña.
 * - Generación de hashes en desarrollo.
 */
@Configuration
public class SecurityBeansConfig {

    /**
     * Bean oficial para hash de contraseñas.
     *
     * BCrypt:
     * - Genera hash diferente aunque la contraseña sea igual.
     * - Incluye salt interno.
     *
     * Se inyecta en:
     * - AuthSigcoServiceImpl
     * - AuthSigcoController (dev/hash)
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}