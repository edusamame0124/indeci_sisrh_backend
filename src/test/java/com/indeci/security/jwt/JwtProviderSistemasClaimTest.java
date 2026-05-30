package com.indeci.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.indeci.user.entity.User;

import io.jsonwebtoken.Claims;

/**
 * Fase 3 SSO — Tests del claim {@code sistemas} en el token definitivo.
 * REGLA-07: caso feliz + caso borde + compat (sin sistemas = comportamiento Fase 1/2).
 */
class JwtProviderSistemasClaimTest {

    private JwtProvider jwtProvider;
    private User user;

    @BeforeEach
    void setUp() {
        // Secret de >= 48 bytes para HS384 (requisito de JwtProperties).
        JwtProperties props = new JwtProperties();
        props.setSecret("secret-de-pruebas-suficientemente-largo-para-hs384-48-bytes-min");
        props.setExpiration(3_600_000L);
        jwtProvider = new JwtProvider(props);

        user = new User();
        user.setUsername("anibal");
        user.setNewClave("N");
        user.setEmpleadoId(4321L);
    }

    @Test
    void sistemasNoVacioApareceComoClaimEnElToken() {
        Map<String, List<String>> sistemas = Map.of(
                "sisrh", List.of("RRHH_JEFE"),
                "convocatoria", List.of("EVALUADOR", "CONSULTA")
        );

        String token = jwtProvider.generarTokenDefinitivo(
                user, List.of("RRHH_JEFE"), List.of("PLA_VER"), sistemas);
        Claims claims = jwtProvider.obtenerClaims(token);

        assertThat(claims.get("sistemas")).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, List<String>> claimSistemas = (Map<String, List<String>>) claims.get("sistemas");
        assertThat(claimSistemas).containsOnlyKeys("sisrh", "convocatoria");
        assertThat(claimSistemas.get("convocatoria")).containsExactly("EVALUADOR", "CONSULTA");
    }

    @Test
    void sistemasVacioOmiteElClaimParaNoInflarToken() {
        String token = jwtProvider.generarTokenDefinitivo(
                user, List.of("RRHH_JEFE"), List.of("PLA_VER"), Map.of());
        Claims claims = jwtProvider.obtenerClaims(token);

        assertThat(claims.get("sistemas")).isNull();
        // El resto de los claims Fase 1/2 siguen intactos.
        assertThat(claims.get("roles")).isInstanceOf(List.class);
        assertThat(claims.get("permisos")).isInstanceOf(List.class);
        assertThat(claims.get("otpValidado")).isEqualTo(true);
        assertThat(claims.get("empleadoId", Long.class)).isEqualTo(4321L);
    }

    @Test
    void firma3argsLegacyOmiteSistemasYConservaResto() {
        // Compatibilidad Fase 1/2: callers que aún llaman con 3 args no rompen.
        String token = jwtProvider.generarTokenDefinitivo(
                user, List.of("RRHH_JEFE"), List.of("PLA_VER"));
        Claims claims = jwtProvider.obtenerClaims(token);

        assertThat(claims.get("sistemas")).isNull();
        assertThat(claims.getSubject()).isEqualTo("anibal");
        assertThat(claims.get("roles", List.class)).containsExactly("RRHH_JEFE");
        assertThat(claims.get("newPassOk")).isEqualTo(true);
    }

    @Test
    void sistemasNullSeTratáComoVacioYOmiteClaim() {
        String token = jwtProvider.generarTokenDefinitivo(
                user, List.of("RRHH_JEFE"), List.of("PLA_VER"), null);
        Claims claims = jwtProvider.obtenerClaims(token);

        assertThat(claims.get("sistemas")).isNull();
    }
}
