package com.indice.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.indice.security.filter.JwtAuthFilter;

/**
 * Configuración principal de seguridad del sistema SIGCO.
 *
 * Define:
 * - Política stateless (JWT).
 * - Endpoints públicos.
 * - Endpoints protegidos.
 * - Integración del filtro JwtAuthFilter.
 *
 * Este sistema NO usa sesiones HTTP.
 * Toda autenticación se basa en JWT.
 */
@Configuration
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    /**
     * Define la cadena de filtros de seguridad.
     *
     * Reglas generales:
     * - CSRF deshabilitado (API REST stateless).
     * - Sesiones deshabilitadas.
     * - Swagger público.
     * - Login público.
     * - Resto de endpoints protegidos.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
            /* =====================================================
             * 🔐 CSRF
             * =====================================================
             * Deshabilitado porque:
             * - No usamos sesiones.
             * - No usamos cookies.
             * - API basada en JWT.
             */
            .csrf(AbstractHttpConfigurer::disable)

            /* =====================================================
             * 📦 SESSION MANAGEMENT
             * =====================================================
             * Stateless:
             * - No se crea sesión HTTP.
             * - Toda autenticación via JWT.
             */
            .sessionManagement(sm ->
                sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            /* =====================================================
             * 🌍 CORS
             * =====================================================
             * Usa configuración definida en CorsConfig.
             */
            .cors(Customizer.withDefaults())

            /* =====================================================
             * 🔐 AUTORIZACIÓN DE ENDPOINTS
             * ===================================================== */
            .authorizeHttpRequests(auth -> auth

                /* =============================
                 * 🔓 SWAGGER (DOCUMENTACIÓN)
                 * =============================
                 * Permite acceso a:
                 * - UI Swagger
                 * - JSON de especificación OpenAPI
                 */
                .requestMatchers(
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/swagger-ui.html"
                ).permitAll()

                /* =============================
                 * 🔓 AUTH
                 * =============================
                 * Login, refresh, OTP, change-password
                 */
                .requestMatchers("/api/auth/**").permitAll()

                /* =============================
                 * 🔓 REGISTRO PROVEEDOR
                 * =============================
                 */
                .requestMatchers(
                        "/api/proveedor/registrar",
                        "/api/proveedor/activar"
                ).permitAll()

                /* =============================
                 * 🔐 RESTO PROTEGIDO
                 * =============================
                 */
                .anyRequest().authenticated()
            )

            /* =====================================================
             * 🛡️ JWT FILTER
             * =====================================================
             * Se ejecuta antes del filtro estándar de autenticación.
             * - Valida JWT.
             * - Aplica reglas de cambio obligatorio.
             * - Aplica reglas OTP.
             */
            .addFilterBefore(
                    jwtAuthFilter,
                    UsernamePasswordAuthenticationFilter.class
            );

        return http.build();
    }
}