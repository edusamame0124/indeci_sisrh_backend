package com.indeci.rrhh.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.indeci.audit.context.AuditoriaContext;
import com.indeci.rrhh.dto.AsistenciaValidacionBatchDto;
import com.indeci.rrhh.entity.AsistenciaCabecera;
import com.indeci.rrhh.entity.AsistenciaImportacion;
import com.indeci.rrhh.repository.AsistenciaCabeceraRepository;
import com.indeci.rrhh.repository.AsistenciaImportacionFilaRepository;
import com.indeci.rrhh.repository.AsistenciaImportacionRepository;
import com.indeci.rrhh.repository.PeriodoPlanillaRepository;
import com.indeci.rrhh.service.asistencia.AsistenciaCsvParser;
import com.indeci.rrhh.service.asistencia.AsistenciaCsvValidator;
import com.indeci.rrhh.service.asistencia.AsistenciaImportErroresCsvWriter;
import com.indeci.rrhh.service.asistencia.BaseAsistenciaResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AsistenciaImportServiceValidarCabecerasTest {

    @Mock private AsistenciaCsvParser csvParser;
    @Mock private AsistenciaCsvValidator csvValidator;
    @Mock private PeriodoPlanillaRepository periodoRepository;
    @Mock private AsistenciaCabeceraRepository cabeceraRepository;
    @Mock private AsistenciaImportacionRepository importacionRepository;
    @Mock private AsistenciaImportacionFilaRepository filaRepository;
    @Mock private BaseAsistenciaResolver baseResolver;
    @Mock private AsistenciaService asistenciaService;
    @Mock private AuditoriaContext auditoriaContext;
    @Mock private AsistenciaImportErroresCsvWriter erroresCsvWriter;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks private AsistenciaImportService service;

    @Test
    void validarCabeceras_soloValidaPrevalidadaYListaParaValidar() {
        AsistenciaImportacion importacion = new AsistenciaImportacion();
        importacion.setId(77L);
        importacion.setPeriodo("2026-06");

        AsistenciaCabecera prevalidada = cabecera("PREVALIDADA");
        AsistenciaCabecera lista = cabecera("LISTA_PARA_VALIDAR");
        AsistenciaCabecera observada = cabecera("OBSERVADA");
        AsistenciaCabecera validada = cabecera("VALIDADA");

        when(importacionRepository.findById(77L)).thenReturn(Optional.of(importacion));
        when(cabeceraRepository.findByImportacionIdAndActivo(77L, 1))
                .thenReturn(List.of(prevalidada, lista, observada, validada));

        AsistenciaValidacionBatchDto result = service.validarCabeceras(77L);

        assertThat(prevalidada.getEstado()).isEqualTo("VALIDADA");
        assertThat(lista.getEstado()).isEqualTo("VALIDADA");
        assertThat(observada.getEstado()).isEqualTo("OBSERVADA");
        assertThat(validada.getEstado()).isEqualTo("VALIDADA");
        assertThat(result.getTotalCabeceras()).isEqualTo(4);
        assertThat(result.getValidadas()).isEqualTo(2);
        assertThat(result.getObservadas()).isEqualTo(1);
        assertThat(result.getYaValidadas()).isEqualTo(1);
        verify(cabeceraRepository).saveAll(List.of(prevalidada, lista, observada, validada));
    }

    private AsistenciaCabecera cabecera(String estado) {
        AsistenciaCabecera cabecera = new AsistenciaCabecera();
        cabecera.setEstado(estado);
        cabecera.setActivo(1);
        return cabecera;
    }
}
