package com.indeci.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

/**
 * Clase de configuración para propiedades JWT del sistema SIGCO.
 *
 * Se vincula automáticamente con propiedades definidas en:
 *
 * application.yml o application.properties
 *
 * Prefijo esperado:
 *
 * jwt.secret=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
 * jwt.expiration=3600000
 *
 * Responsabilidad:
 * - Proveer la clave secreta usada para firmar tokens.
 * - Definir el tiempo de expiración del access token.
 *
 * Seguridad:
 * - El secret debe tener longitud suficiente según algoritmo (HS384 mínimo 48 bytes).
 * - En producción, el secret debe provenir de variable de entorno.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /**
     * Clave secreta usada para firmar el JWT.
     *
     * ⚠ Debe tener longitud suficiente:
     * - HS384 requiere mínimo 48 bytes.
     *
     * Recomendación:
     * No hardcodear en repositorio.
     * Usar variable de entorno.
     */
    private String secret;

    /**
     * Tiempo de expiración del access token en milisegundos.
     *
     * Ejemplo:
     * 3600000 = 1 hora
     */
    private long expiration;
}