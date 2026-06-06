package com.indeci.rrhh.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.indeci.rrhh.dto.SubsidioCalculadoDto;
import com.indeci.rrhh.entity.EmpleadoEvento;
import com.indeci.rrhh.entity.MovimientoPlanilla;
import com.indeci.rrhh.entity.SubsidioEventoCalculo;
import com.indeci.rrhh.entity.TipoEvento;
import com.indeci.rrhh.repository.EmpleadoEventoRepository;
import com.indeci.rrhh.repository.MovimientoPlanillaRepository;
import com.indeci.rrhh.repository.SubsidioEventoCalculoRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
class SubsidioCalculadorServiceTest {

    private static final Long EMPLEADO_ID = 41L;

    @Mock private MovimientoPlanillaRepository movimientoRepository;
    @Mock private EmpleadoEventoRepository empleadoEventoRepository;
    @Mock private SubsidioEventoCalculoRepository subsidioEventoCalculoRepository;
    @Mock private ParametroRemunerativoService parametroService;
    @Mock private CalculoSnapshotService calculoSnapshotService;

    private SubsidioCalculadorService service;

    @BeforeEach
    void setUp() {
        service = new SubsidioCalculadorService(
                movimientoRepository,
                empleadoEventoRepository,
                subsidioEventoCalculoRepository,
                parametroService,
                calculoSnapshotService);
        lenient().when(parametroService.obtenerValor(eq("UIT"), anyInt(), any()))
                .thenReturn(new BigDecimal("5350"));
        lenient().when(parametroService.obtenerValor(eq("SUBSIDIO_TOPE_PCT_UIT"), anyInt(), any()))
                .thenReturn(new BigDecimal("0.45"));
        lenient().when(parametroService.obtenerValor(eq("SUBSIDIO_DIVISOR_PROMEDIO"), anyInt(), any()))
                .thenReturn(new BigDecimal("360"));
        lenient().when(empleadoEventoRepository.findEnfermedadesPreviasEnAnio(
                        anyLong(), any(LocalDate.class), any(LocalDate.class), any()))
                .thenReturn(List.of());
    }

