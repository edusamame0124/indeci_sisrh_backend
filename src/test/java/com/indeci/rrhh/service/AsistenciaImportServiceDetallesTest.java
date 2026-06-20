package com.indeci.rrhh.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.indeci.audit.context.AuditoriaContext;
import com.indeci.rrhh.dto.AsistenciaImportFilaDetalleDto;
import com.indeci.rrhh.dto.AsistenciaImportResumenDto;
import com.indeci.rrhh.entity.AsistenciaImportacion;
import com.indeci.rrhh.entity.AsistenciaImportacionFila;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AsistenciaImportServiceDetallesTest {

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
    void detalles_mapeaColumnasYNormalizaFiltrosVacios() {
        AsistenciaImportacion imp = new AsistenciaImportacion();
        imp.setId(1L);
        when(importacionRepository.findById(1L)).thenReturn(Optional.of(imp));

        AsistenciaImportacionFila fila = new AsistenciaImportacionFila();
        fila.setId(10L);
        fila.setNumeroFila(2);
        fila.setEstadoFila("OBSERVADA");
        fila.setDni("12345678");
        fila.setNombreCsv("JUAN PEREZ");
        fila.setNombreSistema("JUAN PEREZ GARCIA");
        fila.setFecha(LocalDate.of(2026, 5, 10));
        fila.setMarca3("13:00");
        fila.setTardanzaMin(5);
        fila.setRefrigerioMin(60);
        fila.setHorasExtraTotalMin(0);
        fila.setMensajeValidacion("Fila observada");

        Page<AsistenciaImportacionFila> page = new PageImpl<>(List.of(fila));
        // filtros vacíos ("  ") deben llegar como null al repositorio
        when(filaRepository.buscarDetalle(eq(1L), isNull(), isNull(), isNull(), eq(true), any()))
                .thenReturn(page);

        Page<AsistenciaImportFilaDetalleDto> result =
                service.detalles(1L, "  ", "", null, true, PageRequest.of(0, 25));

        assertThat(result.getContent()).hasSize(1);
        AsistenciaImportFilaDetalleDto dto = result.getContent().get(0);
        assertThat(dto.getDni()).isEqualTo("12345678");
        assertThat(dto.getEstado()).isEqualTo("OBSERVADA");
        assertThat(dto.getEmpleadoSistema()).isEqualTo("JUAN PEREZ GARCIA");
        assertThat(dto.getMarca3()).isEqualTo("13:00");
        assertThat(dto.getTardanzaMin()).isEqualTo(5);
        assertThat(dto.getRefrigerioMin()).isEqualTo(60);
    }

    @Test
    void resumen_calculaDuplicadoHashPrevio() {
        AsistenciaImportacion imp = new AsistenciaImportacion();
        imp.setId(7L);
        imp.setNombreArchivo("mayo.csv");
        imp.setPeriodo("2026-05");
        imp.setHashSha256("abc123");
        imp.setFilasTotal(40);
        imp.setFilasValidas(35);
        imp.setFilasObservadas(3);
        imp.setFilasError(2);
        imp.setEmpleadosDetectados(20);
        imp.setEstado("BORRADOR_PREVIEW");
        when(importacionRepository.findById(7L)).thenReturn(Optional.of(imp));
        when(importacionRepository.existsByHashSha256AndIdNot("abc123", 7L)).thenReturn(true);

        AsistenciaImportResumenDto dto = service.resumen(7L);

        assertThat(dto.getFilasLeidas()).isEqualTo(40);
        assertThat(dto.getEmpleadosDetectados()).isEqualTo(20);
        assertThat(dto.isDuplicadoHashPrevio()).isTrue();
    }
}
