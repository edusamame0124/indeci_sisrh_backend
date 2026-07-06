package com.indeci.rrhh.service;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.ReintegroMontoDto;
import com.indeci.rrhh.entity.MotivoReintegro;
import com.indeci.rrhh.entity.ReintegroMonto;
import com.indeci.rrhh.repository.MovimientoPlanillaRepository;
import com.indeci.rrhh.repository.ReintegroMontoRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BLOQUE 2 — Blindaje del registro de reintegros/devengados (Modelo B).
 * Cubre la validación de contrato (Bean Validation) y la lógica del servicio.
 */
@ExtendWith(MockitoExtension.class)
class ReintegroMontoServiceTest {

    @Mock private ReintegroMontoRepository reintegroMontoRepository;
    @Mock private MovimientoPlanillaRepository movimientoPlanillaRepository;
    @InjectMocks private ReintegroMontoService service;

    private static Validator validator;
    private static ValidatorFactory factory;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private ReintegroMontoDto dtoValido() {
        ReintegroMontoDto dto = new ReintegroMontoDto();
        dto.setEmpleadoId(41L);
        dto.setPeriodoDestino("2026-06");
        dto.setMonto(new BigDecimal("1500.00"));
        dto.setMotivo(MotivoReintegro.DEVENGADO_JUDICIAL);
        dto.setSustento("R.J. 123-2026-INDECI");
        return dto;
    }

    // ---- Bean Validation (contrato) ----

    @Test
    void dto_valido_sin_origen_no_tiene_violaciones() {
        Set<ConstraintViolation<ReintegroMontoDto>> v = validator.validate(dtoValido());
        assertThat(v).isEmpty();
    }

    @Test
    void dto_rechaza_campos_obligatorios_faltantes() {
        ReintegroMontoDto dto = new ReintegroMontoDto();
        dto.setMonto(new BigDecimal("-5")); // no positivo
        dto.setSustento("   ");             // en blanco
        // empleadoId, periodoDestino, motivo nulos

        Set<ConstraintViolation<ReintegroMontoDto>> v = validator.validate(dto);
        // empleadoId, periodoDestino, monto(@Positive), motivo, sustento(@NotBlank)
        assertThat(v).hasSize(5);
    }

    @Test
    void dto_rechaza_sustento_solo_espacios() {
        ReintegroMontoDto dto = dtoValido();
        dto.setSustento("    ");
        Set<ConstraintViolation<ReintegroMontoDto>> v = validator.validate(dto);
        assertThat(v).extracting(ConstraintViolation::getMessage)
                .anyMatch(m -> m.contains("N° de Resolución o Mandato Judicial"));
    }

    @Test
    void dto_retroactivo_sin_origen_es_rechazado() {
        ReintegroMontoDto dto = dtoValido();
        dto.setMotivo(MotivoReintegro.RETROACTIVO); // exige periodo + concepto de origen
        Set<ConstraintViolation<ReintegroMontoDto>> v = validator.validate(dto);
        assertThat(v).extracting(ConstraintViolation::getMessage)
                .anyMatch(m -> m.contains("periodo y concepto de origen"));
    }

    @Test
    void dto_retroactivo_con_origen_es_valido() {
        ReintegroMontoDto dto = dtoValido();
        dto.setMotivo(MotivoReintegro.DIFERENCIA_REMUNERATIVA);
        dto.setPeriodoOrigen("2026-05");
        dto.setConceptoOrigenCodigo("00501");
        assertThat(validator.validate(dto)).isEmpty();
    }

    // ---- Lógica del servicio ----

    @Test
    void registrar_persiste_pendiente_y_motivo_canonico_sin_origen() {
        when(reintegroMontoRepository.save(any(ReintegroMonto.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.registrarReintegro(dtoValido());

        ArgumentCaptor<ReintegroMonto> captor = ArgumentCaptor.forClass(ReintegroMonto.class);
        verify(reintegroMontoRepository).save(captor.capture());
        ReintegroMonto saved = captor.getValue();
        assertThat(saved.getEstadoPago()).isEqualTo("PENDIENTE");
        assertThat(saved.getMotivo()).isEqualTo("DEVENGADO_JUDICIAL");
        assertThat(saved.getMonto()).isEqualByComparingTo("1500.00");
        // Sin movimiento origen → no se valida contra el repositorio.
        verify(movimientoPlanillaRepository, never()).findById(any());
    }

    @Test
    void registrar_con_movimiento_origen_inexistente_lanza_negocio() {
        ReintegroMontoDto dto = dtoValido();
        dto.setMovimientoOrigenId(999L);
        when(movimientoPlanillaRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registrarReintegro(dto))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("Movimiento origen");
        verify(reintegroMontoRepository, never()).save(any());
    }
}
