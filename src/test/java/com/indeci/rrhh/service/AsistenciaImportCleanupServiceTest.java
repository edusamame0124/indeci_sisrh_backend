package com.indeci.rrhh.service;

import com.indeci.rrhh.repository.AsistenciaImportacionFilaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Data Lifecycle — purga por lotes de INDECI_ASISTENCIA_IMPORTACION_FILA.
 *   - caso feliz: hay filas vencidas -> se borran y se retorna la cantidad.
 *   - caso de borde: sin filas vencidas -> no se llama a eliminarPorIds.
 *   - caso de error normativo: N/A (operación de mantenimiento, no de negocio).
 */
@ExtendWith(MockitoExtension.class)
class AsistenciaImportCleanupServiceTest {

    @Mock
    private AsistenciaImportacionFilaRepository filaRepository;

    @InjectMocks
    private AsistenciaImportCleanupService service;

    private static final LocalDateTime CORTE = LocalDateTime.of(2026, 1, 1, 2, 0);

    @Test
    void purgarLote_con_filas_vencidas_las_elimina_y_retorna_cantidad() {
        ReflectionTestUtils.setField(service, "tamanoLote", 500);
        List<Long> ids = List.of(1L, 2L, 3L);
        when(filaRepository.buscarIdsAnterioresA(eq(CORTE), any())).thenReturn(ids);
        when(filaRepository.eliminarPorIds(ids)).thenReturn(3);

        int eliminadas = service.purgarLote(CORTE);

        assertThat(eliminadas).isEqualTo(3);
        verify(filaRepository).eliminarPorIds(ids);
    }

    @Test
    void purgarLote_sin_filas_vencidas_no_elimina_nada() {
        ReflectionTestUtils.setField(service, "tamanoLote", 500);
        when(filaRepository.buscarIdsAnterioresA(eq(CORTE), any())).thenReturn(Collections.emptyList());

        int eliminadas = service.purgarLote(CORTE);

        assertThat(eliminadas).isZero();
        verify(filaRepository, never()).eliminarPorIds(any());
    }
}
