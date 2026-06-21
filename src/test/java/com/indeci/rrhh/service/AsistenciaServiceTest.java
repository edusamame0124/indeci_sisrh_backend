package com.indeci.rrhh.service;

import com.indeci.audit.context.AuditoriaContext;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.AsistenciaDiaDto;
import com.indeci.rrhh.dto.AsistenciaGuardarDto;
import com.indeci.rrhh.dto.AsistenciaResponseDto;
import com.indeci.rrhh.entity.AsistenciaCabecera;
import com.indeci.rrhh.repository.AsistenciaCabeceraRepository;
import com.indeci.rrhh.repository.AsistenciaDetalleRepository;
import com.indeci.rrhh.service.asistencia.BaseAsistenciaResolver;
import com.indeci.rrhh.service.asistencia.BaseAsistenciaResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Spec 010 / M04 — Tests del servicio de asistencia (SPEC §12.2 PANTALLA-02).
 *   - guardar feliz → recalcula agregados + descuento D.Leg. 276 Art. 24
 *   - guardar sin empleado/período → NegocioException (caso error normativo)
 *   - guardar tipo de día inválido → NegocioException (caso borde)
 *   - obtener inexistente → DTO vacío (id null)
 *   - descuento tardanza/falta — fórmula REGLA 276-02 verificada
 */
@ExtendWith(MockitoExtension.class)
class AsistenciaServiceTest {

    @Mock private AsistenciaCabeceraRepository cabeceraRepository;
    @Mock private AsistenciaDetalleRepository detalleRepository;
    @Mock private AuditoriaContext auditoriaContext;
    @Mock private BaseAsistenciaResolver baseResolver;

    @InjectMocks private AsistenciaService service;

    private static final Long EMPLEADO_ID = 42L;
    private static final String PERIODO = "2026-05";

    private AsistenciaDiaDto dia(String tipo, int minutos, int diaMes) {
        AsistenciaDiaDto d = new AsistenciaDiaDto();
        d.setDia(LocalDate.of(2026, 5, diaMes));
        d.setTipoDia(tipo);
        d.setMinutosTardanza(minutos);
        return d;
    }

    private AsistenciaGuardarDto dtoBase() {
        AsistenciaGuardarDto dto = new AsistenciaGuardarDto();
        dto.setEmpleadoId(EMPLEADO_ID);
        dto.setPeriodo(PERIODO);
        dto.setRemuneracionBase(3000.0);
        return dto;
    }

    @Test
    void guardar_caso_feliz_recalcula_agregados_y_descuentos() {
        AsistenciaGuardarDto dto = dtoBase();
        dto.setDias(List.of(
                dia("LABORAL", 0, 4),
                dia("TARDANZA", 45, 5),
                dia("FALTA", 0, 6)));

        when(cabeceraRepository.findByEmpleadoIdAndPeriodoAndActivo(
                EMPLEADO_ID, PERIODO, 1)).thenReturn(Optional.empty());
        when(cabeceraRepository.save(any(AsistenciaCabecera.class)))
                .thenAnswer(inv -> {
                    AsistenciaCabecera c = inv.getArgument(0);
                    c.setId(10L);
                    return c;
                });

        service.guardar(dto);

        ArgumentCaptor<AsistenciaCabecera> capt =
                ArgumentCaptor.forClass(AsistenciaCabecera.class);
        verify(cabeceraRepository).save(capt.capture());
        AsistenciaCabecera cab = capt.getValue();

        assertThat(cab.getDiasLaborados()).isEqualTo(2); // LABORAL + TARDANZA
        assertThat(cab.getDiasFalta()).isEqualTo(1);
        assertThat(cab.getTotalMinTardanza()).isEqualTo(45);
        // (3000/30/8/60) * 45 = 9.375 -> 9.38
        assertThat(cab.getDescuentoTardanza()).isEqualTo(9.38);
        // (3000/30) * 1 = 100.00
        assertThat(cab.getDescuentoFalta()).isEqualTo(100.00);
        assertThat(cab.getEstado()).isEqualTo("BORRADOR");

        verify(detalleRepository).deleteByCabeceraId(10L);
        verify(detalleRepository).saveAll(any());
    }

    @Test
    void guardarImportacion_primeraVez_creaVersion1_sinBorrarHistorico() {
        when(cabeceraRepository.findByEmpleadoIdAndPeriodoAndActivo(EMPLEADO_ID, PERIODO, 1))
                .thenReturn(Optional.empty());
        when(cabeceraRepository.maxVersion(EMPLEADO_ID, PERIODO)).thenReturn(null);
        when(cabeceraRepository.save(any(AsistenciaCabecera.class))).thenAnswer(inv -> {
            AsistenciaCabecera c = inv.getArgument(0);
            c.setId(11L);
            return c;
        });

        service.guardarImportacion(EMPLEADO_ID, PERIODO, 3000.0, "FALLBACK", "PREVALIDADA", 5L,
                List.of(dia("LABORAL", 0, 4)), null, null, null);

        ArgumentCaptor<AsistenciaCabecera> capt = ArgumentCaptor.forClass(AsistenciaCabecera.class);
        verify(cabeceraRepository).save(capt.capture());
        AsistenciaCabecera nueva = capt.getValue();
        assertThat(nueva.getVersion()).isEqualTo(1);
        assertThat(nueva.getActivo()).isEqualTo(1);
        assertThat(nueva.getMotivoRectificacion()).isNull();
        verify(detalleRepository).saveAll(any());
        // NO se borra detalle histórico (req 5)
        verify(detalleRepository, never()).deleteByCabeceraId(anyLong());
    }

