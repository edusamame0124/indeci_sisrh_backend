package com.indeci.rrhh.service.subsidio;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.entity.SubsidioCaso;
import com.indeci.rrhh.repository.SubsidioCasoRepository;
import com.indeci.rrhh.subsidio.SubsidioEstados;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regla de eliminación (anulación lógica) de casos de subsidio: eliminable solo
 * mientras no haya impactado la planilla ({@code BORRADOR}, {@code PENDIENTE_VALIDACION},
 * {@code CALCULADO}); bloqueado en {@code APLICADO_PLANILLA} y posteriores.
 */
@ExtendWith(MockitoExtension.class)
class SubsidioCasoServiceAnularTest {

    @Mock private SubsidioCasoRepository casoRepository;
    @Mock private SubsidioTimelineService timelineService;

    @InjectMocks private SubsidioCasoService service;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("tester", null));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private SubsidioCaso caso(String estado) {
        SubsidioCaso c = new SubsidioCaso();
        c.setId(7L);
        c.setEstado(estado);
        c.setActivo(1);
        return c;
    }

    @Test
    void anula_caso_en_borrador_baja_logica_y_timeline() {
        SubsidioCaso c = caso(SubsidioEstados.CASO_BORRADOR);
        when(casoRepository.findByIdAndActivo(7L, 1)).thenReturn(Optional.of(c));

        service.anular(7L, "Duplicado por error de digitación");

        assertThat(c.getActivo()).isZero();
        assertThat(c.getEstado()).isEqualTo("ANULADO");
        verify(casoRepository).save(c);
        verify(timelineService).registrar(eq(7L), eq("ELIMINACION"),
                eq("Caso de subsidio anulado. Sustento: Duplicado por error de digitación"), eq(7L));
    }

    @Test
    void anula_caso_calculado_es_permitido() {
        SubsidioCaso c = caso(SubsidioEstados.CASO_CALCULADO);
        when(casoRepository.findByIdAndActivo(7L, 1)).thenReturn(Optional.of(c));

        service.anular(7L, "Recalcular con base corregida");

        assertThat(c.getActivo()).isZero();
        assertThat(c.getEstado()).isEqualTo("ANULADO");
    }

    @Test
    void bloquea_anulacion_de_caso_aplicado_a_planilla() {
        SubsidioCaso c = caso(SubsidioEstados.CASO_APLICADO_PLANILLA);
        when(casoRepository.findByIdAndActivo(7L, 1)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.anular(7L, "Motivo cualquiera"))
                .isInstanceOf(NegocioException.class);

        assertThat(c.getActivo()).isEqualTo(1);
        verify(casoRepository, never()).save(c);
        verify(timelineService, never()).registrar(anyLong(), eq("ELIMINACION"), eq("Motivo cualquiera"), anyLong());
    }

    @Test
    void bloquea_anulacion_de_caso_cerrado() {
        SubsidioCaso c = caso(SubsidioEstados.CASO_CERRADO);
        when(casoRepository.findByIdAndActivo(7L, 1)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.anular(7L, "Motivo cualquiera"))
                .isInstanceOf(NegocioException.class);

        assertThat(c.getActivo()).isEqualTo(1);
        verify(casoRepository, never()).save(c);
    }
}
