package com.indeci.rrhh.job;

import com.indeci.rrhh.service.AsistenciaImportCleanupService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Data Lifecycle — orquestación del job mensual de purga.
 *   - caso feliz: agota los lotes hasta que un lote devuelve 0.
 *   - caso de borde: no hay nada vencido -> un solo intento, sin bucle.
 *   - caso de error normativo: tope de seguridad detiene el bucle si el
 *     purgado nunca llega a 0 (evita loop infinito / job colgado toda la noche).
 */
@ExtendWith(MockitoExtension.class)
class AsistenciaImportCleanupJobTest {

    @Mock
    private AsistenciaImportCleanupService cleanupService;

    @InjectMocks
    private AsistenciaImportCleanupJob job;

    @Test
    void purgarFilasVencidas_agota_lotes_hasta_que_no_quedan_filas() {
        ReflectionTestUtils.setField(job, "retencionMeses", 6);
        ReflectionTestUtils.setField(job, "maxLotesPorEjecucion", 20_000);
        when(cleanupService.purgarLote(any())).thenReturn(500, 500, 120, 0);

        job.purgarFilasVencidas();

        verify(cleanupService, times(4)).purgarLote(any());
    }

    @Test
    void purgarFilasVencidas_sin_filas_vencidas_ejecuta_un_solo_lote() {
        ReflectionTestUtils.setField(job, "retencionMeses", 6);
        ReflectionTestUtils.setField(job, "maxLotesPorEjecucion", 20_000);
        when(cleanupService.purgarLote(any())).thenReturn(0);

        job.purgarFilasVencidas();

        verify(cleanupService, times(1)).purgarLote(any());
    }

    @Test
    void purgarFilasVencidas_respeta_tope_de_seguridad_si_nunca_llega_a_cero() {
        ReflectionTestUtils.setField(job, "retencionMeses", 6);
        ReflectionTestUtils.setField(job, "maxLotesPorEjecucion", 3);
        when(cleanupService.purgarLote(any())).thenReturn(500);

        job.purgarFilasVencidas();

        verify(cleanupService, times(3)).purgarLote(any());
    }
}
