package com.indeci.rrhh.service;

import com.indeci.exception.ConceptoNoAsignableManualmenteException;
import com.indeci.exception.ConceptoYaAsignadoException;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.EmpleadoConceptoDto;
import com.indeci.rrhh.entity.ConceptoPlanilla;
import com.indeci.rrhh.entity.EmpleadoConcepto;
import com.indeci.rrhh.repository.ConceptoPlanillaRepository;
import com.indeci.rrhh.repository.EmpleadoConceptoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Spec 013 / C1 — Validación del endpoint guardar de EmpleadoConcepto:
 *   - VALIDACIÓN 1 (relajada): rechaza conceptos calculados por el motor.
 *   - VALIDACIÓN 2: rechaza duplicados activos.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmpleadoConceptoServiceTest {

    @Mock private EmpleadoConceptoRepository repository;
    @Mock private ConceptoPlanillaRepository conceptoRepository;

    @InjectMocks private EmpleadoConceptoService service;

    private static final Long EMPLEADO_ID = 50L;
    private static final Long CONCEPTO_ID = 9L;

    @Test
    void guardar_descuento_voluntario_se_persiste() {
        // CASO FELIZ — DESCUENTO con SISPER, sin duplicado.
        stubConcepto(concepto("05301", "DESCUENTO", "Coop. La Rehabilitadora"));
        when(repository.existsByEmpleadoIdAndConceptoPlanillaIdAndActivo(
                EMPLEADO_ID, CONCEPTO_ID, 1)).thenReturn(false);

        service.guardar(dto(120.0));

        ArgumentCaptor<EmpleadoConcepto> captor =
                ArgumentCaptor.forClass(EmpleadoConcepto.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getActivo()).isEqualTo(1);
        assertThat(captor.getValue().getMonto()).isEqualTo(120.0);
    }

    @Test
    void guardar_remunerativo_manual_se_permite() {
        // REGLA RELAJADA — el sueldo básico REMUNERATIVO sí puede asignarse a
        // mano mientras el motor no lo calcule por régimen (deuda Etapa 3).
        stubConcepto(concepto("00301", "REMUNERATIVO", "Sueldo Básico"));
        when(repository.existsByEmpleadoIdAndConceptoPlanillaIdAndActivo(
                EMPLEADO_ID, CONCEPTO_ID, 1)).thenReturn(false);

        service.guardar(dto(2500.0));

        verify(repository).save(any(EmpleadoConcepto.class));
    }

    @Test
    void guardar_aporte_empleador_es_rechazado_422() {
        // ERROR NORMATIVO — ESSALUD lo calcula el motor (APORTE_EMPLEADOR).
        stubConcepto(concepto("06001", "APORTE_EMPLEADOR", "ESSALUD 9%"));

        assertThatThrownBy(() -> service.guardar(dto(300.0)))
                .isInstanceOf(ConceptoNoAsignableManualmenteException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void guardar_concepto_autocalculado_por_mef_es_rechazado() {
        // CASO BORDE — la retención 5ta tiene TIPO_CONCEPTO=DESCUENTO (lo dejaría
        // pasar la regla por tipo) pero su CODIGO_MEF 05101 está en
        // MEF_AUTOCALCULADOS → igual se rechaza.
        stubConcepto(concepto("05101", "DESCUENTO", "Retención IR 5ta"));

        assertThatThrownBy(() -> service.guardar(dto(150.0)))
                .isInstanceOf(ConceptoNoAsignableManualmenteException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void guardar_duplicado_activo_es_rechazado_409() {
        // VALIDACIÓN 2 — ya existe el concepto activo para el empleado.
        stubConcepto(concepto("05301", "DESCUENTO", "Coop. La Rehabilitadora"));
        when(repository.existsByEmpleadoIdAndConceptoPlanillaIdAndActivo(
                EMPLEADO_ID, CONCEPTO_ID, 1)).thenReturn(true);

        assertThatThrownBy(() -> service.guardar(dto(120.0)))
                .isInstanceOf(ConceptoYaAsignadoException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void actualizar_modifica_monto_y_vigencia() {
        EmpleadoConcepto entity = entityActivo(7L, 600.0);
        when(repository.findById(7L)).thenReturn(Optional.of(entity));

        EmpleadoConceptoDto update = dto(750.0);
        update.setFechaInicio(LocalDate.of(2026, 1, 1));
        update.setFechaFin(LocalDate.of(2026, 12, 1));

        service.actualizar(7L, update);

        ArgumentCaptor<EmpleadoConcepto> captor =
                ArgumentCaptor.forClass(EmpleadoConcepto.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getMonto()).isEqualTo(750.0);
        assertThat(captor.getValue().getFechaInicio())
                .isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(captor.getValue().getFechaFin())
                .isEqualTo(LocalDate.of(2026, 12, 1));
    }

    @Test
    void actualizar_registro_inexistente_lanza_negocio() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.actualizar(99L, dto(100.0)))
                .isInstanceOf(NegocioException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void actualizar_cambio_concepto_duplicado_es_rechazado_409() {
        EmpleadoConcepto entity = entityActivo(7L, 600.0);
        when(repository.findById(7L)).thenReturn(Optional.of(entity));

        Long otroConceptoId = 10L;
        ConceptoPlanilla otro = concepto("05302", "DESCUENTO", "Otro descuento");
        otro.setId(otroConceptoId);
        when(conceptoRepository.findById(otroConceptoId)).thenReturn(Optional.of(otro));
        when(repository.existsByEmpleadoIdAndConceptoPlanillaIdAndActivoAndIdNot(
                EMPLEADO_ID, otroConceptoId, 1, 7L)).thenReturn(true);

        EmpleadoConceptoDto update = dto(750.0);
        update.setConceptoPlanillaId(otroConceptoId);

        assertThatThrownBy(() -> service.actualizar(7L, update))
                .isInstanceOf(ConceptoYaAsignadoException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void eliminar_desactiva_registro_activo() {
        EmpleadoConcepto entity = entityActivo(8L, 500.0);
        when(repository.findById(8L)).thenReturn(Optional.of(entity));

        service.eliminar(8L);

        ArgumentCaptor<EmpleadoConcepto> captor =
                ArgumentCaptor.forClass(EmpleadoConcepto.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getActivo()).isEqualTo(0);
    }

    @Test
    void eliminar_registro_inexistente_lanza_negocio() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.eliminar(99L))
                .isInstanceOf(NegocioException.class);
        verify(repository, never()).save(any());
    }

    // ===================== HELPERS =====================

    private void stubConcepto(ConceptoPlanilla c) {
        when(conceptoRepository.findById(CONCEPTO_ID)).thenReturn(Optional.of(c));
    }

    private EmpleadoConceptoDto dto(Double monto) {
        EmpleadoConceptoDto d = new EmpleadoConceptoDto();
        d.setEmpleadoId(EMPLEADO_ID);
        d.setConceptoPlanillaId(CONCEPTO_ID);
        d.setMonto(monto);
        return d;
    }

    private EmpleadoConcepto entityActivo(Long id, Double monto) {
        EmpleadoConcepto entity = new EmpleadoConcepto();
        entity.setId(id);
        entity.setEmpleadoId(EMPLEADO_ID);
        entity.setConceptoPlanillaId(CONCEPTO_ID);
        entity.setMonto(monto);
        entity.setActivo(1);
        return entity;
    }

    private ConceptoPlanilla concepto(String mef, String tipoConcepto, String nombre) {
        ConceptoPlanilla c = new ConceptoPlanilla();
        c.setId(CONCEPTO_ID);
        c.setCodigoMef(mef);
        c.setTipoConcepto(tipoConcepto);
        c.setNombre(nombre);
        return c;
    }
}
