package com.indeci.rrhh.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.indeci.audit.context.AuditoriaContext;
import com.indeci.rrhh.entity.AsistenciaImportacion;
import com.indeci.rrhh.entity.PeriodoPlanilla;
import com.indeci.rrhh.repository.AsistenciaCabeceraRepository;
import com.indeci.rrhh.repository.AsistenciaImportacionFilaRepository;
import com.indeci.rrhh.repository.AsistenciaImportacionRepository;
import com.indeci.rrhh.repository.PeriodoPlanillaRepository;
import com.indeci.rrhh.service.asistencia.AsistenciaCsvParser;
import com.indeci.rrhh.service.asistencia.AsistenciaCsvValidator;
import com.indeci.rrhh.service.asistencia.AsistenciaImportErroresCsvWriter;
import com.indeci.rrhh.service.asistencia.BaseAsistenciaResolver;
import com.indeci.rrhh.service.asistencia.MarcadorCsvRow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AsistenciaImportServicePreviewTest {

    private static final Path ARCHIVO_GENERADO =
            Path.of("./data/asistencia/import/123_asistencia.csv");

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

    @AfterEach
    void limpiarArchivoGenerado() throws Exception {
        Files.deleteIfExists(ARCHIVO_GENERADO);
    }

    @Test
    void previewInicializaContadoresAntesDelPrimerGuardado() throws Exception {
        PeriodoPlanilla periodo = new PeriodoPlanilla();
        periodo.setPeriodo("2026-06");
        periodo.setFechaInicio(LocalDate.of(2026, 6, 1));
        periodo.setFechaFin(LocalDate.of(2026, 6, 30));

        MarcadorCsvRow row = new MarcadorCsvRow();
        row.setNumeroFila(2);
        row.setDni("12345678");
        row.setFecha(LocalDate.of(2026, 6, 1));
        row.setMarca1("08:00");
        row.setMarca2("17:00");

        AsistenciaCsvParser.ParseResult parseResult = new AsistenciaCsvParser.ParseResult();
        parseResult.setEncoding("UTF-8");
        parseResult.setFilas(List.of(row));

        AtomicInteger saveCall = new AtomicInteger();
        when(periodoRepository.findByPeriodoAndActivo("2026-06", 1))
                .thenReturn(Optional.of(periodo));
        when(csvParser.parse(any())).thenReturn(parseResult);
        when(importacionRepository.save(any(AsistenciaImportacion.class))).thenAnswer(invocation -> {
            AsistenciaImportacion importacion = invocation.getArgument(0);
            if (saveCall.incrementAndGet() == 1) {
                assertThat(importacion.getFilasTotal()).isEqualTo(1);
                assertThat(importacion.getFilasValidas()).isZero();
                assertThat(importacion.getFilasError()).isZero();
                assertThat(importacion.getFilasObservadas()).isZero();
                assertThat(importacion.getEmpleadosProcesados()).isZero();
            }
            importacion.setId(123L);
            return importacion;
        });
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        MockMultipartFile archivo = new MockMultipartFile(
                "archivo",
                "asistencia.csv",
                "text/csv",
                "DNI;FECHA;MARCA1;MARCA2\n12345678;01/06/2026;08:00;17:00".getBytes());

        service.preview("2026-06", archivo);

        assertThat(saveCall).hasValue(3);
    }

    @Test
    void previewNoPersisteResultadoJsonCuandoExcedeLimiteOracle() throws Exception {
        PeriodoPlanilla periodo = new PeriodoPlanilla();
        periodo.setPeriodo("2026-06");
        periodo.setFechaInicio(LocalDate.of(2026, 6, 1));
        periodo.setFechaFin(LocalDate.of(2026, 6, 30));

        MarcadorCsvRow row = new MarcadorCsvRow();
        row.setNumeroFila(2);
        row.setDni("12345678");
        row.setFecha(LocalDate.of(2026, 6, 1));
        row.setMarca1("08:00");
        row.setMarca2("17:00");

        AsistenciaCsvParser.ParseResult parseResult = new AsistenciaCsvParser.ParseResult();
        parseResult.setEncoding("UTF-8");
        parseResult.setFilas(List.of(row));

        AtomicInteger saveCall = new AtomicInteger();
        when(periodoRepository.findByPeriodoAndActivo("2026-06", 1))
                .thenReturn(Optional.of(periodo));
        when(csvParser.parse(any())).thenReturn(parseResult);
        when(importacionRepository.save(any(AsistenciaImportacion.class))).thenAnswer(invocation -> {
            AsistenciaImportacion importacion = invocation.getArgument(0);
            int call = saveCall.incrementAndGet();
            importacion.setId(123L);
            if (call == 3) {
                assertThat(importacion.getResultadoJson()).isNull();
            }
            return importacion;
        });
        when(objectMapper.writeValueAsString(any()))
                .thenReturn("{}", "x".repeat(4001));

        MockMultipartFile archivo = new MockMultipartFile(
                "archivo",
                "asistencia.csv",
                "text/csv",
                "DNI;FECHA;MARCA1;MARCA2\n12345678;01/06/2026;08:00;17:00".getBytes());

        service.preview("2026-06", archivo);

        assertThat(saveCall).hasValue(3);
    }

    @Test
    void previewSeparaValidasAdvertenciasObservadasYErrores() throws Exception {
        PeriodoPlanilla periodo = new PeriodoPlanilla();
        periodo.setPeriodo("2026-06");
        periodo.setFechaInicio(LocalDate.of(2026, 6, 1));
        periodo.setFechaFin(LocalDate.of(2026, 6, 30));

        MarcadorCsvRow valida = fila(2, "VALIDA");
        MarcadorCsvRow warn = fila(3, "WARN");
        warn.getAdvertencias().add("El nombre del marcador difiere del registrado en el sistema.");
        MarcadorCsvRow observada = fila(4, "OBSERVADA");
        observada.setObservacion("Marca Incompleta");
        MarcadorCsvRow error = fila(5, "ERROR");
        error.getErrores().add("Empleado no registrado en el sistema (DNI no encontrado).");

        AsistenciaCsvParser.ParseResult parseResult = new AsistenciaCsvParser.ParseResult();
        parseResult.setEncoding("UTF-8");
        parseResult.setFilas(List.of(valida, warn, observada, error));

        when(periodoRepository.findByPeriodoAndActivo("2026-06", 1))
                .thenReturn(Optional.of(periodo));
        when(csvParser.parse(any())).thenReturn(parseResult);
        when(importacionRepository.save(any(AsistenciaImportacion.class))).thenAnswer(invocation -> {
            AsistenciaImportacion importacion = invocation.getArgument(0);
            importacion.setId(123L);
            return importacion;
        });
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        MockMultipartFile archivo = new MockMultipartFile(
                "archivo",
                "asistencia.csv",
                "text/csv",
                "DNI;FECHA;MARCA1;MARCA2\n12345678;01/06/2026;08:00;17:00".getBytes());

        var result = service.preview("2026-06", archivo);

        assertThat(result.getFilasTotal()).isEqualTo(4);
        assertThat(result.getFilasValidas()).isEqualTo(2);
        assertThat(result.getFilasValidasLimpias()).isEqualTo(1);
        assertThat(result.getFilasAdvertencia()).isEqualTo(1);
        assertThat(result.getFilasObservadas()).isEqualTo(1);
        assertThat(result.getFilasError()).isEqualTo(1);
        assertThat(result.getEmpleadosConError()).isEqualTo(1);
        assertThat(result.getErrores()).hasSize(4);
        assertThat(result.getErrores().get(0).getSeveridad()).isEqualTo("VALIDA");
        assertThat(result.getErrores().get(0).getMensaje()).contains("sin incidencias detectadas");
        assertThat(result.getErrores().get(1).getSeveridad()).isEqualTo("WARN");
        assertThat(result.getErrores().get(2).getSeveridad()).isEqualTo("OBSERVADA");
        assertThat(result.getErrores().get(3).getSeveridad()).isEqualTo("ERROR");
    }

    private MarcadorCsvRow fila(int numeroFila, String estado) {
        MarcadorCsvRow row = new MarcadorCsvRow();
        row.setNumeroFila(numeroFila);
        row.setDni("12345678");
        row.setFecha(LocalDate.of(2026, 6, 1));
        row.setMarca1("08:00");
        row.setMarca2("17:00");
        row.setEstadoFila(estado);
        return row;
    }
}
