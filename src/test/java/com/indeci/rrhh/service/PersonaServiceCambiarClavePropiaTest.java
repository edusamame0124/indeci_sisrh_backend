package com.indeci.rrhh.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.indeci.auth.entity.AuthRefreshToken;
import com.indeci.auth.repository.AuthRefreshTokenRepository;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.CambiarClavePropiaDto;
import com.indeci.security.password.PasswordService;
import com.indeci.user.entity.User;
import com.indeci.user.repository.UserRepository;

/**
 * Autoservicio de Mi Perfil — cambio de contraseña voluntario (a diferencia de
 * {@code AuthService.cambiarClave()}, exclusivo de la clave temporal forzada del
 * primer login, aquí SÍ se exige conocer la clave actual).
 * REGLA-07: caso feliz + error normativo (clave incorrecta) + caso de borde
 * (clave temporal pendiente / clave nueva igual a la actual).
 */
@ExtendWith(MockitoExtension.class)
class PersonaServiceCambiarClavePropiaTest {

    private static final String USERNAME = "jperez";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordService passwordService;

    @Mock
    private AuthRefreshTokenRepository authRefreshTokenRepository;

    @InjectMocks
    private PersonaService personaService;

    @BeforeEach
    void autenticarComoUsuario() {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn(USERNAME);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    private User usuarioActivo() {
        User user = new User();
        user.setUsername(USERNAME);
        user.setNewClave("N");
        return user;
    }

    @Test
    void casoFeliz_actualizaClaveYRevocaOtrasSesiones() {
        User user = usuarioActivo();
        CambiarClavePropiaDto dto = new CambiarClavePropiaDto();
        dto.setClaveActual("ActualS3g!");
        dto.setClaveNueva("NuevaS3g!");

        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
        when(passwordService.verificarClave(user, "ActualS3g!")).thenReturn(true);

        AuthRefreshToken tokenActivo = new AuthRefreshToken();
        tokenActivo.setUsuario(USERNAME);
        tokenActivo.setActivo("S");
        when(authRefreshTokenRepository.findByUsuarioAndActivo(USERNAME, "S"))
                .thenReturn(List.of(tokenActivo));

        personaService.cambiarClavePropia(dto);

        verify(passwordService).aplicarClaveCodificada(user, "NuevaS3g!");
        verify(userRepository).save(user);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AuthRefreshToken>> captor = ArgumentCaptor.forClass(List.class);
        verify(authRefreshTokenRepository).saveAll(captor.capture());

        List<AuthRefreshToken> revocados = captor.getValue();
        assertThat(revocados).hasSize(1);
        assertThat(revocados.get(0).getActivo()).isEqualTo("N");
        assertThat(revocados.get(0).getMotivoRevocacion()).isEqualTo("CAMBIO_CLAVE");
        assertThat(revocados.get(0).getFechaRevocacion()).isNotNull();
    }

    @Test
    void claveActualIncorrecta_lanzaNegocioExceptionYNoGuardaNada() {
        User user = usuarioActivo();
        CambiarClavePropiaDto dto = new CambiarClavePropiaDto();
        dto.setClaveActual("Equivocada1!");
        dto.setClaveNueva("NuevaS3g!");

        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
        when(passwordService.verificarClave(user, "Equivocada1!")).thenReturn(false);

        assertThatThrownBy(() -> personaService.cambiarClavePropia(dto))
                .isInstanceOf(NegocioException.class)
                .hasMessage("La contraseña actual no es correcta");

        verify(userRepository, never()).save(any());
        verify(authRefreshTokenRepository, never()).saveAll(any());
    }

    @Test
    void claveTemporalPendiente_rechazaSinLlegarAVerificarClave() {
        User user = usuarioActivo();
        user.setNewClave("S");

        CambiarClavePropiaDto dto = new CambiarClavePropiaDto();
        dto.setClaveActual("ActualS3g!");
        dto.setClaveNueva("NuevaS3g!");

        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> personaService.cambiarClavePropia(dto))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("contraseña temporal pendiente");

        verify(passwordService, never()).verificarClave(any(), any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void claveNuevaIgualALaActual_lanzaNegocioException() {
        User user = usuarioActivo();
        CambiarClavePropiaDto dto = new CambiarClavePropiaDto();
        dto.setClaveActual("MismaClave1!");
        dto.setClaveNueva("MismaClave1!");

        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
        when(passwordService.verificarClave(user, "MismaClave1!")).thenReturn(true);

        assertThatThrownBy(() -> personaService.cambiarClavePropia(dto))
                .isInstanceOf(NegocioException.class)
                .hasMessage("La nueva contraseña debe ser distinta a la actual");

        verify(userRepository, never()).save(any());
    }
}
