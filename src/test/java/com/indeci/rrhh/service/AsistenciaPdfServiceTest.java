package com.indeci.rrhh.service;

import com.indeci.rrhh.dto.AsistenciaDiaDto;
import com.indeci.rrhh.dto.AsistenciaResponseDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AsistenciaPdfServiceTest {

    @Mock private AsistenciaService asistenciaService;
    @Mock private PersonaService personaService;

    @InjectMocks private AsistenciaPdfService service;

    @Test
    void generar_produceUnPdfConDescuentos() {
        AsistenciaDiaDto tardanza = new AsistenciaDiaDto();
        tardanza.setDia(LocalDate.of(2026, 6, 5));
        tardanza.setDiaSemana("Vie");
        tardanza.setTipoDia("TARDANZA");
        tardanza.setMinutosTardanza(45);
        tardanza.setMarcaEntrada("08:45");
        tardanza.setMarcaSalida("17:00");
        tardanza.setHoraEntradaEsperada("08:00");

        AsistenciaResponseDto resp = new AsistenciaResponseDto();
        resp.setEmpleadoId(42L);
        resp.setPeriodo("2026-06");
        resp.setEstado("VALIDADA");
        resp.setRemuneracionBase(3000.0);
        resp.setDiasLaborados(1);
        resp.setDiasFalta(0);
        resp.setTotalMinTardanza(45);
        resp.setDescuentoTardanza(9.38);
        resp.setDescuentoFalta(0.0);
        resp.setDias(List.of(tardanza));

        when(asistenciaService.obtener(42L, "2026-06")).thenReturn(resp);
        when(personaService.listar()).thenReturn(List.of());

        byte[] pdf = service.generar(42L, "2026-06");

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).startsWith("%PDF");
    }
}
