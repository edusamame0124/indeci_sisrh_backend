package com.indeci.sistema.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.indeci.sistema.entity.Sistema;
import com.indeci.sistema.entity.UsuarioSistema;
import com.indeci.sistema.repository.SistemaRepository;
import com.indeci.sistema.repository.UsuarioSistemaRepository;
import com.indeci.user.entity.User;

/**
 * Fase 3 SSO — Tests del armado del mapa {@code sistemas} (claim del JWT).
 * REGLA-07: caso feliz + caso de error + casos borde.
 */
@ExtendWith(MockitoExtension.class)
class UsuarioSistemaServiceTest {

    @Mock private SistemaRepository sistemaRepository;
    @Mock private UsuarioSistemaRepository usuarioSistemaRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks private UsuarioSistemaService service;

    private User user;

    @BeforeEach
    void setUp() {
        // Re-inyecta el ObjectMapper real (Mockito no lo provee por defecto).
        service = new UsuarioSistemaService(sistemaRepository, usuarioSistemaRepository, objectMapper);
        user = new User();
        user.setId(42L);
        user.setUsername("anibal");
    }

    @Test
    void usuarioSinAsignacionesSoloDevuelveSisrhConSusRoles() {
        when(usuarioSistemaRepository.findByUserIdAndActivo(42L, 1)).thenReturn(List.of());

        Map<String, List<String>> resultado =
                service.obtenerSistemasDe(user, List.of("RRHH_JEFE", "PLANILLA_APROBADOR"));

        assertThat(resultado).hasSize(1);
        assertThat(resultado).containsEntry("sisrh", List.of("RRHH_JEFE", "PLANILLA_APROBADOR"));
    }

    @Test
    void usuarioConSistemasExternosActivosDevuelveTodosLosCódigos() {
        Sistema sisconv = sistema(2L, "convocatoria");
        Sistema gdr = sistema(3L, "rendimiento");
        when(sistemaRepository.findByActivoOrderByOrdenAsc(1)).thenReturn(List.of(sisconv, gdr));

        UsuarioSistema asignSisconv = asignacion(2L, "[\"EVALUADOR\",\"CONSULTA\"]");
        UsuarioSistema asignGdr = asignacion(3L, "[\"JEFE_AREA\"]");
        when(usuarioSistemaRepository.findByUserIdAndActivo(42L, 1))
                .thenReturn(List.of(asignSisconv, asignGdr));

        Map<String, List<String>> resultado =
                service.obtenerSistemasDe(user, List.of("SUPER_ADMIN"));

        assertThat(resultado).containsOnlyKeys("sisrh", "convocatoria", "rendimiento");
        assertThat(resultado.get("sisrh")).containsExactly("SUPER_ADMIN");
        assertThat(resultado.get("convocatoria")).containsExactly("EVALUADOR", "CONSULTA");
        assertThat(resultado.get("rendimiento")).containsExactly("JEFE_AREA");
    }

    @Test
    void asignacionContraSistemaDesactivadoSeIgnora() {
        // El sistema 'rendimiento' (id=3) NO está en findByActivoOrderByOrdenAsc → debe omitirse.
        Sistema sisconv = sistema(2L, "convocatoria");
        when(sistemaRepository.findByActivoOrderByOrdenAsc(1)).thenReturn(List.of(sisconv));

        UsuarioSistema asignSisconv = asignacion(2L, "[\"EVALUADOR\"]");
        UsuarioSistema asignGdrAislada = asignacion(3L, "[\"JEFE_AREA\"]");
        when(usuarioSistemaRepository.findByUserIdAndActivo(42L, 1))
                .thenReturn(List.of(asignSisconv, asignGdrAislada));

        Map<String, List<String>> resultado =
                service.obtenerSistemasDe(user, List.of("RRHH_CONSULTA"));

        assertThat(resultado).containsOnlyKeys("sisrh", "convocatoria");
        assertThat(resultado).doesNotContainKey("rendimiento");
    }

    @Test
    void rolesExternosCorruptoDegradaListaVaciaSinRomperLogin() {
        Sistema sisconv = sistema(2L, "convocatoria");
        when(sistemaRepository.findByActivoOrderByOrdenAsc(1)).thenReturn(List.of(sisconv));

        UsuarioSistema asignCorrupta = asignacion(2L, "no es json valido [[[");
        when(usuarioSistemaRepository.findByUserIdAndActivo(42L, 1))
                .thenReturn(List.of(asignCorrupta));

        Map<String, List<String>> resultado =
                service.obtenerSistemasDe(user, List.of("SUPER_ADMIN"));

        assertThat(resultado).containsOnlyKeys("sisrh", "convocatoria");
        assertThat(resultado.get("convocatoria")).isEmpty();
    }

    @Test
    void rolesExternosNullSeMapeaAListaVacia() {
        Sistema sisconv = sistema(2L, "convocatoria");
        when(sistemaRepository.findByActivoOrderByOrdenAsc(1)).thenReturn(List.of(sisconv));

        UsuarioSistema asignSinRoles = asignacion(2L, null);
        when(usuarioSistemaRepository.findByUserIdAndActivo(42L, 1))
                .thenReturn(List.of(asignSinRoles));

        Map<String, List<String>> resultado =
                service.obtenerSistemasDe(user, List.of("SUPER_ADMIN"));

        assertThat(resultado.get("convocatoria")).isEmpty();
    }

    private Sistema sistema(Long id, String codigo) {
        Sistema s = new Sistema();
        s.setId(id);
        s.setCodigo(codigo);
        s.setActivo(1);
        return s;
    }

    private UsuarioSistema asignacion(Long sistemaId, String rolesJson) {
        UsuarioSistema u = new UsuarioSistema();
        u.setUserId(42L);
        u.setSistemaId(sistemaId);
        u.setRolesExternos(rolesJson);
        u.setActivo(1);
        return u;
    }
}
