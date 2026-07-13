package com.indeci.rrhh.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.SolicitudVacacionDetDto;

/**
 * Art. 34 D.S. 013-2019-PCM — blindaje backend del cómputo del descanso vacacional.
 * base = (Hasta − Desde) + 1; +2 si INICIA o CONCLUYE en viernes (una sola vez, sin +4);
 * tope de 30 días calendario. El servidor recalcula y rechaza los días manipulados por el
 * cliente. Fechas ancla 2026 (2026-07-11 = sábado): 07-06 lunes … 07-10 viernes.
 */
@ExtendWith(MockitoExtension.class)
class SolicitudRrhhArt34ValidacionTest {

    @InjectMocks
    private SolicitudRrhhService service;

    private SolicitudVacacionDetDto det(String tipo, LocalDate desde, LocalDate hasta, Integer totalDias) {
        SolicitudVacacionDetDto d = new SolicitudVacacionDetDto();
        d.setTipo(tipo);
        d.setFechaInicio(desde);
        d.setFechaFin(hasta);
        d.setTotalDias(totalDias != null ? totalDias.doubleValue() : null);
        return d;
    }

    @Test
    @DisplayName("Sin viernes, días correctos → no lanza")
    void sinViernes_correcto() {
        // Lun 06 → Mié 08 = 3 días, ningún extremo viernes.
        var det = det("PROGRAMACION", LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 8), 3);
        assertDoesNotThrow(() -> service.validarDiasArt34(List.of(det)));
    }

    @Test
    @DisplayName("Concluye viernes → +2, días correctos (7) → no lanza")
    void concluyeViernes_masDos() {
        // Lun 06 → Vie 10 = base 5, +2 viernes = 7.
        var det = det("PROGRAMACION", LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 10), 7);
        assertDoesNotThrow(() -> service.validarDiasArt34(List.of(det)));
    }

    @Test
    @DisplayName("Mismo viernes (1 día) → +2 una sola vez = 3, NO 5 (sin +4)")
    void mismoViernes_sinDuplicar() {
        var det = det("PROGRAMACION", LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 10), 3);
        assertDoesNotThrow(() -> service.validarDiasArt34(List.of(det)));
        // Con 5 (si se hubiera duplicado el +2) debe rechazar.
        var detDuplicado = det("PROGRAMACION", LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 10), 5);
        assertThrows(NegocioException.class, () -> service.validarDiasArt34(List.of(detDuplicado)));
    }

    @Test
    @DisplayName("Cliente omite el +2 del viernes (envía 5 en vez de 7) → NegocioException Art. 34")
    void diasManipulados_rechaza() {
        var det = det("PROGRAMACION", LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 10), 5);
        NegocioException ex = assertThrows(NegocioException.class,
                () -> service.validarDiasArt34(List.of(det)));
        assertTrue(ex.getMessage().contains("Art. 34"));
    }

    @Test
    @DisplayName("Supera 30 días calendario → NegocioException tope legal")
    void superaMaximoLegal_rechaza() {
        // Lun 06/07 → 05/08 = 31 días base (> 30) aun sin el +2.
        var det = det("PROGRAMACION", LocalDate.of(2026, 7, 6), LocalDate.of(2026, 8, 5), 31);
        NegocioException ex = assertThrows(NegocioException.class,
                () -> service.validarDiasArt34(List.of(det)));
        assertTrue(ex.getMessage().contains("30 días"));
    }

    @Test
    @DisplayName("Detalle _ACTUAL (histórico) se omite aunque los días no coincidan → no lanza")
    void detalleActual_seOmite() {
        // 999 días imposibles, pero por ser REPROG_ACTUAL no se recalcula.
        var det = det("REPROG_ACTUAL", LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 8), 999);
        assertDoesNotThrow(() -> service.validarDiasArt34(List.of(det)));
    }

    @Test
    @DisplayName("Fecha fin anterior a fecha inicio → NegocioException")
    void fechaFinAntesDeInicio_rechaza() {
        var det = det("PROGRAMACION", LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 6), 1);
        assertThrows(NegocioException.class, () -> service.validarDiasArt34(List.of(det)));
    }

    @Test
    @DisplayName("Lista nula → no lanza (sin detalles de vacación)")
    void listaNula_noLanza() {
        assertDoesNotThrow(() -> service.validarDiasArt34(null));
    }
}