    @Test
    void maternidad_reconoce_todos_los_dias_con_base_12_meses_topada() {
        when(parametroService.obtenerValor(
                eq("SUBSIDIO_DIAS_ASUME_ENTIDAD_MATERNIDAD"), anyInt(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(movimientoRepository.findByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(List.of());

        SubsidioCalculadoDto r = service.calcular(
                evento("MATERNIDAD", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 8, 6)),
                new BigDecimal("5364.19"));

        assertThat(r.aplica()).isTrue();
        assertThat(r.tipoSubsidio()).isEqualTo("MATERNIDAD");
        assertThat(r.diasDescanso()).isEqualTo(98);
        assertThat(r.diasEntidad()).isZero();
        assertThat(r.diasSubsidioEssalud()).isEqualTo(98);
        assertThat(r.codigoPlameSubsidio()).isEqualTo("0915");
        assertThat(r.remuneracionDiaria()).isEqualByComparingTo("178.81");
        assertThat(r.subtotalRemunerativo()).isEqualByComparingTo("17523.38");
        assertThat(r.baseReconocidaEssalud()).isEqualByComparingTo("28890.00");
        assertThat(r.subsidioDiarioEssalud()).isEqualByComparingTo("80.25");
        assertThat(r.subsidioEssalud()).isEqualByComparingTo("7865");
        assertThat(r.diferenciaAsumidaIndeci()).isEqualByComparingTo("9658.38");
    }

    @Test
    void enfermedad_de_15_dias_no_genera_subsidio_essalud_por_regla_dia_21() {
        when(parametroService.obtenerValor(
                eq("SUBSIDIO_DIAS_ASUME_ENTIDAD_ENFERMEDAD"), anyInt(), any()))
                .thenReturn(new BigDecimal("20"));
        when(movimientoRepository.findByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(List.of());

        SubsidioCalculadoDto r = service.calcular(
                evento("ENFERMEDAD", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 15)),
                new BigDecimal("3000.00"));

        assertThat(r.diasDescanso()).isEqualTo(15);
        assertThat(r.diasAcumuladosPrevios()).isZero();
        assertThat(r.diasEntidad()).isEqualTo(15);
        assertThat(r.diasSubsidioEssalud()).isZero();
        assertThat(r.codigoPlameSubsidio()).isEqualTo("0916");
        assertThat(r.subtotalRemunerativo()).isEqualByComparingTo("1500.00");
        assertThat(r.subsidioEssalud()).isEqualByComparingTo("0");
        assertThat(r.diferenciaAsumidaIndeci()).isEqualByComparingTo("1500.00");
    }

    @Test
    void enfermedad_de_30_dias_reconoce_solo_10_dias_essalud() {
        when(parametroService.obtenerValor(
                eq("SUBSIDIO_DIAS_ASUME_ENTIDAD_ENFERMEDAD"), anyInt(), any()))
                .thenReturn(new BigDecimal("20"));
        when(movimientoRepository.findByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(List.of());

        SubsidioCalculadoDto r = service.calcular(
                evento("ENFERMEDAD", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 30)),
                new BigDecimal("3000.00"));

        assertThat(r.diasDescanso()).isEqualTo(30);
        assertThat(r.diasEntidad()).isEqualTo(20);
        assertThat(r.diasSubsidioEssalud()).isEqualTo(10);
        assertThat(r.subtotalRemunerativo()).isEqualByComparingTo("3000.00");
        assertThat(r.subsidioDiarioEssalud()).isEqualByComparingTo("80.25");
        assertThat(r.subsidioEssalud()).isEqualByComparingTo("803");
        assertThat(r.diferenciaAsumidaIndeci()).isEqualByComparingTo("2197.00");
    }

    @Test
    void enfermedad_acumula_20_dias_por_anio_calendario_y_reconoce_7_dias_essalud() {
        EmpleadoEvento marzo = evento(
                "ENFERMEDAD", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 12));
        marzo.setId(10L);
        marzo.setEstado("VALIDADO");

        when(parametroService.obtenerValor(
                eq("SUBSIDIO_DIAS_ASUME_ENTIDAD_ENFERMEDAD"), anyInt(), any()))
                .thenReturn(new BigDecimal("20"));
        when(movimientoRepository.findByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(List.of());
        when(empleadoEventoRepository.findEnfermedadesPreviasEnAnio(
                        eq(EMPLEADO_ID),
                        eq(LocalDate.of(2026, 1, 1)),
                        eq(LocalDate.of(2026, 5, 31)),
                        any()))
                .thenReturn(List.of(marzo));

        SubsidioCalculadoDto r = service.calcular(
                evento("ENFERMEDAD", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 15)),
                new BigDecimal("3000.00"));

        assertThat(r.diasDescanso()).isEqualTo(15);
        assertThat(r.diasAcumuladosPrevios()).isEqualTo(12);
        assertThat(r.diasEntidad()).isEqualTo(8);
        assertThat(r.diasSubsidioEssalud()).isEqualTo(7);
        assertThat(r.subsidioDiarioEssalud()).isEqualByComparingTo("80.25");
        assertThat(r.subsidioEssalud()).isEqualByComparingTo("562");
        assertThat(r.diferenciaAsumidaIndeci()).isEqualByComparingTo("938.00");
    }

    @Test
    void historial_se_topa_mes_a_mes_para_base_reconocida() {
        when(parametroService.obtenerValor(
                eq("SUBSIDIO_DIAS_ASUME_ENTIDAD_MATERNIDAD"), anyInt(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(movimientoRepository.findByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(List.of(
                        movimiento("2026-04", 6000.0),
                        movimiento("2026-03", 2000.0),
                        movimiento("2025-01", 9999.0)));

        SubsidioCalculadoDto r = service.calcular(
                evento("MATERNIDAD", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 10)),
                new BigDecimal("3000.00"));

        assertThat(r.baseReconocidaEssalud()).isEqualByComparingTo("4407.50");
    }

    @Test
    void licencia_sin_subsidio_devuelve_no_aplica() {
        SubsidioCalculadoDto r = service.calcular(
                evento("LICENCIA_SIN_GOCE", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 5), "N"),
                new BigDecimal("3000.00"));

        assertThat(r.aplica()).isFalse();
        verifyNoInteractions(movimientoRepository);
    }

    @Test
    void calcular_y_registrar_persiste_trazabilidad_por_evento() {
        when(parametroService.obtenerValor(
                eq("SUBSIDIO_DIAS_ASUME_ENTIDAD_ENFERMEDAD"), anyInt(), any()))
                .thenReturn(new BigDecimal("20"));
        when(movimientoRepository.findByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(List.of());

        EmpleadoEvento evento = evento(
                "ENFERMEDAD", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 30));
        evento.setId(77L);

        service.calcularYRegistrar(
                evento, new BigDecimal("3000.00"), 900L, "202605", "tester");

        ArgumentCaptor<SubsidioEventoCalculo> captor =
                ArgumentCaptor.forClass(SubsidioEventoCalculo.class);
        verify(subsidioEventoCalculoRepository).desactivarVigentesPorEvento(77L);
        verify(subsidioEventoCalculoRepository).save(captor.capture());
        SubsidioEventoCalculo guardado = captor.getValue();
        assertThat(guardado.getEmpleadoEventoId()).isEqualTo(77L);
        assertThat(guardado.getMovimientoPlanillaId()).isEqualTo(900L);
        assertThat(guardado.getCodigoPlameSubsidio()).isEqualTo("0916");
        assertThat(guardado.getCodigoPlameDiferencial()).isEqualTo("2073");
        assertThat(guardado.getDiasACargoEntidad()).isEqualTo(20);
        assertThat(guardado.getDiasSubsidioEssalud()).isEqualTo(10);
        assertThat(guardado.getSubsidioEssalud()).isEqualByComparingTo("803");
        assertThat(guardado.getPagoDiferencial()).isEqualByComparingTo("2197.00");
        verify(calculoSnapshotService).registrar(any(CalculoSnapshotService.Registro.class));
    }

    private EmpleadoEvento evento(String codigo, LocalDate inicio, LocalDate fin) {
        return evento(codigo, inicio, fin, "S");
    }

    private EmpleadoEvento evento(String codigo, LocalDate inicio, LocalDate fin, String generaSubsidio) {
        TipoEvento tipo = new TipoEvento();
        tipo.setCodigo(codigo);
        tipo.setGeneraSubsidio(generaSubsidio);

        EmpleadoEvento evento = new EmpleadoEvento();
        evento.setEmpleadoId(EMPLEADO_ID);
        evento.setFechaInicio(inicio);
        evento.setFechaFin(fin);
        evento.setTipoEvento(tipo);
        return evento;
    }

    private MovimientoPlanilla movimiento(String periodo, Double totalIngresos) {
        MovimientoPlanilla movimiento = new MovimientoPlanilla();
        movimiento.setEmpleadoId(EMPLEADO_ID);
        movimiento.setPeriodo(periodo);
        movimiento.setTotalIngresos(totalIngresos);
        movimiento.setActivo(1);
        return movimiento;
    }
}
