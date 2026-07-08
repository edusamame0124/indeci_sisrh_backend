package com.indeci.rrhh.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.indeci.rrhh.dto.AguinaldoRequest;
import com.indeci.rrhh.dto.AguinaldoResultDto;
import com.indeci.rrhh.entity.ConceptoPlanilla;
import com.indeci.rrhh.entity.EmpleadoConcepto;
import com.indeci.rrhh.entity.EmpleadoPlanilla;
import com.indeci.rrhh.entity.MovimientoPlanilla;
import com.indeci.rrhh.entity.PlanillaLote;
import com.indeci.rrhh.repository.ConceptoPlanillaRepository;
import com.indeci.rrhh.repository.EmpleadoConceptoRepository;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.MovimientoPlanillaRepository;
import com.indeci.rrhh.repository.PlanillaLoteRepository;

/** Track B — Generación del aguinaldo: judicial (#B), régimen y elegibilidad (#4). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AguinaldoServiceTest {

    private static final String PERIODO = "2026-07";
    private static final LocalDate CORTE = LocalDate.of(2026, 6, 30);

    @Mock private EmpleadoPlanillaRepository planillaRepository;
    @Mock private MovimientoPlanillaRepository movimientoRepository;
    @Mock private ConceptoPlanillaRepository conceptoRepository;
    @Mock private EmpleadoConceptoRepository empleadoConceptoRepository;
    @Mock private ParametroRemunerativoService parametroService;
    @Mock private GeneradorPlanillaService motor;
    @Mock private PlanillaLoteRepository planillaLoteRepository;

    @InjectMocks private AguinaldoService service;

    private EmpleadoPlanilla vinculoCas(long empleadoId, long regimenId) {
        EmpleadoPlanilla v = new EmpleadoPlanilla();
        v.setEmpleadoId(empleadoId);
        v.setRegimenLaboralId(regimenId);
        v.setFechaInicioContrato(LocalDate.of(2025, 1, 1)); // antes del corte
        return v;
    }

    private void stubParametros() {
        when(parametroService.obtenerValorOpcional(eq("AGUINALDO_CAS_PISO"), eq(2026), any()))
                .thenReturn(Optional.of(new BigDecimal("300.00")));
        when(parametroService.obtenerValorOpcional(eq("AGUINALDO_276_MONTO"), eq(2026), any()))
                .thenReturn(Optional.of(new BigDecimal("300.00")));
        when(movimientoRepository.findByPeriodoAndActivo(PERIODO, 1)).thenReturn(List.of());
        when(movimientoRepository.save(any(MovimientoPlanilla.class)))
                .thenAnswer(inv -> {
                    MovimientoPlanilla m = inv.getArgument(0);
                    if (m.getId() == null) m.setId(100L);
                    return m;
                });
        when(motor.conceptoPorCodigoMef(any())).thenReturn(new ConceptoPlanilla());
        when(planillaLoteRepository.save(any(PlanillaLote.class)))
                .thenAnswer(inv -> {
                    PlanillaLote l = inv.getArgument(0);
                    if (l.getId() == null) l.setId(500L);
                    return l;
                });
    }

    @Test
    void cas_con_retencion_judicial_30pct_descuenta_sobre_el_aguinaldo() {
        stubParametros();
        EmpleadoPlanilla v = vinculoCas(1L, 10L);
        when(planillaRepository.findByActivo(1)).thenReturn(List.of(v));
        when(motor.resolverRegimenLaboralCodigo(10L)).thenReturn("CAS");
        when(motor.resolverBaseRemunerativa(any(), eq(PERIODO))).thenReturn(new BigDecimal("5364.19"));

        // Retención judicial activa 30%.
        EmpleadoConcepto ec = new EmpleadoConcepto();
        ec.setConceptoPlanillaId(716L);
        ec.setPorcentaje(30.0);
        ec.setActivo(1);
        when(empleadoConceptoRepository.findByEmpleadoIdAndActivo(1L, 1)).thenReturn(List.of(ec));
        ConceptoPlanilla judicial = new ConceptoPlanilla();
        judicial.setCodigo("DESCUENTO_JUDICIAL");
        when(conceptoRepository.findById(716L)).thenReturn(Optional.of(judicial));

        AguinaldoRequest req = new AguinaldoRequest();
        req.setPeriodo(PERIODO);
        req.setPctCas(new BigDecimal("100"));
        req.setFechaCorte(CORTE);

        AguinaldoResultDto r = service.generar(req);

        assertThat(r.generados()).isEqualTo(1);
        ArgumentCaptor<MovimientoPlanilla> capt = ArgumentCaptor.forClass(MovimientoPlanilla.class);
        verify(movimientoRepository).save(capt.capture());
        MovimientoPlanilla mov = capt.getValue();
        assertThat(mov.getTotalIngresos()).isEqualTo(5364.19);
        assertThat(mov.getTotalDescuentos()).isEqualTo(1609.26); // 30% de 5364.19
        assertThat(mov.getNetoPagar()).isEqualTo(3754.93);       // 5364.19 - 1609.26
        assertThat(mov.getTipoPlanilla()).isEqualTo("AGUINALDO");
    }

    @Test
    void servir_gana_100pct_y_sin_judicial_neto_igual_al_aguinaldo() {
        stubParametros();
        EmpleadoPlanilla v = vinculoCas(2L, 20L);
        when(planillaRepository.findByActivo(1)).thenReturn(List.of(v));
        when(motor.resolverRegimenLaboralCodigo(20L)).thenReturn("SERVIR");
        when(motor.resolverBaseRemunerativa(any(), eq(PERIODO))).thenReturn(new BigDecimal("18707.14"));
        when(empleadoConceptoRepository.findByEmpleadoIdAndActivo(2L, 1)).thenReturn(List.of());

        AguinaldoRequest req = new AguinaldoRequest();
        req.setPeriodo(PERIODO);
        req.setFechaCorte(CORTE);

        AguinaldoResultDto r = service.generar(req);

        assertThat(r.generados()).isEqualTo(1);
        ArgumentCaptor<MovimientoPlanilla> capt = ArgumentCaptor.forClass(MovimientoPlanilla.class);
        verify(movimientoRepository).save(capt.capture());
        assertThat(capt.getValue().getTotalIngresos()).isEqualTo(18707.14);
        assertThat(capt.getValue().getTotalDescuentos()).isEqualTo(0.0);
        assertThat(capt.getValue().getNetoPagar()).isEqualTo(18707.14);
    }

    @Test
    void cesado_antes_del_corte_queda_excluido_con_motivo() {
        stubParametros();
        EmpleadoPlanilla v = vinculoCas(3L, 10L);
        v.setFechaCese(LocalDate.of(2026, 5, 31)); // cesó antes del corte 30/06
        when(planillaRepository.findByActivo(1)).thenReturn(List.of(v));

        AguinaldoRequest req = new AguinaldoRequest();
        req.setPeriodo(PERIODO);
        req.setPctCas(new BigDecimal("100"));
        req.setFechaCorte(CORTE);

        AguinaldoResultDto r = service.generar(req);

        assertThat(r.generados()).isZero();
        assertThat(r.excluidos()).hasSize(1);
        assertThat(r.excluidos().get(0).empleadoId()).isEqualTo(3L);
        assertThat(r.excluidos().get(0).motivo()).contains("Cesado antes del inicio del per");
        verify(movimientoRepository, never()).save(any());
    }

    @Test
    void regimen_728_no_genera_aguinaldo() {
        stubParametros();
        EmpleadoPlanilla v = vinculoCas(4L, 40L);
        when(planillaRepository.findByActivo(1)).thenReturn(List.of(v));
        when(motor.resolverRegimenLaboralCodigo(40L)).thenReturn("728");
        when(motor.resolverBaseRemunerativa(any(), eq(PERIODO))).thenReturn(new BigDecimal("5000.00"));

        AguinaldoRequest req = new AguinaldoRequest();
        req.setPeriodo(PERIODO);
        req.setPctCas(new BigDecimal("100"));
        req.setFechaCorte(CORTE);

        AguinaldoResultDto r = service.generar(req);

        assertThat(r.generados()).isZero();
        assertThat(r.excluidos()).anyMatch(e -> e.motivo().contains("Régimen sin aguinaldo"));
        verify(movimientoRepository, never()).save(any());
    }
}
