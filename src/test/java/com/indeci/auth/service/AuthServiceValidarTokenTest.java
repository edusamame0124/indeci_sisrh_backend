package com.indeci.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.indeci.auth.dto.ValidateTokenResponse;
import com.indeci.auth.repository.AuthRefreshTokenRepository;
import com.indeci.exception.SeguridadException;
import com.indeci.security.captcha.TurnstileService;
import com.indeci.security.jwt.JwtProperties;
import com.indeci.security.jwt.JwtProvider;
import com.indeci.security.otp.OtpService;
import com.indeci.security.ratelimit.LoginRateLimiter;
import com.indeci.sistema.service.UsuarioSistemaService;
import com.indeci.user.entity.User;
import com.indeci.user.repository.PermisoRepository;
import com.indeci.user.repository.RolPermisoRepository;
import com.indeci.user.repository.RolRepository;
import com.indeci.user.repository.UserRepository;
import com.indeci.user.repository.UsuarioPermisoDenyRepository;
import com.indeci.user.repository.UsuarioPermisoRepository;
import com.indeci.user.repository.UsuarioRolRepository;

import io.jsonwebtoken.security.SignatureException;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Fase 3 SSO — Tests de {@code validarToken}. Solo necesita JwtProvider real
 * + secret consistente; el resto de dependencias son mocks no usados.
 * REGLA-07: feliz + 3 errores normativos + 1 caso borde (token legacy sin sistemas).
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceValidarTokenTest {

    @Mock private UserRepository userRepository;
    @Mock private UsuarioRolRepository usuarioRolRepository;
    @Mock private RolRepository rolRepository;
    @Mock private RolPermisoRepository rolPermisoRepository;
    @Mock private PermisoRepository permisoRepository;
    @Mock private UsuarioPermisoDenyRepository usuarioPermisoDenyRepository;
    @Mock private UsuarioPermisoRepository usuarioPermisoRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private TurnstileService turnstileService;
    @Mock private LoginRateLimiter loginRateLimiter;
    @Mock private OtpService otpService;
    @Mock private AuthRefreshTokenRepository authRefreshTokenRepository;
    @Mock private UsuarioSistemaService usuarioSistemaService;

    private JwtProvider jwtProvider;
    private AuthService service;
    private User userOk;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret("secret-de-pruebas-suficientemente-largo-para-hs384-48-bytes-min");
        props.setExpiration(3_600_000L);
        jwtProvider = new JwtProvider(props);

        service = new AuthService(
                userRepository, usuarioRolRepository, rolRepository,
                rolPermisoRepository, permisoRepository, usuarioPermisoDenyRepository,
                usuarioPermisoRepository, jwtProvider,
                passwordEncoder, turnstileService, loginRateLimiter,
                otpService, authRefreshTokenRepository, usuarioSistemaService);

        userOk = new User();
        userOk.setUsername("anibal");
        userOk.setNewClave("N");
        userOk.setEmpleadoId(4321L);
    }

    @Test
    void tokenValidoConSistemasDevuelveResponseCompleta() {
        Map<String, List<String>> sistemas = Map.of(
                "sisrh", List.of("SUPER_ADMIN"),
                "convocatoria", List.of("EVALUADOR")
        );
        String token = jwtProvider.generarTokenDefinitivo(
                userOk, List.of("SUPER_ADMIN"), List.of("PLA_VER"), sistemas);

        ValidateTokenResponse res = service.validarToken("Bearer " + token);

        assertThat(res.getSubject()).isEqualTo("anibal");
        assertThat(res.getRoles()).containsExactly("SUPER_ADMIN");
        assertThat(res.getPermisos()).containsExactly("PLA_VER");
        assertThat(res.getSistemas()).containsOnlyKeys("sisrh", "convocatoria");
        assertThat(res.getEmpleadoId()).isEqualTo(4321L);
        assertThat(res.getExp()).isNotNull().isPositive();
    }

    @Test
    void tokenLegacySinClaimSistemasDevuelveMapaVacio() {
        // Compat Fase 1/2: tokens emitidos antes del SSO (sin claim "sistemas").
        String token = jwtProvider.generarTokenDefinitivo(
                userOk, List.of("RRHH_JEFE"), List.of("PLA_VER"));

        ValidateTokenResponse res = service.validarToken("Bearer " + token);

        assertThat(res.getSubject()).isEqualTo("anibal");
        assertThat(res.getSistemas()).isEmpty();
    }

    @Test
    void headerSinBearerLanzaSeguridadException() {
        assertThatThrownBy(() -> service.validarToken("Basic xyz"))
                .isInstanceOf(SeguridadException.class)
                .hasMessageContaining("Token ausente");

        assertThatThrownBy(() -> service.validarToken(null))
                .isInstanceOf(SeguridadException.class)
                .hasMessageContaining("Token ausente");
    }

    @Test
    void tokenSinOtpValidadoEsRechazadoComoIncompleto() {
        // Un token "temporal" tiene otpValidado=false → NO sirve para SSO.
        String temporal = jwtProvider.generarTokenTemporal(userOk);

        assertThatThrownBy(() -> service.validarToken("Bearer " + temporal))
                .isInstanceOf(SeguridadException.class)
                .hasMessageContaining("Token incompleto");
    }

    @Test
    void tokenSinNewPassOkEsRechazadoComoIncompleto() {
        // Un token cambio-clave tiene newPassOk=false → NO sirve para SSO.
        String cambioClave = jwtProvider.generarTokenCambioClave(userOk);

        assertThatThrownBy(() -> service.validarToken("Bearer " + cambioClave))
                .isInstanceOf(SeguridadException.class)
                .hasMessageContaining("Token incompleto");
    }

    @Test
    void tokenFirmadoConOtroSecretEsRechazado() {
        // Simula un atacante que firma con su propio secret.
        JwtProperties otroProps = new JwtProperties();
        otroProps.setSecret("secret-totalmente-distinto-suficientemente-largo-48-min-bytes!!");
        otroProps.setExpiration(3_600_000L);
        JwtProvider atacante = new JwtProvider(otroProps);
        String tokenFalso = atacante.generarTokenDefinitivo(
                userOk, List.of("SUPER_ADMIN"), List.of("PLA_VER"), Map.of());

        // SignatureException bubblea — el handler global responde 401.
        assertThatThrownBy(() -> service.validarToken("Bearer " + tokenFalso))
                .isInstanceOf(SignatureException.class);
    }
}