    @Test
    void guardarImportacion_rectificacion_versiona_y_conserva_anterior() {
        AsistenciaCabecera anterior = new AsistenciaCabecera();
        anterior.setId(10L);
        anterior.setActivo(1);
        anterior.setEstado("VALIDADA");
        anterior.setVersion(1);
        when(cabeceraRepository.findByEmpleadoIdAndPeriodoAndActivo(EMPLEADO_ID, PERIODO, 1))
                .thenReturn(Optional.of(anterior));
        when(cabeceraRepository.maxVersion(EMPLEADO_ID, PERIODO)).thenReturn(1);
        when(cabeceraRepository.save(any(AsistenciaCabecera.class))).thenAnswer(inv -> {
            AsistenciaCabecera c = inv.getArgument(0);
            c.setId(11L);
            return c;
        });

        service.guardarImportacion(EMPLEADO_ID, PERIODO, 3000.0, "FALLBACK", "PREVALIDADA", 5L,
                List.of(dia("LABORAL", 0, 4)), "Marca manual validada", "rrhh", "jefe");

        // La anterior se conserva como ACTIVO=0 (req 4) y se desactiva ANTES (saveAndFlush).
        verify(cabeceraRepository).saveAndFlush(argThat(c ->
                c.getId().equals(10L) && c.getActivo() == 0));

        ArgumentCaptor<AsistenciaCabecera> capt = ArgumentCaptor.forClass(AsistenciaCabecera.class);
        verify(cabeceraRepository).save(capt.capture());
        AsistenciaCabecera nueva = capt.getValue();
        assertThat(nueva.getVersion()).isEqualTo(2);
        assertThat(nueva.getActivo()).isEqualTo(1);
        assertThat(nueva.getMotivoRectificacion()).isEqualTo("Marca manual validada");
        assertThat(nueva.getAutorizadoPor()).isEqualTo("jefe");
        // NO se borra detalle de versiones previas (req 5)
        verify(detalleRepository, never()).deleteByCabeceraId(anyLong());
    }

    @Test
    void guardar_sin_empleado_o_periodo_lanza_excepcion() {
        AsistenciaGuardarDto dto = new AsistenciaGuardarDto();
        dto.setPeriodo(PERIODO);

        assertThatThrownBy(() -> service.guardar(dto))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("obligatorios");
    }

    @Test
    void guardar_tipo_dia_invalido_lanza_excepcion() {
        AsistenciaGuardarDto dto = dtoBase();
        dto.setDias(List.of(dia("INVALIDO", 0, 1)));

        assertThatThrownBy(() -> service.guardar(dto))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("Tipo de día inválido");
    }

    @Test
    void obtener_inexistente_devuelve_dto_vacio() {
        when(cabeceraRepository.findByEmpleadoIdAndPeriodoAndActivo(
                EMPLEADO_ID, PERIODO, 1)).thenReturn(Optional.empty());
        BaseAsistenciaResult base = new BaseAsistenciaResult();
        base.setRemuneracionBase(0.0);
        when(baseResolver.resolver(EMPLEADO_ID)).thenReturn(base);

        AsistenciaResponseDto dto = service.obtener(EMPLEADO_ID, PERIODO);

        assertThat(dto.getId()).isNull();
        assertThat(dto.getEmpleadoId()).isEqualTo(EMPLEADO_ID);
        assertThat(dto.getPeriodo()).isEqualTo(PERIODO);
        assertThat(dto.getDias()).isEmpty();
        assertThat(dto.getDescuentoTardanza()).isZero();
    }

    @Test
    void descuento_aplica_formula_276_art24() {
        // ROUND((3000/30/8/60) * 120, 2) = 25.00
        assertThat(service.calcularDescuentoTardanza(3000.0, 120))
                .isEqualTo(25.00);
        // ROUND((3000/30) * 2, 2) = 200.00
        assertThat(service.calcularDescuentoFalta(3000.0, 2))
                .isEqualTo(200.00);
        // Bordes: sin minutos/faltas o sin remuneración => 0
        assertThat(service.calcularDescuentoTardanza(3000.0, 0)).isZero();
        assertThat(service.calcularDescuentoFalta(0.0, 5)).isZero();
    }
}
