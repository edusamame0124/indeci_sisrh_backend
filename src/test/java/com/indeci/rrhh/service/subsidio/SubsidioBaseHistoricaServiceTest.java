package com.indeci.rrhh.service.subsidio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.indeci.rrhh.entity.MovimientoPlanilla;
import com.indeci.rrhh.entity.SubsidioBaseHistorica;
import com.indeci.rrhh.entity.SubsidioCaso;
import com.indeci.rrhh.entity.SubsidioReglaVigencia;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.MovimientoPlanillaRepository;
import com.indeci.rrhh.repository.SubsidioBaseDetalleRepository;
import com.indeci.rrhh.repository.SubsidioBaseHistoricaRepository;
import com.indeci.rrhh.repository.SubsidioCasoRepository;

@ExtendWith(MockitoExtension.class)
class SubsidioBaseHistoricaServiceTest {

    private static final Long CASO_ID = 10L;
    private static final Long EMPLEADO_ID = 41L;

    @Mock private SubsidioCasoRepository casoRepository;
    @Mock private SubsidioBaseHistoricaRepository baseRepository;
    @Mock private SubsidioBaseDetalleRepository detalleRepository;
    @Mock private MovimientoPlanillaRepository movimientoRepository;
    @Mock private SubsidioParametroResolverService parametroResolver;
    @Mock private SubsidioReglaResolverService reglaResolver;
    @Mock private EmpleadoPlanillaRepository planillaRepository;

    private SubsidioBaseHistoricaService service;

    @BeforeEach
    void setUp() {
        service = new SubsidioBaseHistoricaService(
                casoRepository,
                baseRepository,
                detalleRepository,
                movimientoRepository,
                parametroResolver,
                reglaResolver,
                planillaRepository,
                new ObjectMapper());
    }

    @Test
    void calcula_base_desde_12_meses_planilla_topados() {
        SubsidioCaso caso = caso();
        when(casoRepository.findByIdAndActivo(CASO_ID, 1)).thenReturn(Optional.of(caso));
        when(reglaResolver.resolverVigente(any())).thenReturn(regla());
        when(parametroResolver.mapaNumerico(any(), anyInt())).thenReturn(Map.of(
                "TOPE_MENSUAL", new BigDecimal("2475.00"),
                "DIVISOR_PROMEDIO", new BigDecimal("360")));
        when(parametroResolver.obtenerNumerico(eq("DIVISOR_PROMEDIO"), any(), anyInt()))
                .thenReturn(new BigDecimal("360"));
        when(baseRepository.findByCasoIdOrderByVersionBaseDesc(CASO_ID)).thenReturn(List.of());
        when(movimientoRepository.findByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(List.of(mov("2025-05", 3000.0), mov("2025-06", 5000.0)));
        when(baseRepository.save(any())).thenAnswer(inv -> {
            SubsidioBaseHistorica b = inv.getArgument(0);
            b.setId(99L);
            return b;
        });

        SubsidioBaseHistorica base = service.calcular(CASO_ID);

        assertThat(base.getBaseReconocida()).isEqualByComparingTo("4950.00");
        assertThat(base.getFuente()).isEqualTo("PLANILLA");
        assertThat(base.getMesesEvaluados()).isEqualTo(2);
    }

    @Test
    void afiliacion_corta_usa_fallback_uniforme() {
        SubsidioCaso caso = caso();
        when(casoRepository.findByIdAndActivo(CASO_ID, 1)).thenReturn(Optional.of(caso));
        when(reglaResolver.resolverVigente(any())).thenReturn(regla());
        when(parametroResolver.mapaNumerico(any(), anyInt())).thenReturn(Map.of(
                "TOPE_MENSUAL", new BigDecimal("2475.00"),
                "DIVISOR_PROMEDIO", new BigDecimal("360")));
        when(parametroResolver.obtenerNumerico(eq("DIVISOR_PROMEDIO"), any(), anyInt()))
                .thenReturn(new BigDecimal("360"));
        when(baseRepository.findByCasoIdOrderByVersionBaseDesc(CASO_ID)).thenReturn(List.of());
        when(movimientoRepository.findByEmpleadoIdAndActivo(EMPLEADO_ID, 1)).thenReturn(List.of());
        when(planillaRepository.findByEmpleadoIdAndActivo(EMPLEADO_ID, 1)).thenReturn(List.of());
        when(baseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SubsidioBaseHistorica base = service.calcular(CASO_ID);

        assertThat(base.getFuente()).isEqualTo("PARAMETRO");
        assertThat(base.getMesesEvaluados()).isEqualTo(12);
    }

    private static SubsidioCaso caso() {
        SubsidioCaso c = new SubsidioCaso();
        c.setId(CASO_ID);
        c.setEmpleadoId(EMPLEADO_ID);
        c.setFechaInicio(LocalDate.of(2026, 5, 1));
        c.setFechaContingencia(LocalDate.of(2026, 5, 1));
        return c;
    }

    private static SubsidioReglaVigencia regla() {
        SubsidioReglaVigencia r = new SubsidioReglaVigencia();
        r.setId(1L);
        return r;
    }

    private static MovimientoPlanilla mov(String periodo, double ingresos) {
        MovimientoPlanilla m = new MovimientoPlanilla();
        m.setId(1L);
        m.setPeriodo(periodo);
        m.setTotalIngresos(ingresos);
        return m;
    }
}
