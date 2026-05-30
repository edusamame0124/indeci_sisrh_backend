package com.indeci.security.jwt;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.indeci.user.entity.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JwtProvider {

    private final JwtProperties jwtProperties;

    private Key getSigningKey() {
        byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);

        if (keyBytes.length < 48) {
            throw new IllegalStateException("jwt.secret demasiado corto para HS384");
        }

        return Keys.hmacShaKeyFor(keyBytes);
    }

    /* =========================================================
     * TOKEN TEMPORAL
     * ========================================================= */
    public String generarTokenTemporal(User usuario) {

        Date ahora = new Date();
        Date expiracion = new Date(ahora.getTime() + (10 * 60 * 1000));

        return Jwts.builder()
                .setSubject(usuario.getUsername())
                .claim("otpValidado", false)
                .claim("newPassOk", "N".equalsIgnoreCase(usuario.getNewClave()))
                .setIssuedAt(ahora)
                .setExpiration(expiracion)
                .signWith(getSigningKey(), SignatureAlgorithm.HS384)
                .compact();
    }

    /* =========================================================
     * TOKEN DEFINITIVO
     * ========================================================= */

    /**
     * Compatibilidad Fase 1/2: emite el token sin el claim {@code sistemas}.
     * Sigue funcionando para callers que aún no construyen el mapa multi-sistema.
     */
    public String generarTokenDefinitivo(User usuario,
                                         List<String> roles,
                                         List<String> permisos) {
        return generarTokenDefinitivo(usuario, roles, permisos, Map.of());
    }

    /**
     * Fase 3 SSO: emite el token definitivo con el claim {@code sistemas} para
     * habilitar el Portal Selector y la autorización en SISCONV/GDR.
     *
     * El claim {@code roles} se conserva plano (lista de roles macro del SISRH)
     * para no romper {@link com.indeci.security.filter.JwtAuthFilter} ni los
     * guards de Fase 2. El claim {@code sistemas} es un mapa independiente:
     * <pre>
     *   "sistemas": {
     *     "sisrh":        ["RRHH_JEFE"],
     *     "convocatoria": ["EVALUADOR", "CONSULTA"],
     *     "rendimiento":  ["JEFE_AREA"]
     *   }
     * </pre>
     * Si {@code sistemas} viene vacío el claim se omite del JWT para no inflar
     * tokens de usuarios solo-SISRH legacy.
     */
    public String generarTokenDefinitivo(User usuario,
                                         List<String> roles,
                                         List<String> permisos,
                                         Map<String, List<String>> sistemas) {

        Date ahora = new Date();
        Date expiracion = new Date(ahora.getTime() + jwtProperties.getExpiration());

        io.jsonwebtoken.JwtBuilder builder = Jwts.builder()
                .setSubject(usuario.getUsername())
                .claim("roles", roles)
                .claim("permisos", permisos)
                .claim("otpValidado", true)
                .claim("newPassOk", "N".equalsIgnoreCase(usuario.getNewClave()))
                // Spec 011 / B2 — empleado vinculado a la cuenta (null si no tiene).
                .claim("empleadoId", usuario.getEmpleadoId());

        if (sistemas != null && !sistemas.isEmpty()) {
            builder = builder.claim("sistemas", sistemas);
        }

        return builder
                .setIssuedAt(ahora)
                .setExpiration(expiracion)
                .signWith(getSigningKey(), SignatureAlgorithm.HS384)
                .compact();
    }

    /* =========================================================
     * VALIDACIÓN
     * ========================================================= */
    public Claims obtenerClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public String getSubject(String token) {
        return obtenerClaims(token).getSubject();
    }
    
    /* =========================================================
     * TOKEN CAMBIO DE CLAVE
     * ========================================================= */
    public String generarTokenCambioClave(User usuario) {

        Date ahora = new Date();
        Date expiracion = new Date(ahora.getTime() + (15 * 60 * 1000)); // corto

        return Jwts.builder()
                .setSubject(usuario.getUsername())
                .claim("roles", List.of()) // sin permisos
                .claim("permisos", List.of())
                .claim("otpValidado", true) // no usamos OTP aún
                .claim("newPassOk", false) // 🔥 CLAVE
                .setIssuedAt(ahora)
                .setExpiration(expiracion)
                .signWith(getSigningKey(), SignatureAlgorithm.HS384)
                .compact();
    }
    
    public String generarRefreshToken(User user) {

        Date ahora = new Date();
        Date expiracion = new Date(ahora.getTime() + (24 * 60 * 60 * 1000)); // 24h

        return Jwts.builder()
                .setSubject(user.getUsername())
                .claim("type", "refresh")
                .setIssuedAt(ahora)
                .setExpiration(expiracion)
                .signWith(getSigningKey(), SignatureAlgorithm.HS384)
                .compact();
    }
}