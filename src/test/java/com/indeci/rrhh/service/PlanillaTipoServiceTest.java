package com.indeci.rrhh.service;

import com.indeci.audit.context.AuditoriaContext;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.PlanillaTipoDto;
import com.indeci.rrhh.entity.PlanillaTipo;
import com.indeci.rrhh.repository.PlanillaTipoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SPEC_CONCEPTOS_PLANILLA §15 / Fase A — catálogo de tipos de planilla.
 */
@ExtendWith(MockitoExtension.class)
class PlanillaTipoServiceTest {

    @Mock private PlanillaTipoRepository repository;
    @Mock private AuditoriaContext auditoriaContext;

    @InjectMocks private PlanillaTipoService service;

    // ---- listar devuelve los 3 sembrados, ordenados ----
    @Test
    void listar_devuelve_los_tres_tipos_sembrados_ordenados() {
        when(repository.findByActivoOrderByOrden(1)).thenReturn(List.of(
                tipo("CAS", "CAS", 10),
                tipo("CAS_TEMP", "CAS TEMPORAL", 20),
                tipo("CAS_ADIC", "CAS ADICIONAL", 30)));

        List<PlanillaTipoDto> result = service.listar();

        assertThat(result).extracting(PlanillaTipoDto::getCodigo)
                .containsExactly("CAS", "CAS_TEMP", "CAS_ADIC");
        assertThat(result).extracting(PlanillaTipoDto::getNombre)
                .containsExactly("CAS", "CAS TEMPORAL", "CAS ADICIONAL");
    }

    // ---- crear con nombre que genera código duplicado → NegocioException ----
    @Test
    void crear_rechaza_codigo_duplicado() {
        when(repository.existsById("CAS")).thenReturn(true);

        PlanillaTipoDto dto = new PlanillaTipoDto();
        dto.setNombre("CAS");

        assertThatThrownBy(() -> service.crear(dto))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("Ya existe un tipo de planilla con código autogenerado CAS");
        verify(repository, never()).save(any());
    }

    // ---- crear válido → ACTIVO=1 por defecto, orden automático ----
    @Test
    void crear_persiste_con_activo_y_orden_por_defecto() {
        when(repository.existsById("CAS_EXTRA")).thenReturn(false);
        when(repository.findMaxOrden()).thenReturn(30);

        PlanillaTipoDto dto = new PlanillaTipoDto();
        dto.setNombre("CAS EXTRA");
        dto.setDescripcion("Una descripción");

        service.crear(dto);

        ArgumentCaptor<PlanillaTipo> captor = ArgumentCaptor.forClass(PlanillaTipo.class);
        verify(repository).save(captor.capture());
        
        PlanillaTipo guardado = captor.getValue();
        assertThat(guardado.getCodigo()).isEqualTo("CAS_EXTRA");
        assertThat(guardado.getNombre()).isEqualTo("CAS EXTRA");
        assertThat(guardado.getDescripcion()).isEqualTo("Una descripción");
        assertThat(guardado.getOrden()).isEqualTo(31);
        assertThat(guardado.getActivo()).isEqualTo(1);
    }

    // ---- eliminar = baja lógica ----
    @Test
    void eliminar_hace_baja_logica() {
        PlanillaTipo e = tipo("CAS_ADIC", "CAS ADICIONAL", 30);
        when(repository.findById("CAS_ADIC")).thenReturn(java.util.Optional.of(e));

        service.eliminar("CAS_ADIC");

        assertThat(e.getActivo()).isEqualTo(0);
        verify(repository).save(e);
    }

    private PlanillaTipo tipo(String codigo, String nombre, int orden) {
        PlanillaTipo e = new PlanillaTipo();
        e.setCodigo(codigo);
        e.setNombre(nombre);
        e.setOrden(orden);
        e.setActivo(1);
        return e;
    }
}
