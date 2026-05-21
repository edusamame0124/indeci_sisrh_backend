package com.indeci.security.jwt;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.List;

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
    public String generarTokenDefinitivo(User usuario,
                                         List<String> roles,
                                         List<String> permisos) {

        Date ahora = new Date();
        Date expiracion = new Date(ahora.getTime() + jwtProperties.getExpiration());

        return Jwts.builder()
                .setSubject(usuario.getUsername())
                .claim("roles", roles)
                .claim("permisos", permisos)
                .claim("otpValidado", true)
                .claim("newPassOk", "N".equalsIgnoreCase(usuario.getNewClave()))
                // Spec 011 / B2 — empleado vinculado a la cuenta (null si no tiene).
                .claim("empleadoId", usuario.getEmpleadoId())
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