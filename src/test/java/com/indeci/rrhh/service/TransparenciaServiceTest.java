package com.indeci.rrhh.service;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.PersonaResumenDto;
import com.indeci.rrhh.dto.TransparenciaPeriodoDto;
import com.indeci.rrhh.dto.TransparenciaRemuneracionDto;
import com.indeci.rrhh.entity.EmpleadoPlanilla;
import com.indeci.rrhh.entity.MovimientoPlanilla;
import com.indeci.rrhh.entity.PeriodoPlanilla;
import com.indeci.rrhh.entity.RegimenLaboral;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.MovimientoPlanillaRepository;
import com.indeci.rrhh.repository.PeriodoPlanillaRepository;
import com.indeci.rrhh.repository.RegimenLaboralRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Spec 011 / B4 — Tests del servicio de transparencia (M10 / Ley 27806).
 *   - periodosPublicados filtra solo APROBADO / CERRADO
 *   - remuneraciones de período publicado → filas con nombre, régimen, bruto
 *   - remuneraciones de período no publicado (ABIERTO) → NegocioException
 *   - remuneraciones de período inexistente → NegocioException
 */
@ExtendWith(MockitoExtension.class)
class TransparenciaServiceTest {

    @Mock private PeriodoPlanillaRepository periodoRepository;
    @Mock private MovimientoPlanillaRepository movimientoRepository;
    @Mock private EmpleadoPlanillaRepository planillaRepository;
    @Mock private RegimenLaboralRepository regimenLaboralRepository;
    @Mock private PersonaService personaService;

    @InjectMocks private TransparenciaService service;

    private PeriodoPlanilla periodo(String clave, String estado) {
        PeriodoPlanilla p = new PeriodoPlanilla();
        p.setPeriodo(clave);
        p.setEstado(estado);
        p.setActivo(1);
        return p;
    }

    private MovimientoPlanilla mov(Long empleadoId, Double ingresos) {
        MovimientoPlanilla m = new MovimientoPlanilla();
        m.setEmpleadoId(empleadoId);
        m.setTotalIngresos(ingresos);
        m.setActivo(1);
        return m;
    }

    private PersonaResumenDto persona(Long empleadoId, String nombre) {
        PersonaResumenDto p = new PersonaResumenDto();
        p.setEmpleadoId(empleadoId);
        p.setNombreCompleto(nombre);
        return p;
    }

    @Test
    void periodosPublicados_filtra_solo_aprobado_y_cerrado() {
        when(periodoRepository.findByActivo(1)).thenReturn(List.of(
                periodo("2026-05", "ABIERTO"),
                periodo("2026-04", "EN_REVISION"),
                periodo("2026-03", "APROBADO"),
                periodo("2026-02", "CERRADO")));

        List<TransparenciaPeriodoDto> publicados = service.periodosPublicados();

        assertThat(publicados).hasSize(2);
        assertThat(publicados).extracting(TransparenciaPeriodoDto::getPeriodo)
                .containsExactly("2026-03", "2026-02"); // orden descendente
    }

    @Test
    void remuneraciones_de_periodo_publicado_devuelve_filas() {
        when(periodoRepository.findByPeriodoAndActivo("2026-03", 1))
                .thenReturn(Optional.of(periodo("2026-03", "APROBADO")));
        when(personaService.listar())
                .thenReturn(List.of(persona(1L, "Ana Lopez")));
        when(movimientoRepository.findByPeriodoAndActivo("2026-03", 1))
                .thenReturn(List.of(mov(1L, 3000.0)));
        EmpleadoPlanilla ep = new EmpleadoPlanilla();
        ep.setRegimenLaboralId(2L);
        when(planillaRepository.findFirstByEmpleadoIdAndActivo(1L, 1))
                .thenReturn(Optional.of(ep));
        RegimenLaboral reg = new RegimenLaboral();
        reg.setCodigo("276");
        when(regimenLaboralRepository.findById(2L)).thenReturn(Optional.of(reg));

        List<TransparenciaRemuneracionDto> filas = service.remuneraciones("2026-03");

        assertThat(filas).hasSize(1);
        assertThat(filas.get(0).getEmpleado()).isEqualTo("Ana Lopez");
        assertThat(filas.get(0).getRegimen()).isEqualTo("276");
        assertThat(filas.get(0).getRemuneracionBruta()).isEqualTo(3000.0);
    }

    @Test
    void remuneraciones_de_periodo_no_publicado_lanza_negocio() {
        when(periodoRepository.findByPeriodoAndActivo("2026-05", 1))
                .thenReturn(Optional.of(periodo("2026-05", "ABIERTO")));

        assertThatThrownBy(() -> service.remuneraciones("2026-05"))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("no está publicado");
    }

    @Test
    void remuneraciones_de_periodo_inexistente_lanza_negocio() {
        when(periodoRepository.findByPeriodoAndActivo("2099-01", 1))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.remuneraciones("2099-01"))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("no encontrado");
    }
}
