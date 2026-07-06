package com.indeci.rrhh.service;

import com.indeci.rrhh.entity.ConceptoPlanilla;
import com.indeci.rrhh.entity.ReintegroMonto;
import com.indeci.rrhh.repository.ConceptoPlanillaRepository;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.EmpleadoPuestoRepository;
import com.indeci.rrhh.repository.EmpleadoRepository;
import com.indeci.rrhh.repository.MovimientoPlanillaRepository;
import com.indeci.rrhh.repository.PeriodoPlanillaRepository;
import com.indeci.rrhh.repository.PlanillaLoteRepository;
import com.indeci.rrhh.repository.ReintegroMontoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BLOQUE 3 (F2) — Pago de reintegros/devengados en Planilla Adicional.
 * Verifica el camino feliz (detección → pago bajo 00507 → estado PAGADO) y el
 * guard anti-doble-pago (un reintegro ya PAGADO no se reprocesa).
 */
@ExtendWith(MockitoExtension.class)
class PlanillaAdicionalReintegroTest {

    @Mock private PeriodoPlanillaRepository periodoPlanillaRepository;
    @Mock private EmpleadoPlanillaRepository empleadoPlanillaRepository;
    @Mock private MovimientoPlanillaRepository movimientoPlanillaRepository;
    @Mock private EmpleadoPuestoRepository empleadoPuestoRepository;
    @Mock private EmpleadoRepository empleadoRepository;
    @Mock private GeneradorPlanillaService generadorPlanillaService;
    @Mock private PlanillaLoteRepository planillaLoteRepository;
    @Mock private ReintegroMontoRepository reintegroMontoRepository;
    @Mock private ConceptoPlanillaRepository conceptoPlanillaRepository;

    @InjectMocks private PlanillaLoteService service;

    private static final Long EMPLEADO_ID = 41L;
    private static final String PERIODO = "2026-06";
    private static final Long LOTE_ID = 10L;
    private static final String TIPO = "ADICIONAL_1";

    private ConceptoPlanilla concepto00507() {
        ConceptoPlanilla c = new ConceptoPlanilla();
        c.setId(507L);
        c.setCodigo("00507");
        c.setCodigoMef("00507");
        c.setTipoConcepto("REMUNERATIVO");
        return c;
    }

    private ReintegroMonto reintegro(String estado) {
        ReintegroMonto r = new ReintegroMonto();
        r.setId(1L);
        r.setEmpleadoId(EMPLEADO_ID);
        r.setPeriodoDestino(PERIODO);
        r.setMonto(new BigDecimal("1500.00"));
        r.setMotivo("DEVENGADO_JUDICIAL");
        r.setSustento("R.J. 123-2026-INDECI");
        r.setEstadoPago(estado);
        return r;
    }

    @Test
    void camino_feliz_reintegro_pendiente_se_paga_bajo_00507_y_pasa_a_pagado() {
        when(conceptoPlanillaRepository.findByCodigoMefAndActivo("00507", 1))
                .thenReturn(Optional.of(concepto00507()));
        ReintegroMonto pendiente = reintegro("PENDIENTE");
        when(reintegroMontoRepository
                .findByEmpleadoIdAndPeriodoDestinoAndEstadoPago(EMPLEADO_ID, PERIODO, "PENDIENTE"))
                .thenReturn(List.of(pendiente));

        ReflectionTestUtils.invokeMethod(
                service, "procesarReintegrosPendientes", PERIODO, List.of(EMPLEADO_ID), LOTE_ID, TIPO);

        // Se paga bajo el concepto MEF 00507 con el monto exacto.
        ArgumentCaptor<ConceptoPlanilla> conceptoCap = ArgumentCaptor.forClass(ConceptoPlanilla.class);
        verify(generadorPlanillaService).generarReintegroAdicional(
                eq(EMPLEADO_ID), eq(PERIODO), eq(new BigDecimal("1500.00")),
                conceptoCap.capture(), eq(LOTE_ID), eq(TIPO));
        assertThat(conceptoCap.getValue().getCodigoMef()).isEqualTo("00507");

        // Transición atómica a PAGADO.
        ArgumentCaptor<ReintegroMonto> reintCap = ArgumentCaptor.forClass(ReintegroMonto.class);
        verify(reintegroMontoRepository).save(reintCap.capture());
        assertThat(reintCap.getValue().getEstadoPago()).isEqualTo("PAGADO");
    }

    @Test
    void anti_doble_pago_reintegro_ya_pagado_no_se_reprocesa() {
        when(conceptoPlanillaRepository.findByCodigoMefAndActivo("00507", 1))
                .thenReturn(Optional.of(concepto00507()));
        // Segunda corrida: el registro ya está PAGADO → el guard lo ignora limpiamente.
        when(reintegroMontoRepository
                .findByEmpleadoIdAndPeriodoDestinoAndEstadoPago(EMPLEADO_ID, PERIODO, "PENDIENTE"))
                .thenReturn(List.of(reintegro("PAGADO")));

        ReflectionTestUtils.invokeMethod(
                service, "procesarReintegrosPendientes", PERIODO, List.of(EMPLEADO_ID), LOTE_ID, TIPO);

        // No se vuelve a pagar ni se duplica el egreso.
        verify(generadorPlanillaService, never())
                .generarReintegroAdicional(any(), any(), any(), any(), any(), any());
        verify(reintegroMontoRepository, never()).save(any());
    }
}
