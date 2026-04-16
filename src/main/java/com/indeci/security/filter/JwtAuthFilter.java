package com.indeci.security.filter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.indeci.security.jwt.JwtProvider;

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

        // 🔹 Si no hay token → sigue normal
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

            String path = request.getRequestURI();

            /* =====================================================
             * 🔥 ENDPOINTS AUTH (NO BLOQUEAR)
             * ===================================================== */
            boolean esAuthEndpoint = path.startsWith("/api/auth/");

            /* =====================================================
             * 🔐 VALIDACIONES DE FLUJO
             * ===================================================== */

            // 🔴 FORZAR CAMBIO DE CLAVE
            if (Boolean.FALSE.equals(newPassOk) && !esAuthEndpoint) {
                sendError(response, 403, "Debe cambiar contraseña");
                return;
            }

            // 🔴 VALIDAR OTP
            if (Boolean.FALSE.equals(otpValidado) && !esAuthEndpoint) {
                sendError(response, 403, "Debe validar OTP");
                return;
            }

            /* =====================================================
             * 🔑 ROLES
             * ===================================================== */
            List<String> roles = claims.get("roles", List.class);

            List<SimpleGrantedAuthority> authorities = new ArrayList<>();

            if (roles != null) {
                roles.forEach(r ->
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + r))
                );
            }

            /* =====================================================
             * 🔑 PERMISOS
             * ===================================================== */
            List<String> permisos = claims.get("permisos", List.class);

            if (permisos != null) {
                permisos.forEach(p ->
                        authorities.add(new SimpleGrantedAuthority(p))
                );
            }

            /* =====================================================
             * 🔐 AUTENTICACIÓN EN CONTEXTO
             * ===================================================== */
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                            username,
                            null,
                            authorities
                    );

            auth.setDetails(claims);

            SecurityContextHolder.getContext().setAuthentication(auth);

            System.out.println("AUTH SETEADO: " + username);
            System.out.println("PATH: " + path);
            System.out.println("OTP_VALIDADO: " + otpValidado);

        } catch (Exception e) {
            sendError(response, 401, "Token inválido");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 🔥 Respuesta JSON uniforme
     */
    private void sendError(HttpServletResponse response,
                           int status,
                           String mensaje) throws IOException {

        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> error = new HashMap<>();
        error.put("status", status);
        error.put("mensaje", mensaje);

        new ObjectMapper().writeValue(response.getOutputStream(), error);
    }
}