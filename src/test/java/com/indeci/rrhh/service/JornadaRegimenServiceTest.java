package com.indeci.rrhh.service;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.JornadaRegimenDto;
import com.indeci.rrhh.entity.JornadaRegimen;
import com.indeci.rrhh.entity.RegimenLaboral;
import com.indeci.rrhh.repository.JornadaRegimenRepository;
import com.indeci.rrhh.repository.RegimenLaboralRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JornadaRegimenServiceTest {

    @Mock private JornadaRegimenRepository jornadaRepository;
    @Mock private RegimenLaboralRepository regimenRepository;

    @InjectMocks private JornadaRegimenService service;

    private RegimenLaboral regimen() {
        RegimenLaboral r = new RegimenLaboral();
        r.setId(1L);
        r.setCodigo("276");
        r.setNombre("DECRETO LEGISLATIVO 276");
        return r;
    }

    private JornadaRegimenDto dtoValido() {
        JornadaRegimenDto dto = new JornadaRegimenDto();
        dto.setRegimenLaboralId(1L);
        dto.setHoraIngreso("08:00");
        dto.setHoraSalida("17:00");
        dto.setRefrigerioInicio("13:00");
        dto.setRefrigerioFin("14:00");
        dto.setToleranciaIngresoMin(5);
        dto.setToleranciaAlmuerzoMin(5);
        dto.setJornadaHoras(BigDecimal.valueOf(8));
        return dto;
    }

    @Test
    void obtener_sinConfig_devuelveValoresPorDefecto() {
        when(regimenRepository.findById(1L)).thenReturn(Optional.of(regimen()));
        when(jornadaRepository.findByRegimenLaboralId(1L)).thenReturn(Optional.empty());

        JornadaRegimenDto dto = service.obtener(1L);

        assertThat(dto.getRegimenLaboralId()).isEqualTo(1L);
        assertThat(dto.getRegimenCodigo()).isEqualTo("276");
        assertThat(dto.getJornadaHoras()).isEqualByComparingTo("8");
        assertThat(dto.getToleranciaIngresoMin()).isZero();
    }

    @Test
    void guardar_upsertCrea_yDevuelveConfig() {
        when(regimenRepository.findById(1L)).thenReturn(Optional.of(regimen()));
        when(jornadaRepository.findByRegimenLaboralId(1L)).thenReturn(Optional.empty());
        when(jornadaRepository.save(any(JornadaRegimen.class))).thenAnswer(inv -> {
            JornadaRegimen j = inv.getArgument(0);
            j.setId(99L);
            return j;
        });

        JornadaRegimenDto guardado = service.guardar(dtoValido());

        assertThat(guardado.getId()).isEqualTo(99L);
        assertThat(guardado.getHoraIngreso()).isEqualTo("08:00");
        assertThat(guardado.getToleranciaAlmuerzoMin()).isEqualTo(5);
    }

    @Test
    void guardar_salidaAntesDeIngreso_lanzaExcepcion() {
        when(regimenRepository.findById(1L)).thenReturn(Optional.of(regimen()));
        lenient().when(jornadaRepository.findByRegimenLaboralId(1L)).thenReturn(Optional.empty());

        JornadaRegimenDto dto = dtoValido();
        dto.setHoraSalida("07:00"); // antes del ingreso 08:00

        assertThatThrownBy(() -> service.guardar(dto))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("salida");
    }

    @Test
    void guardar_horaInvalida_lanzaExcepcion() {
        when(regimenRepository.findById(1L)).thenReturn(Optional.of(regimen()));

        JornadaRegimenDto dto = dtoValido();
        dto.setHoraIngreso("25:99");

        assertThatThrownBy(() -> service.guardar(dto))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("HH:mm");
    }

    @Test
    void guardar_sinHoraIngreso_lanzaExcepcion() {
        when(regimenRepository.findById(1L)).thenReturn(Optional.of(regimen()));
        JornadaRegimenDto dto = dtoValido();
        dto.setHoraIngreso(null);

        assertThatThrownBy(() -> service.guardar(dto))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("hora de ingreso es obligatoria");
    }

    @Test
    void guardar_sinHoraSalida_lanzaExcepcion() {
        when(regimenRepository.findById(1L)).thenReturn(Optional.of(regimen()));
        JornadaRegimenDto dto = dtoValido();
        dto.setHoraSalida("  ");

        assertThatThrownBy(() -> service.guardar(dto))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("hora de salida es obligatoria");
    }

    @Test
    void guardar_sinToleranciaIngreso_lanzaExcepcion() {
        when(regimenRepository.findById(1L)).thenReturn(Optional.of(regimen()));
        JornadaRegimenDto dto = dtoValido();
        dto.setToleranciaIngresoMin(null);

        assertThatThrownBy(() -> service.guardar(dto))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("tolerancia de ingreso es obligatoria");
    }

    @Test
    void guardar_sinRegimen_lanzaExcepcion() {
        JornadaRegimenDto dto = dtoValido();
        dto.setRegimenLaboralId(null);

        assertThatThrownBy(() -> service.guardar(dto))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("régimen");
    }
}
