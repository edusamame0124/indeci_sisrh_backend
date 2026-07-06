package com.indeci.rrhh.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.indeci.audit.context.AuditoriaContext;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.EmpleadoPlanillaDto;
import com.indeci.rrhh.dto.IncrementosDsResponseDto;
import com.indeci.rrhh.entity.EmpleadoPlanilla;
import com.indeci.rrhh.repository.CondicionLaboralRepository;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.EmpleadoRepository;
import com.indeci.rrhh.repository.PersonaRepository;
import com.indeci.rrhh.repository.RegimenLaboralRepository;
import com.indeci.rrhh.repository.TipoContratoRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmpleadoPlanillaServiceTest {

    private static final Long EMPLEADO_ID = 100L;
    private static final Long PLANILLA_ID = 1L;

    @Mock private EmpleadoPlanillaRepository repository;
    @Mock private AuditoriaContext auditoriaContext;
    @Mock private RegimenLaboralRepository regimenLaboralRepository;
    @Mock private TipoContratoRepository tipoContratoRepository;
    @Mock private CondicionLaboralRepository condicionLaboralRepository;
    @Mock private EmpleadoRepository empleadoRepository;
    @Mock private PersonaRepository personaRepository;
    @Mock private IncrementosDsCalculoService incrementosDsCalculoService;

    @InjectMocks private EmpleadoPlanillaService service;

    @BeforeEach
    void sinPlanillaActiva() {
        when(repository.findByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(List.of());
    }

    @Test
    void guardar_rechaza_airhsp_invalido() {
        EmpleadoPlanillaDto dto = dtoBase();
        dto.setCodigoAirhsp("51");

        assertThatThrownBy(() -> service.guardar(dto))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("AIRHSP");

        verify(repository, never()).save(any());
    }

    @Test
    void guardar_rechaza_sueldo_basico_distinto_al_calculado() {
        stubCalculo("4864.19");
        EmpleadoPlanillaDto dto = dtoBase();
        dto.setSueldoBasico(5000.0);

        assertThatThrownBy(() -> service.guardar(dto))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("Remuneración mensual");

        verify(repository, never()).save(any());
    }

    @Test
    void guardar_persiste_airhsp_monto_y_sueldo_recalculado() {
        stubCalculo("4864.19");
        EmpleadoPlanillaDto dto = dtoBase();
        dto.setSueldoBasico(4864.19);

        service.guardar(dto);

        ArgumentCaptor<EmpleadoPlanilla> captor = ArgumentCaptor.forClass(EmpleadoPlanilla.class);
        verify(repository).save(captor.capture());

        EmpleadoPlanilla saved = captor.getValue();
        assertThat(saved.getCodigoAirhsp()).isEqualTo("000051");
        assertThat(saved.getMontoContrato()).isEqualTo(4500.0);
        assertThat(saved.getSueldoBasico()).isEqualTo(4864.19);
        assertThat(saved.getMovilidad()).isNull();
        assertThat(saved.getAlimentacion()).isNull();
    }

    @Test
    void guardar_acepta_tolerancia_de_un_centavo_en_sueldo_basico() {
        stubCalculo("4864.19");
        EmpleadoPlanillaDto dto = dtoBase();
        dto.setSueldoBasico(4864.20);

        service.guardar(dto);

        verify(repository).save(any(EmpleadoPlanilla.class));
    }

    @Test
    void actualizar_recalcula_y_persiste_campos_remunerativos() {
        EmpleadoPlanilla entity = new EmpleadoPlanilla();
        entity.setId(PLANILLA_ID);
        entity.setEmpleadoId(EMPLEADO_ID);
        when(repository.findById(PLANILLA_ID)).thenReturn(Optional.of(entity));
        stubCalculo("3000.00");

        EmpleadoPlanillaDto dto = dtoBase();
        dto.setMontoContrato(3000.0);
        dto.setSueldoBasico(3000.0);

        service.actualizar(PLANILLA_ID, dto);

        assertThat(entity.getCodigoAirhsp()).isEqualTo("000051");
        assertThat(entity.getMontoContrato()).isEqualTo(3000.0);
        assertThat(entity.getSueldoBasico()).isEqualTo(3000.0);
        verify(repository).save(entity);
    }

    @Test
    void listar_incluye_codigo_airhsp_y_monto_contrato() {
        EmpleadoPlanilla entity = new EmpleadoPlanilla();
        entity.setId(PLANILLA_ID);
        entity.setCodigoAirhsp("000051");
        entity.setMontoContrato(4500.0);
        entity.setSueldoBasico(4864.19);
        entity.setActivo(1);
        when(repository.findByEmpleadoIdAndActivo(EMPLEADO_ID, 1)).thenReturn(List.of(entity));

        var rows = service.listar(EMPLEADO_ID);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getCodigoAirhsp()).isEqualTo("000051");
        assertThat(rows.get(0).getMontoContrato()).isEqualTo(4500.0);
        assertThat(rows.get(0).getSueldoBasico()).isEqualTo(4864.19);
    }

    // ── Fase 1: registro secuencial de vínculos (rotación CAS) ──────────────

    @Test
    void guardar_permite_nuevo_vinculo_si_anterior_esta_cesado() {
        stubCalculo("4864.19");
        EmpleadoPlanilla cesado = new EmpleadoPlanilla();
        cesado.setActivo(1);
        cesado.setFechaCese(LocalDate.of(2026, 6, 30));
        when(repository.findByEmpleadoIdAndActivo(EMPLEADO_ID, 1)).thenReturn(List.of(cesado));

        EmpleadoPlanillaDto dto = dtoBase();
        dto.setSueldoBasico(4864.19);
        dto.setFechaInicioContrato(LocalDate.of(2026, 7, 1));

        service.guardar(dto);

        verify(repository).save(any(EmpleadoPlanilla.class));
    }

    @Test
    void guardar_rechaza_nuevo_vinculo_si_existe_uno_vigente_sin_cese() {
        EmpleadoPlanilla vigente = new EmpleadoPlanilla();
        vigente.setActivo(1);
        vigente.setFechaCese(null);
        when(repository.findByEmpleadoIdAndActivo(EMPLEADO_ID, 1)).thenReturn(List.of(vigente));

        EmpleadoPlanillaDto dto = dtoBase();
        dto.setSueldoBasico(4864.19);

        assertThatThrownBy(() -> service.guardar(dto))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("contrato vigente");

        verify(repository, never()).save(any());
    }

    @Test
    void guardar_rechaza_nuevo_vinculo_que_inicia_antes_del_cese_anterior() {
        EmpleadoPlanilla cesado = new EmpleadoPlanilla();
        cesado.setActivo(1);
        cesado.setFechaCese(LocalDate.of(2026, 6, 30));
        when(repository.findByEmpleadoIdAndActivo(EMPLEADO_ID, 1)).thenReturn(List.of(cesado));

        EmpleadoPlanillaDto dto = dtoBase();
        dto.setSueldoBasico(4864.19);
        dto.setFechaInicioContrato(LocalDate.of(2026, 6, 15)); // anterior/igual al cese

        assertThatThrownBy(() -> service.guardar(dto))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("después del cese");

        verify(repository, never()).save(any());
    }

    private void stubCalculo(String remuneracionMensual) {
        when(incrementosDsCalculoService.calcular(
                        any(), any(), any(BigDecimal.class), any(LocalDate.class)))
                .thenAnswer(inv -> {
                    BigDecimal monto = inv.getArgument(2);
                    if (monto.compareTo(new BigDecimal("3000")) == 0) {
                        return IncrementosDsResponseDto.sinIncrementos(new BigDecimal("3000.00"));
                    }
                    return new IncrementosDsResponseDto(
                            true,
                            new BigDecimal("4500.00"),
                            List.of(),
                            new BigDecimal("364.19"),
                            new BigDecimal(remuneracionMensual));
                });
    }

    private static EmpleadoPlanillaDto dtoBase() {
        EmpleadoPlanillaDto dto = new EmpleadoPlanillaDto();
        dto.setEmpleadoId(EMPLEADO_ID);
        dto.setCodigoAirhsp("000051");
        dto.setMontoContrato(4500.0);
        dto.setRegimenLaboralId(10L);
        dto.setCondicionLaboralId(20L);
        dto.setTieneAsignacionFamiliar(1);
        dto.setNumHijos(1);
        return dto;
    }
}
