package com.indeci.rrhh.service.asistencia;

import com.indeci.rrhh.entity.AsistenciaCabecera;
import com.indeci.rrhh.entity.AsistenciaDetalle;
import com.indeci.rrhh.entity.EmpleadoTurno24h;
import com.indeci.rrhh.entity.JornadaRegimen;
import com.indeci.rrhh.entity.RegimenLaboral;
import com.indeci.rrhh.repository.AsistenciaDetalleRepository;
import com.indeci.rrhh.repository.EmpleadoTurno24hRepository;
import com.indeci.rrhh.repository.JornadaRegimenRepository;
import com.indeci.rrhh.repository.RegimenLaboralRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class Turno24hReconciliadorServiceTest {

    @Mock private EmpleadoTurno24hRepository turno24hRepository;
    @Mock private RegimenLaboralRepository regimenLaboralRepository;
    @Mock private JornadaRegimenRepository jornadaRegimenRepository;
    @Mock private AsistenciaDetalleRepository detalleRepository;

    @InjectMocks private Turno24hReconciliadorService service;

    private static final LocalDate DIA_N = LocalDate.of(2026, 7, 10);
    private static final LocalDate DIA_N1 = LocalDate.of(2026, 7, 11);

    @Test
    void reconciliar_casoFeliz_reclasificaAmbosDiasYAjustaAgregados() {
        AsistenciaCabecera cabecera = cabecera();
        stubTurnoActivo();
        stubRegimenCoen("08:30");

        AsistenciaDetalle diaN = detalleOrfano(DIA_N, "08:25");
        AsistenciaDetalle diaN1 = detalleOrfano(DIA_N1, "08:35");
        when(detalleRepository.findByCabeceraIdOrderByDia(1L)).thenReturn(List.of(diaN, diaN1));

        int reconciliados = service.reconciliar(cabecera);

        assertThat(reconciliados).isEqualTo(2);
        assertThat(diaN.getTipoDia()).isEqualTo("LABORAL");
        assertThat(diaN1.getTipoDia()).isEqualTo("DESCANSO");
        assertThat(diaN.getObservacion()).contains("turno COEN 24h");
        assertThat(diaN1.getObservacion()).contains("turno COEN 24h");
        assertThat(cabecera.getDiasFalta()).isEqualTo(3); // 5 - 2
        assertThat(cabecera.getDiasLaborados()).isEqualTo(11); // 10 + 1
        verify(detalleRepository).saveAll(anyList());
    }

    @Test
    void reconciliar_marcaFueraDeVentana_noTocaNada() {
        AsistenciaCabecera cabecera = cabecera();
        stubTurnoActivo();
        stubRegimenCoen("08:30");

        // Día N+1 marcado a las 23:00 -> abandono de guardia real, no un cierre de turno 24h.
        AsistenciaDetalle diaN = detalleOrfano(DIA_N, "08:25");
        AsistenciaDetalle diaN1 = detalleOrfano(DIA_N1, "23:00");
        when(detalleRepository.findByCabeceraIdOrderByDia(1L)).thenReturn(List.of(diaN, diaN1));

        int reconciliados = service.reconciliar(cabecera);

        assertThat(reconciliados).isZero();
        assertThat(diaN.getTipoDia()).isEqualTo("FALTA");
        assertThat(diaN1.getTipoDia()).isEqualTo("FALTA");
        verify(detalleRepository, never()).saveAll(anyList());
    }

    @Test
    void reconciliar_sinTurno24hActivo_noHaceNada() {
        AsistenciaCabecera cabecera = cabecera();
        when(turno24hRepository.findByEmpleadoIdAndActivoOrderByFechaInicioDesc(10L, 1))
                .thenReturn(List.of());

        int reconciliados = service.reconciliar(cabecera);

        assertThat(reconciliados).isZero();
        verify(detalleRepository, never()).findByCabeceraIdOrderByDia(anyLong());
    }

    @Test
    void reconciliar_yaReconciliado_esIdempotente() {
        AsistenciaCabecera cabecera = cabecera();
        stubTurnoActivo();
        stubRegimenCoen("08:30");

        // Ya reconciliado en una pasada anterior: tipoDia ya no es FALTA -> no vuelve a tocarlo.
        AsistenciaDetalle diaN = detalleOrfano(DIA_N, "08:25");
        diaN.setTipoDia("LABORAL");
        AsistenciaDetalle diaN1 = detalleOrfano(DIA_N1, "08:35");
        diaN1.setTipoDia("DESCANSO");
        when(detalleRepository.findByCabeceraIdOrderByDia(1L)).thenReturn(List.of(diaN, diaN1));

        int reconciliados = service.reconciliar(cabecera);

        assertThat(reconciliados).isZero();
        verify(detalleRepository, never()).saveAll(anyList());
    }

    @Test
    void reconciliar_regimenCoenSinJornadaConfigurada_noHaceNadaYNoLanza() {
        AsistenciaCabecera cabecera = cabecera();
        stubTurnoActivo();
        RegimenLaboral coen = new RegimenLaboral();
        coen.setId(5L);
        when(regimenLaboralRepository.findByCodigo(Turno24hReconciliadorService.CODIGO_REGIMEN_COEN))
                .thenReturn(Optional.of(coen));
        when(jornadaRegimenRepository.findByRegimenLaboralId(5L)).thenReturn(Optional.empty());

        int reconciliados = service.reconciliar(cabecera);

        assertThat(reconciliados).isZero();
        verify(detalleRepository, never()).findByCabeceraIdOrderByDia(anyLong());
    }

    private void stubTurnoActivo() {
        EmpleadoTurno24h turno = new EmpleadoTurno24h();
        turno.setEmpleadoId(10L);
        turno.setFechaInicio(LocalDate.of(2026, 1, 1));
        turno.setFechaFin(LocalDate.of(2026, 12, 31));
        when(turno24hRepository.findByEmpleadoIdAndActivoOrderByFechaInicioDesc(10L, 1))
                .thenReturn(List.of(turno));
    }

    private void stubRegimenCoen(String horaIngreso) {
        RegimenLaboral coen = new RegimenLaboral();
        coen.setId(5L);
        coen.setCodigo(Turno24hReconciliadorService.CODIGO_REGIMEN_COEN);
        lenient().when(regimenLaboralRepository.findByCodigo(Turno24hReconciliadorService.CODIGO_REGIMEN_COEN))
                .thenReturn(Optional.of(coen));

        JornadaRegimen jornada = new JornadaRegimen();
        jornada.setRegimenLaboralId(5L);
        jornada.setHoraIngreso(horaIngreso);
        jornada.setHoraSalida(horaIngreso);
        jornada.setJornadaHoras(java.math.BigDecimal.valueOf(24));
        jornada.setToleranciaIngresoMin(0);
        lenient().when(jornadaRegimenRepository.findByRegimenLaboralId(5L)).thenReturn(Optional.of(jornada));
    }

    private AsistenciaCabecera cabecera() {
        AsistenciaCabecera c = new AsistenciaCabecera();
        c.setId(1L);
        c.setEmpleadoId(10L);
        c.setDiasFalta(5);
        c.setDiasLaborados(10);
        return c;
    }

    private AsistenciaDetalle detalleOrfano(LocalDate dia, String marcaEntrada) {
        AsistenciaDetalle d = new AsistenciaDetalle();
        d.setDia(dia);
        d.setTipoDia("FALTA");
        d.setMarcaEntrada(marcaEntrada);
        d.setObservacion("Falta: solo ingreso (sin salida).");
        return d;
    }
}
