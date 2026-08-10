package com.indeci.rrhh.service;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.entity.PeriodoPlanilla;
import com.indeci.rrhh.repository.AsistenciaCabeceraRepository;
import com.indeci.rrhh.repository.PeriodoPlanillaRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * LEY-05 / hallazgo 2026-08-09: "Recalcular" (botón de Consulta Diaria) no debe poder
 * tocar la asistencia de un período con planilla ya CERRADO/APROBADO — quedaría
 * desincronizada del monto ya generado/pagado, sin aviso.
 */
@ExtendWith(MockitoExtension.class)
class AsistenciaImportServiceRecalcularTest {

    @Mock private PeriodoPlanillaRepository periodoRepository;
    @Mock private AsistenciaCabeceraRepository cabeceraRepository;
    @Mock private com.indeci.rrhh.service.asistencia.Turno24hReconciliadorService turno24hReconciliador;

    @InjectMocks private AsistenciaImportService service;

    private PeriodoPlanilla periodo(String estado) {
        PeriodoPlanilla p = new PeriodoPlanilla();
        p.setPeriodo("2026-07");
        p.setEstado(estado);
        return p;
    }

    @Test
    void recalcular_periodoCerrado_bloqueaDuroYNoTocaCabecera() {
        lenient().when(periodoRepository.findByPeriodoAndActivo("2026-07", 1))
                .thenReturn(Optional.of(periodo("CERRADO")));

        assertThatThrownBy(() -> service.recalcularAsistencia(42L, "2026-07"))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("CERRADO");

        verify(cabeceraRepository, never()).findByEmpleadoIdAndPeriodoAndActivo(42L, "2026-07", 1);
    }

    @Test
    void recalcular_periodoAprobado_bloqueaDuro() {
        lenient().when(periodoRepository.findByPeriodoAndActivo("2026-07", 1))
                .thenReturn(Optional.of(periodo("APROBADO")));

        assertThatThrownBy(() -> service.recalcularAsistencia(42L, "2026-07"))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("APROBADO");
    }

    @Test
    void recalcular_periodoAbierto_noBloquea_siguePorFaltaDeCabecera() {
        // Periodo VALIDADA (no CERRADO/APROBADO): el guard deja pasar y llega hasta la
        // búsqueda real de la cabecera — se prueba con "no hay asistencia" como señal de
        // que el guard normativo no interrumpió el flujo feliz.
        lenient().when(periodoRepository.findByPeriodoAndActivo("2026-07", 1))
                .thenReturn(Optional.of(periodo("VALIDADA")));
        lenient().when(cabeceraRepository.findByEmpleadoIdAndPeriodoAndActivo(42L, "2026-07", 1))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.recalcularAsistencia(42L, "2026-07"))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("No hay asistencia registrada");
    }
}
