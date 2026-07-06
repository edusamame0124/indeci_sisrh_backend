package com.indeci.rrhh.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.GenerarPlanillaCabeceraDto;
import com.indeci.rrhh.entity.EmpleadoPlanilla;
import com.indeci.rrhh.entity.PeriodoPlanilla;
import com.indeci.rrhh.entity.PlanillaLote;
import com.indeci.rrhh.entity.RegimenLaboral;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.EmpleadoPuestoRepository;
import com.indeci.rrhh.repository.EmpleadoRepository;
import com.indeci.rrhh.repository.MovimientoPlanillaRepository;
import com.indeci.rrhh.repository.PeriodoPlanillaRepository;
import com.indeci.rrhh.repository.PlanillaLoteRepository;

/** Track B F1 — tope parametrizado de planillas adicionales. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlanillaLoteServiceTest {

    private static final String PERIODO = "2026-05";

    @Mock private PeriodoPlanillaRepository periodoPlanillaRepository;
    @Mock private EmpleadoPlanillaRepository empleadoPlanillaRepository;
    @Mock private MovimientoPlanillaRepository movimientoPlanillaRepository;
    @Mock private EmpleadoPuestoRepository empleadoPuestoRepository;
    @Mock private EmpleadoRepository empleadoRepository;
    @Mock private GeneradorPlanillaService generadorPlanillaService;
    @Mock private PlanillaLoteRepository planillaLoteRepository;

    @InjectMocks private PlanillaLoteService service;

    @BeforeEach
    void topeEnTres() {
        ReflectionTestUtils.setField(service, "maxPlanillasAdicionales", 3);
    }

    @Test
    void generarLoteAdicional_rechaza_cuando_supera_el_maximo() {
        PeriodoPlanilla periodo = new PeriodoPlanilla();
        periodo.setPeriodo(PERIODO);
        periodo.setActivo(1);
        when(periodoPlanillaRepository.findByPeriodoAndActivo(PERIODO, 1))
                .thenReturn(Optional.of(periodo));
        when(movimientoPlanillaRepository.findByPeriodoAndActivo(PERIODO, 1))
                .thenReturn(List.of());

        RegimenLaboral cas = new RegimenLaboral();
        cas.setCodigo("CAS");
        EmpleadoPlanilla ep = new EmpleadoPlanilla();
        ep.setEmpleadoId(1L);
        ep.setRegimenLaboral(cas);
        when(empleadoPlanillaRepository.findByActivo(1)).thenReturn(List.of(ep));

        // Ya existen 3 adicionales → el siguiente sería el 4.º, por encima del tope.
        when(planillaLoteRepository.findMaxCorrelativo(PERIODO, "CAS", "ADICIONAL"))
                .thenReturn(3);

        GenerarPlanillaCabeceraDto request = new GenerarPlanillaCabeceraDto();
        request.setPeriodo(PERIODO);
        request.setEmpleadosIds(List.of(1L));

        assertThatThrownBy(() -> service.generarLoteAdicional(request))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("máximo de planillas adicionales");

        verify(planillaLoteRepository, never()).save(any(PlanillaLote.class));
    }
}
