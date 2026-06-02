package com.indeci.rrhh.service;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.SubsidioCalculadoDto;
import com.indeci.rrhh.entity.EmpleadoEvento;
import com.indeci.rrhh.entity.MovimientoPlanilla;
import com.indeci.rrhh.entity.TipoEvento;
import com.indeci.rrhh.repository.MovimientoPlanillaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * F2.4 — Tests del cálculo de subsidios (maternidad/enfermedad).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubsidioCalculadorServiceTest {

    @Mock private MovimientoPlanillaRepository movimientoRepository;
    @InjectMocks private SubsidioCalculadorService service;

    private static final Long EMP_ID = 42L;

    // ======================================================================
    // Casos: no aplica.
    // ======================================================================

    @Test
    void noAplica_cuando_tipo_evento_es_null() {
        EmpleadoEvento e = new EmpleadoEvento();
        e.setTipoEvento(null);
        e.setEmpleadoId(EMP_ID);
        e.setFechaInicio(LocalDate.of(2026, 5, 1));
        e.setFechaFin(LocalDate.of(2026, 5, 30));

        SubsidioCalculadoDto r = service.calcular(e, new BigDecimal("3000.00"));
        assertThat(r.aplica()).isFalse();
        assertThat(r.subsidioEssalud()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void noAplica_cuando_tipo_genera_subsidio_N() {
        // Licencia con goce → genera_subsidio = N → no aplica.
        EmpleadoEvento e = eventoConTipo("LICENCIA_GOCE", "N",
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 10));

        SubsidioCalculadoDto r = service.calcular(e, new BigDecimal("3000.00"));
        assertThat(r.aplica()).isFalse();
    }

    @Test
    void lanza_si_evento_null() {
        assertThatThrownBy(() -> service.calcular(null, new BigDecimal("3000")))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("Evento nulo");
    }

    @Test
    void lanza_si_fechas_null() {
        EmpleadoEvento e = eventoConTipo("MATERNIDAD", "S", null, null);

        assertThatThrownBy(() -> service.calcular(e, new BigDecimal("3000")))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("Evento sin fechas");
    }

    @Test
    void lanza_si_fechaFin_anterior_a_fechaInicio() {
        EmpleadoEvento e = eventoConTipo("MATERNIDAD", "S",
                LocalDate.of(2026, 5, 30), LocalDate.of(2026, 5, 1));

        assertThatThrownBy(() -> service.calcular(e, new BigDecimal("3000")))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("fechaFin < fechaInicio");
    }

    @Test
    void lanza_si_remuneracion_invalida() {
        EmpleadoEvento e = eventoConTipo("MATERNIDAD", "S",
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 30));

        assertThatThrownBy(() -> service.calcular(e, BigDecimal.ZERO))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("Remuneración mensual base inválida");

        assertThatThrownBy(() -> service.calcular(e, null))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("Remuneración mensual base inválida");
    }

    // ======================================================================
    // Casos: maternidad / enfermedad — promedio 12 meses.
    // ======================================================================

    @Test
    void maternidad_30dias_con_12meses_de_historial_calcula_promedio_correcto() {
        // Historial: 12 movimientos de 3000.00 cada uno (mayo-2025 a abril-2026).
        when(movimientoRepository.findByEmpleadoIdAndActivo(EMP_ID, 1))
                .thenReturn(historialUniforme("3000.00", 12, YearMonthRef.de(2026, 5)));

        EmpleadoEvento e = eventoConTipo("MATERNIDAD", "S",
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 30));

        SubsidioCalculadoDto r = service.calcular(e, new BigDecimal("3000.00"));

        assertThat(r.aplica()).isTrue();
        assertThat(r.diasDescanso()).isEqualTo(30);
        assertThat(r.remuneracionDiaria()).isEqualByComparingTo("100.00"); // 3000/30
        assertThat(r.subtotalRemunerativo()).isEqualByComparingTo("3000.00"); // 30 × 100
        assertThat(r.promedioMensual12Meses()).isEqualByComparingTo("3000.00");
        assertThat(r.subsidioDiarioEssalud()).isEqualByComparingTo("100.00");
        assertThat(r.subsidioEssalud()).isEqualByComparingTo("3000"); // entero
        assertThat(r.diferenciaAsumidaIndeci()).isEqualByComparingTo("0.00");
    }

    @Test
    void enfermedad_15dias_promedio_alto_INDECI_no_asume_si_essalud_cubre_todo() {
        // Historial promedio = 4000 → subsidio diario 133.33 → 15 días = 2000.
        // Subtotal remunerativo con base 3000 = 1500. INDECI asume 1500-2000 = -500.
        // (Caso raro pero matemáticamente válido — EsSalud puede pagar más que
        // la remuneración actual si el empleado tuvo aumentos en últimos 12m.)
        when(movimientoRepository.findByEmpleadoIdAndActivo(EMP_ID, 1))
                .thenReturn(historialUniforme("4000.00", 12, YearMonthRef.de(2026, 5)));

        EmpleadoEvento e = eventoConTipo("ENFERMEDAD", "S",
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 15));

        SubsidioCalculadoDto r = service.calcular(e, new BigDecimal("3000.00"));

        assertThat(r.diasDescanso()).isEqualTo(15);
        assertThat(r.subtotalRemunerativo()).isEqualByComparingTo("1500.00"); // 15 × 100
        assertThat(r.promedioMensual12Meses()).isEqualByComparingTo("4000.00");
        assertThat(r.subsidioDiarioEssalud()).isEqualByComparingTo("133.33");
        assertThat(r.subsidioEssalud()).isEqualByComparingTo("2000"); // 15 × 133.33 = 1999.95 → 2000
        assertThat(r.diferenciaAsumidaIndeci()).isEqualByComparingTo("-500.00");
    }

    @Test
    void maternidad_sin_historial_usa_fallback_remuneracion_base() {
        when(movimientoRepository.findByEmpleadoIdAndActivo(EMP_ID, 1))
                .thenReturn(List.of()); // sin historial.

        EmpleadoEvento e = eventoConTipo("MATERNIDAD", "S",
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 30));

        SubsidioCalculadoDto r = service.calcular(e, new BigDecimal("3000.00"));

        // Sin historial → promedio = remuneración base = 3000.
        assertThat(r.promedioMensual12Meses()).isEqualByComparingTo("3000.00");
        assertThat(r.subsidioEssalud()).isEqualByComparingTo("3000");
        assertThat(r.diferenciaAsumidaIndeci()).isEqualByComparingTo("0.00");
    }

    @Test
    void maternidad_con_solo_3_meses_de_historial_promedia_los_disponibles() {
        // Empleado entró hace 3 meses → solo 3 movimientos previos.
        when(movimientoRepository.findByEmpleadoIdAndActivo(EMP_ID, 1))
                .thenReturn(historialUniforme("3000.00", 3, YearMonthRef.de(2026, 5)));

        EmpleadoEvento e = eventoConTipo("MATERNIDAD", "S",
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 30));

        SubsidioCalculadoDto r = service.calcular(e, new BigDecimal("3000.00"));

        // 3 movimientos de 3000 → suma 9000 / 3 = 3000.
        assertThat(r.promedioMensual12Meses()).isEqualByComparingTo("3000.00");
    }

    @Test
    void maternidad_98dias_clasico_descanso_pre_post_parto() {
        // Periodo legal en Perú: 49 días pre + 49 días post = 98 días.
        when(movimientoRepository.findByEmpleadoIdAndActivo(EMP_ID, 1))
                .thenReturn(historialUniforme("3000.00", 12, YearMonthRef.de(2026, 7)));

        EmpleadoEvento e = eventoConTipo("MATERNIDAD", "S",
                LocalDate.of(2026, 5, 25), LocalDate.of(2026, 8, 30));

        SubsidioCalculadoDto r = service.calcular(e, new BigDecimal("3000.00"));

        // 2026-05-25 a 2026-08-30 = 98 días (verifica fórmula DAYS.between + 1).
        assertThat(r.diasDescanso()).isEqualTo(98);
        // Subtotal = 100 × 98 = 9800.
        assertThat(r.subtotalRemunerativo()).isEqualByComparingTo("9800.00");
        // Subsidio EsSalud = 100 × 98 = 9800 (redondeado a entero).
        assertThat(r.subsidioEssalud()).isEqualByComparingTo("9800");
    }

    @Test
    void filtro_12meses_excluye_movimientos_fuera_de_ventana() {
        // Historial con 13 movimientos (uno fuera de ventana, debería excluirse).
        // 12 meses anteriores a mayo 2026 = mayo 2025 a abril 2026.
        // Agregamos un movimiento de abril 2025 (fuera) que NO debe contar.
        java.util.List<MovimientoPlanilla> historial = new java.util.ArrayList<>(
                historialUniforme("3000.00", 12, YearMonthRef.de(2026, 5)));
        MovimientoPlanilla fuera = mov(LocalDate.of(2025, 4, 1), "9999.00");
        historial.add(fuera);

        when(movimientoRepository.findByEmpleadoIdAndActivo(EMP_ID, 1))
                .thenReturn(historial);

        EmpleadoEvento e = eventoConTipo("MATERNIDAD", "S",
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 30));

        SubsidioCalculadoDto r = service.calcular(e, new BigDecimal("3000.00"));

        // El movimiento de abril 2025 NO entra → promedio sigue siendo 3000.
        assertThat(r.promedioMensual12Meses()).isEqualByComparingTo("3000.00");
    }

    @Test
    void filtro_12meses_excluye_movimiento_del_mismo_periodo_del_evento() {
        // El evento es en mayo 2026 → mayo 2026 NO entra en el promedio
        // (la ventana es de los 12 meses ANTERIORES). Verifica frontera.
        java.util.List<MovimientoPlanilla> historial = new java.util.ArrayList<>(
                historialUniforme("3000.00", 12, YearMonthRef.de(2026, 5)));
        // Agregamos un movimiento de mayo 2026 (el mismo mes del evento).
        MovimientoPlanilla mayo = mov(LocalDate.of(2026, 5, 1), "9999.00");
        historial.add(mayo);

        when(movimientoRepository.findByEmpleadoIdAndActivo(EMP_ID, 1))
                .thenReturn(historial);

        EmpleadoEvento e = eventoConTipo("MATERNIDAD", "S",
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 30));

        SubsidioCalculadoDto r = service.calcular(e, new BigDecimal("3000.00"));

        // El movimiento de mayo 2026 NO entra → promedio sigue siendo 3000.
        assertThat(r.promedioMensual12Meses()).isEqualByComparingTo("3000.00");
    }

    @Test
    void tolera_periodos_con_y_sin_guion() {
        // Algunos movimientos usan "YYYY-MM", otros "YYYYMM". Ambos válidos.
        MovimientoPlanilla m1 = new MovimientoPlanilla();
        m1.setPeriodo("2025-12");
        m1.setTotalIngresos(3000.0);
        MovimientoPlanilla m2 = new MovimientoPlanilla();
        m2.setPeriodo("202601");
        m2.setTotalIngresos(3000.0);

        when(movimientoRepository.findByEmpleadoIdAndActivo(EMP_ID, 1))
                .thenReturn(List.of(m1, m2));

        EmpleadoEvento e = eventoConTipo("MATERNIDAD", "S",
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 30));

        SubsidioCalculadoDto r = service.calcular(e, new BigDecimal("3000.00"));

        // Ambos formatos parseados → promedio sobre 2 = 3000.
        assertThat(r.promedioMensual12Meses()).isEqualByComparingTo("3000.00");
    }

    // ======================================================================
    // Helpers
    // ======================================================================

    private record YearMonthRef(int anio, int mes) {
        static YearMonthRef de(int a, int m) { return new YearMonthRef(a, m); }
    }

    /**
     * Genera {@code count} movimientos con el mismo {@code totalIngresos},
     * uno por mes terminando en (eventoYM - 1).
     */
    private List<MovimientoPlanilla> historialUniforme(
            String monto, int count, YearMonthRef eventoYM) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> {
                    java.time.YearMonth ym = java.time.YearMonth
                            .of(eventoYM.anio(), eventoYM.mes())
                            .minusMonths(i);
                    return mov(LocalDate.of(ym.getYear(), ym.getMonthValue(), 1), monto);
                })
                .toList();
    }

    private MovimientoPlanilla mov(LocalDate fechaPeriodo, String totalIngresos) {
        MovimientoPlanilla m = new MovimientoPlanilla();
        m.setPeriodo(String.format("%04d%02d",
                fechaPeriodo.getYear(), fechaPeriodo.getMonthValue()));
        m.setTotalIngresos(new BigDecimal(totalIngresos).doubleValue());
        return m;
    }

    private EmpleadoEvento eventoConTipo(
            String codigoTipo, String generaSubsidio,
            LocalDate inicio, LocalDate fin) {
        TipoEvento t = new TipoEvento();
        t.setCodigo(codigoTipo);
        t.setGeneraSubsidio(generaSubsidio);

        EmpleadoEvento e = new EmpleadoEvento();
        e.setEmpleadoId(EMP_ID);
        e.setTipoEvento(t);
        e.setFechaInicio(inicio);
        e.setFechaFin(fin);
        return e;
    }
}
