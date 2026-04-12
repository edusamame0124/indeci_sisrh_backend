package com.indice.security.filter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.indice.security.jwt.JwtProvider;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                   HttpServletResponse response,
                                   FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);

        try {
            Claims claims = jwtProvider.obtenerClaims(token);

            String username = claims.getSubject();

            Boolean otpValidado = claims.get("otpValidado", Boolean.class);
            Boolean newPassOk = claims.get("newPassOk", Boolean.class);

            // 🔥 Validaciones de flujo
            if (Boolean.FALSE.equals(newPassOk)) {
                response.sendError(403, "Debe cambiar contraseña");
                return;
            }

            if (Boolean.FALSE.equals(otpValidado)) {
                response.sendError(403, "Debe validar OTP");
                return;
            }

            // 🔥 ROLES
            List<String> roles = claims.get("roles", List.class);

            List<SimpleGrantedAuthority> authorities = new ArrayList<>();

            if (roles != null) {
                roles.forEach(r ->
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + r))
                );
            }

            // 🔥 PERMISOS (opcional pero recomendado)
            List<String> permisos = claims.get("permisos", List.class);

            if (permisos != null) {
                permisos.forEach(p ->
                        authorities.add(new SimpleGrantedAuthority(p))
                );
            }

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                            username,
                            null,
                            authorities
                    );

            auth.setDetails(claims);

            SecurityContextHolder.getContext().setAuthentication(auth);

        } catch (Exception e) {
            // token inválido
            response.sendError(401, "Token inválido");
            return;
        }

        filterChain.doFilter(request, response);
    }
}