package com.indeci.rrhh.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.indeci.rrhh.entity.ExportArchivo;
import com.indeci.rrhh.repository.ExportArchivoRepository;

/**
 * B3 / M09 — Tests de ExportLogService (hash SHA-256 + conteo de líneas).
 */
@ExtendWith(MockitoExtension.class)
class ExportLogServiceTest {

    @Mock private ExportArchivoRepository repository;
    @InjectMocks private ExportLogService service;

    @Test
    void registrarCalculaHashYContarLineas() {
        when(repository.save(any(ExportArchivo.class))).thenAnswer(inv -> inv.getArgument(0));
        String contenido = "01|00256418|0601|2264.19|2264.19|\r\n01|00256418|0608|236.42|236.42|\r\n";

        ArgumentCaptor<ExportArchivo> captor = ArgumentCaptor.forClass(ExportArchivo.class);
        service.registrar("2026-03", "PLAME_REM", "060120260320135890031.rem",
                contenido, new BigDecimal("2264.19"), new BigDecimal("236.42"));

        org.mockito.Mockito.verify(repository).save(captor.capture());
        ExportArchivo saved = captor.getValue();
        assertThat(saved.getNroLineas()).isEqualTo(2);
        assertThat(saved.getHashSha256()).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(saved.getTipoArchivo()).isEqualTo("PLAME_REM");
    }

    @Test
    void sha256EsDeterministicoYConocido() {
        // SHA-256("abc") = ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
        assertThat(ExportLogService.sha256("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void contarLineasCuentaSaltos() {
        assertThat(ExportLogService.contarLineas("a\r\nb\r\n")).isEqualTo(2);
        assertThat(ExportLogService.contarLineas("")).isZero();
        assertThat(ExportLogService.contarLineas("sin salto")).isZero();
    }
}
