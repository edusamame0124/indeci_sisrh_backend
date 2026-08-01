package com.indeci.rrhh.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.entity.SolicitudRrhh;
import com.indeci.rrhh.entity.SolicitudRrhhDoc;
import com.indeci.rrhh.repository.SolicitudRrhhDocRepository;
import com.indeci.rrhh.repository.SolicitudRrhhRepository;

import io.jsonwebtoken.Claims;

/**
 * Defensa en profundidad (IDOR) — {@link SolicitudRrhhDocService#listar}: el controller acepta
 * PAP_EMPLEADO además de los permisos de revisor/admin, así que el guard de propiedad para
 * quien SOLO tiene PAP_EMPLEADO vive aquí (evita leer documentos de una papeleta ajena pasando
 * cualquier solicitudId). Los roles revisores/admin (PAP_JEFE/PAP_RRHH/PAP_APROBAR_RRHH/
 * EMP_READ/EMP_WRITE) conservan el acceso sin restricción — revisan papeletas ajenas por diseño.
 */
@ExtendWith(MockitoExtension.class)
class SolicitudRrhhDocServiceTest {

    private static final Long EMPLEADO_ID = 42L;
    private static final Long SOLICITUD_ID = 100L;

    @Mock
    private SolicitudRrhhDocRepository repository;

    @Mock
    private SolicitudRrhhRepository solicitudRepository;

    @InjectMocks
    private SolicitudRrhhDocService service;

    private void autenticarComo(Long empleadoId, String... authorities) {
        Claims claims = mock(Claims.class);
        // Solo se consulta cuando el guard de propiedad se ejecuta (sin authority de revisor).
        lenient().when(claims.get("empleadoId")).thenReturn(empleadoId);

        List<SimpleGrantedAuthority> grants = List.of(authorities).stream()
                .map(SimpleGrantedAuthority::new)
                .toList();

        // El detalle (claims con empleadoId) se lee vía Authentication.getDetails() en SecurityUtil,
        // pero solo cuando el guard de propiedad se ejecuta (sin authority de revisor) — lenient.
        Authentication auth = mock(Authentication.class);
        lenient().when(auth.getDetails()).thenReturn(claims);
        when(auth.getAuthorities()).thenAnswer(inv -> grants);

        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    private SolicitudRrhh solicitud(Long id, Long empleadoId) {
        SolicitudRrhh s = new SolicitudRrhh();
        s.setId(id);
        s.setEmpleadoId(empleadoId);
        return s;
    }

    @Test
    void empleado_dueño_lista_sus_propios_documentos() {
        autenticarComo(EMPLEADO_ID, "PAP_EMPLEADO");

        when(solicitudRepository.findById(SOLICITUD_ID))
                .thenReturn(Optional.of(solicitud(SOLICITUD_ID, EMPLEADO_ID)));
        when(repository.findBySolicitudIdAndActivoOrderByVersionDocAsc(SOLICITUD_ID, 1))
                .thenReturn(List.of(new SolicitudRrhhDoc()));

        assertThat(service.listar(SOLICITUD_ID)).hasSize(1);
    }

    @Test
    void empleado_con_solo_pap_empleado_no_puede_ver_documentos_de_otro() {
        autenticarComo(EMPLEADO_ID, "PAP_EMPLEADO");

        when(solicitudRepository.findById(SOLICITUD_ID))
                .thenReturn(Optional.of(solicitud(SOLICITUD_ID, 999L))); // papeleta ajena

        assertThatThrownBy(() -> service.listar(SOLICITUD_ID))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("otro empleado");
    }

    @Test
    void jefe_revisa_documentos_de_papeleta_ajena_sin_restriccion() {
        autenticarComo(EMPLEADO_ID, "PAP_JEFE");

        when(repository.findBySolicitudIdAndActivoOrderByVersionDocAsc(SOLICITUD_ID, 1))
                .thenReturn(List.of(new SolicitudRrhhDoc()));

        // No consulta el dueño: el reviewer no está sujeto al guard de propiedad.
        assertThat(service.listar(SOLICITUD_ID)).hasSize(1);
    }

    @Test
    void evaluador_rrhh_papeleta_revisa_documentos_sin_emp_read() {
        // Rol "Perfil Evaluador de Papeletas": solo PAP_APROBAR_RRHH/PAP_RRHH, sin EMP_READ.
        autenticarComo(EMPLEADO_ID, "PAP_APROBAR_RRHH", "PAP_RRHH");

        when(repository.findBySolicitudIdAndActivoOrderByVersionDocAsc(SOLICITUD_ID, 1))
                .thenReturn(List.of(new SolicitudRrhhDoc()));

        assertThat(service.listar(SOLICITUD_ID)).hasSize(1);
    }
}
