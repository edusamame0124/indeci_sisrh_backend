package com.indeci.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.indeci.admin.dto.AdminSetPasswordRequest;
import com.indeci.auth.entity.AuthRefreshToken;
import com.indeci.auth.repository.AuthRefreshTokenRepository;
import com.indeci.exception.NegocioException;
import com.indeci.user.entity.User;
import com.indeci.user.repository.UserRepository;

/**
 * Soporte de mesa de ayuda (SUPER_ADMIN) — {@link AdminUserService#setPassword}.
 * El guard "solo SUPER_ADMIN" es declarativo (@PreAuthorize en el controller,
 * mismo mecanismo ya usado en todo el módulo admin) y no se re-testea aquí.
 * REGLA-07: caso feliz + error normativo (usuario inexistente) + borde (sin
 * sesiones activas que revocar).
 */
@ExtendWith(MockitoExtension.class)
class AdminUserServiceSetPasswordTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthRefreshTokenRepository authRefreshTokenRepository;

    @InjectMocks
    private AdminUserService adminUserService;

    private User usuarioObjetivo() {
        User user = new User();
        user.setId(7L);
        user.setUsername("jperez");
        user.setNewClave("N");
        return user;
    }

    private AdminSetPasswordRequest request(String claveNueva) {
        AdminSetPasswordRequest req = new AdminSetPasswordRequest();
        req.setClaveNueva(claveNueva);
        return req;
    }

    @Test
    void casoFeliz_defineClaveTemporalYRevocaSesionesActivas() {
        User user = usuarioObjetivo();
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("NuevaS3g!")).thenReturn("hash-encoded");

        AuthRefreshToken tokenActivo = new AuthRefreshToken();
        tokenActivo.setUsuario("jperez");
        tokenActivo.setActivo("S");
        when(authRefreshTokenRepository.findByUsuarioAndActivo("jperez", "S"))
                .thenReturn(List.of(tokenActivo));

        adminUserService.setPassword(7L, request("NuevaS3g!"));

        assertThat(user.getPassword()).isEqualTo("hash-encoded");
        assertThat(user.getPasswordHash()).isEqualTo("hash-encoded");
        assertThat(user.getNewClave()).isEqualTo("S");
        verify(userRepository).save(user);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AuthRefreshToken>> captor = ArgumentCaptor.forClass(List.class);
        verify(authRefreshTokenRepository).saveAll(captor.capture());

        List<AuthRefreshToken> revocados = captor.getValue();
        assertThat(revocados).hasSize(1);
        assertThat(revocados.get(0).getActivo()).isEqualTo("N");
        assertThat(revocados.get(0).getMotivoRevocacion()).isEqualTo("ADMIN_RESET_CLAVE");
        assertThat(revocados.get(0).getFechaRevocacion()).isNotNull();
    }

    @Test
    void usuarioInexistente_lanzaNegocioExceptionYNoTocaNada() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminUserService.setPassword(99L, request("NuevaS3g!")))
                .isInstanceOf(NegocioException.class)
                .hasMessage("Usuario no encontrado");

        verify(userRepository, never()).save(any());
        verify(authRefreshTokenRepository, never()).saveAll(any());
    }

    @Test
    void sinSesionesActivas_noFallaYGuardaListaVacia() {
        User user = usuarioObjetivo();
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode(any())).thenReturn("hash-encoded");
        when(authRefreshTokenRepository.findByUsuarioAndActivo("jperez", "S"))
                .thenReturn(List.of());

        adminUserService.setPassword(7L, request("NuevaS3g!"));

        verify(userRepository).save(user);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AuthRefreshToken>> captor = ArgumentCaptor.forClass(List.class);
        verify(authRefreshTokenRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).isEmpty();
    }
}
