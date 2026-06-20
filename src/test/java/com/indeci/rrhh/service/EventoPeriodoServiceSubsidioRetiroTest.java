package com.indeci.rrhh.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.indeci.audit.context.AuditoriaContext;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.EventoPeriodoDto;
import com.indeci.rrhh.entity.TipoEvento;
import com.indeci.rrhh.repository.EmpleadoEventoRepository;
import com.indeci.rrhh.repository.EmpleadoRepository;
import com.indeci.rrhh.repository.EventoDistribucionMesRepository;
import com.indeci.rrhh.repository.TipoEventoRepository;

@ExtendWith(MockitoExtension.class)
class EventoPeriodoServiceSubsidioRetiroTest {

    @Mock private EmpleadoEventoRepository repository;
    @Mock private EmpleadoRepository empleadoRepository;
    @Mock private TipoEventoRepository tipoRepository;
    @Mock private EventoDistribucionMesRepository distribucionRepository;
    @Mock private AuditoriaContext auditoriaContext;

    @InjectMocks private EventoPeriodoService service;

    @Test
    void crear_rechazaTipoGeneraSubsidio() {
        TipoEvento tipo = new TipoEvento();
        tipo.setId(1L);
        tipo.setCodigo("MATERNIDAD");
        tipo.setGeneraSubsidio("S");
        tipo.setActivo(1);
        when(tipoRepository.findById(1L)).thenReturn(Optional.of(tipo));

        EventoPeriodoDto dto = new EventoPeriodoDto();
        dto.setEmpleadoId(10L);
        dto.setTipoEventoId(1L);
        dto.setFechaInicio(java.time.LocalDate.of(2026, 5, 1));
        dto.setFechaFin(java.time.LocalDate.of(2026, 8, 6));

        assertThrows(NegocioException.class, () -> service.crear(dto));
    }
}
