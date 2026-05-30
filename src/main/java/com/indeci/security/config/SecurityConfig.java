package com.indeci.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.indeci.security.filter.JwtAuthFilter;

@Configuration
@EnableMethodSecurity
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http

            // 🔐 SIN CSRF
            .csrf(AbstractHttpConfigurer::disable)

            // 🚫 DESACTIVAR LOGIN HTML
            .formLogin(AbstractHttpConfigurer::disable)

            // 🚫 DESACTIVAR BASIC AUTH
            .httpBasic(AbstractHttpConfigurer::disable)

            // 📦 SIN SESIONES
            .sessionManagement(sm ->
                sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // 🌍 CORS
            .cors(Customizer.withDefaults())

            // 🔐 AUTORIZACIÓN
            .authorizeHttpRequests(auth -> auth

            		.requestMatchers(
            		        "/swagger-ui/**",
            		        "/swagger-ui.html",
            		        "/v3/api-docs/**",
            		        "/actuator/**"
            		).permitAll()

                .requestMatchers(
                        "/api/auth/**",
                        "/api/telemetry/client"
                ).permitAll()
                


                // 🔓 Transparencia pública — Ley 27806 (Spec 011 / B4 — M10)
                .requestMatchers("/api/transparencia/**").permitAll()

                // 🔐 TODO LO DEMÁS PROTEGIDO


                .anyRequest().authenticated()
            )

            // 🛡️ JWT
            .addFilterBefore(
                    jwtAuthFilter,
                    UsernamePasswordAuthenticationFilter.class
            );

        return http.build();
    }
}