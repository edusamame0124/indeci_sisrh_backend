package com.indeci.rrhh.service.evento.importacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.indeci.rrhh.service.evento.importacion.EventoHistoricoImportDtos.Estado;
import com.indeci.rrhh.service.evento.importacion.EventoHistoricoImportDtos.FilaResultadoDto;
import com.indeci.rrhh.service.evento.importacion.EventoHistoricoImportDtos.ImportResultDto;

/**
 * V012_42 F2 — {@code EventoHistoricoImportService}: orquesta el import fila por fila, aísla
 * fallas (una fila mala no bloquea a las demás) y reporta el progreso.
 */
@ExtendWith(MockitoExtension.class)
class EventoHistoricoImportServiceTest {

    @Mock private EventoHistoricoSheetReader reader;
    @Mock private EventoHistoricoRowProcessor processor;

    @InjectMocks private EventoHistoricoImportService service;

    private EventoHistoricoRowRaw fila(int numero) {
        return new EventoHistoricoRowRaw(numero);
    }

    @Test
    void agrega_conteos_por_estado_y_reporta_progreso_creciente() {
        List<EventoHistoricoRowRaw> filas = List.of(fila(2), fila(3), fila(4), fila(5));
        when(reader.leer(any())).thenReturn(filas);
        when(processor.procesar(filas.get(0)))
                .thenReturn(new FilaResultadoDto(2, "1", "A", "LICENCIA SIN GOCE", Estado.OK, null));
        when(processor.procesar(filas.get(1)))
                .thenReturn(new FilaResultadoDto(3, "2", "B", "LICENCIA SIN GOCE", Estado.DUPLICADO_OMITIDO, "ya existe"));
        when(processor.procesar(filas.get(2)))
                .thenReturn(new FilaResultadoDto(4, "3", "C", "VACACIONES", Estado.ERROR, "motivo sin mapeo"));
        when(processor.procesar(filas.get(3)))
                .thenReturn(new FilaResultadoDto(5, "4", "D", "LICENCIA SIN GOCE", Estado.OK, null));

        List<Integer> progresos = new ArrayList<>();
        ImportResultDto resultado = service.importar(new byte[0], (pct, fase) -> progresos.add(pct));

        assertThat(resultado.total()).isEqualTo(4);
        assertThat(resultado.insertados()).isEqualTo(2);
        assertThat(resultado.duplicadosOmitidos()).isEqualTo(1);
        assertThat(resultado.rechazados()).isEqualTo(1);
        assertThat(resultado.filas()).hasSize(4);
        // Progreso monotónico y termina en 100.
        assertThat(progresos).isSorted();
        assertThat(progresos.get(progresos.size() - 1)).isEqualTo(100);
    }

    @Test
    void fila_que_lanza_excepcion_no_capturada_se_reporta_como_error_y_continua() {
        EventoHistoricoRowRaw filaMala = fila(7);
        filaMala.put(EventoHistoricoColumna.DNI, "12345678");
        List<EventoHistoricoRowRaw> filas = List.of(filaMala);
        when(reader.leer(any())).thenReturn(filas);
        when(processor.procesar(filaMala))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("constraint"));

        ImportResultDto resultado = service.importar(new byte[0], null);

        assertThat(resultado.total()).isEqualTo(1);
        assertThat(resultado.rechazados()).isEqualTo(1);
        assertThat(resultado.filas().get(0).estado()).isEqualTo(Estado.ERROR);
        assertThat(resultado.filas().get(0).mensaje()).contains("No se pudo guardar");
    }

    @Test
    void archivo_sin_filas_devuelve_resultado_vacio_sin_llamar_processor() {
        when(reader.leer(any())).thenReturn(List.of());

        ImportResultDto resultado = service.importar(new byte[0], null);

        assertThat(resultado.total()).isZero();
        assertThat(resultado.insertados()).isZero();
        assertThat(resultado.filas()).isEmpty();
    }
}
