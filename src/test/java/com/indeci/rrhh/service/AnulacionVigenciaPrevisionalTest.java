package com.indeci.rrhh.service;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.entity.AfpParametroVigencia;
import com.indeci.rrhh.entity.IndAfp;
import com.indeci.rrhh.entity.OnpParametroVigencia;
import com.indeci.rrhh.entity.PrevisionalLog;
import com.indeci.rrhh.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests para la anulación lógica de vigencias AFP/ONP.
 * Criterios de aceptación: Eliminar en UI = ANULADO en BD.
 * NUNCA se borra físicamente un registro.
 */
@ExtendWith(MockitoExtension.class)
class AnulacionVigenciaPrevisionalTest {

    @Mock private IndAfpRepository              afpRepo;
    @Mock private AfpParametroVigenciaRepository afpVigenciaRepo;
    @Mock private OnpParametroVigenciaRepository onpVigenciaRepo;
    @Mock private PrevisionalLogRepository      logRepo;
    @Mock private EmpleadoPensionRepository     pensionRepo;
    @Mock private TipoComisionAfpRepository     tipoComisionAfpRepo;
    @Mock private PeriodoPlanillaRepository     periodoPlanillaRepo;
    @Mock private MovimientoPlanillaRepository  movimientoPlanillaRepo;

    @InjectMocks
    private ParametroPrevisionalService service;

    // ── Fixtures ───────────────────────────────────────────────

    private IndAfp afpHabitat() {
        IndAfp afp = new IndAfp();
        afp.setId(1L);
        afp.setNombre("AFP Habitat");
        afp.setCodigo("HABITAT");
        afp.setActivo(1);
        return afp;
    }

    private AfpParametroVigencia vigenciaAfpVigente() {
        AfpParametroVigencia v = new AfpParametroVigencia();
        v.setId(10L);
        v.setAfp(afpHabitat());
        v.setPeriodoInicio("202601");
        v.setPeriodoFin(null);
        v.setEstado("VIGENTE");
        v.setBloqueadoPorPlanilla(0);
        v.setAporteObligatorioPct(new BigDecimal("0.1000"));
        v.setComisionFlujoPct(new BigDecimal("0.0038"));
        v.setComisionSaldoAnualPct(BigDecimal.ZERO);
        v.setPrimaSeguroPct(new BigDecimal("0.0174"));
        v.setRemuneracionMaximaAseg(new BigDecimal("12537.00"));
        v.setFuenteOficial("SBS Circular AFP-163-2025");
        v.setCreadoPor("SISTEMA");
        v.setCreadoEn(LocalDateTime.now());
        return v;
    }

    private OnpParametroVigencia vigenciaOnpVigente() {
        OnpParametroVigencia v = new OnpParametroVigencia();
        v.setId(20L);
        v.setPeriodoInicio("199301");
        v.setPeriodoFin(null);
        v.setEstado("VIGENTE");
        v.setBloqueadoPorPlanilla(0);
        v.setAporteOnpPct(new BigDecimal("0.1300"));
        v.setFuenteOficial("Ley 19990 Art. 7");
        v.setCreadoPor("SISTEMA");
        v.setCreadoEn(LocalDateTime.now());
        return v;
    }

    // ── Test 1: Anular AFP cambia estado a ANULADO ─────────────

    @Test
    void anularAfp_feliz_cambiaEstadoAAnulado() {
        AfpParametroVigencia v = vigenciaAfpVigente();
        when(afpVigenciaRepo.findById(10L)).thenReturn(Optional.of(v));
        when(movimientoPlanillaRepo.findDistinctAfpVigenciaIdsByPeriodo(anyString())).thenReturn(List.of());
        when(periodoPlanillaRepo.countPlanillasCerradasEnRango(anyString(), any())).thenReturn(0L);
        when(logRepo.save(any(PrevisionalLog.class))).thenAnswer(i -> i.getArgument(0));

        service.anularAfpVigencia(10L, "Vigencia creada con periodo incorrecto por error", "analista01");

        assertThat(v.getEstado()).isEqualTo("ANULADO");
        assertThat(v.getMotivoAnulacion()).isEqualTo("Vigencia creada con periodo incorrecto por error");
        assertThat(v.getAnuladoPor()).isEqualTo("analista01");
        assertThat(v.getAnuladoEn()).isNotNull();
        assertThat(v.getModificadoPor()).isEqualTo("analista01");
    }

    // ── Test 2: Anular ONP cambia estado a ANULADO ─────────────

    @Test
    void anularOnp_feliz_cambiaEstadoAAnulado() {
        OnpParametroVigencia v = vigenciaOnpVigente();
        when(onpVigenciaRepo.findById(20L)).thenReturn(Optional.of(v));
        when(movimientoPlanillaRepo.findDistinctOnpVigenciaIdsByPeriodo(anyString())).thenReturn(List.of());
        when(periodoPlanillaRepo.countPlanillasCerradasEnRango(anyString(), any())).thenReturn(0L);
        when(logRepo.save(any(PrevisionalLog.class))).thenAnswer(i -> i.getArgument(0));

        service.anularOnpVigencia(20L, "Parámetros ingresados por error en el sistema", "analista01");

        assertThat(v.getEstado()).isEqualTo("ANULADO");
        assertThat(v.getMotivoAnulacion()).isEqualTo("Parámetros ingresados por error en el sistema");
        assertThat(v.getAnuladoPor()).isEqualTo("analista01");
        assertThat(v.getAnuladoEn()).isNotNull();
    }

    // ── Test 3: No se borra físicamente el registro ─────────────

    @Test
    void anularAfp_nuncaLlamaDeleteFisico() {
        AfpParametroVigencia v = vigenciaAfpVigente();
        when(afpVigenciaRepo.findById(10L)).thenReturn(Optional.of(v));
        when(movimientoPlanillaRepo.findDistinctAfpVigenciaIdsByPeriodo(anyString())).thenReturn(List.of());
        when(periodoPlanillaRepo.countPlanillasCerradasEnRango(anyString(), any())).thenReturn(0L);
        when(logRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        service.anularAfpVigencia(10L, "Registro reemplazado por nueva vigencia validada", "analista01");

        verify(afpVigenciaRepo, never()).delete(any());
        verify(afpVigenciaRepo, never()).deleteById(anyLong());
        verify(afpVigenciaRepo, never()).deleteAll();
    }

    // ── Test 4: Rechaza si BLOQUEADO_POR_PLANILLA = 1 (AFP) ────

    @Test
    void anularAfp_rechaza_siBloquedoPorPlanilla() {
        AfpParametroVigencia v = vigenciaAfpVigente();
        v.setBloqueadoPorPlanilla(1);
        when(afpVigenciaRepo.findById(10L)).thenReturn(Optional.of(v));

        assertThatThrownBy(() ->
            service.anularAfpVigencia(10L, "Intento de anulacion bloqueado", "analista01"))
            .isInstanceOf(NegocioException.class)
            .hasMessageContaining("utilizada en planilla");
    }

    // ── Test 5: Rechaza si BLOQUEADO_POR_PLANILLA = 1 (ONP) ────

    @Test
    void anularOnp_rechaza_siBloquedoPorPlanilla() {
        OnpParametroVigencia v = vigenciaOnpVigente();
        v.setBloqueadoPorPlanilla(1);
        when(onpVigenciaRepo.findById(20L)).thenReturn(Optional.of(v));

        assertThatThrownBy(() ->
            service.anularOnpVigencia(20L, "Intento de anulacion bloqueado", "analista01"))
            .isInstanceOf(NegocioException.class)
            .hasMessageContaining("utilizada en planilla");
    }

    // ── Test 6: Rechaza si hay planilla cerrada en rango ────────

    @Test
    void anularAfp_rechaza_siHayPlanillaCerradaEnRango() {
        AfpParametroVigencia v = vigenciaAfpVigente();
        when(afpVigenciaRepo.findById(10L)).thenReturn(Optional.of(v));
        when(movimientoPlanillaRepo.findDistinctAfpVigenciaIdsByPeriodo(anyString())).thenReturn(List.of());
        when(periodoPlanillaRepo.countPlanillasCerradasEnRango(anyString(), any())).thenReturn(2L);

        assertThatThrownBy(() ->
            service.anularAfpVigencia(10L, "Vigencia duplicada por error operativo", "analista01"))
            .isInstanceOf(NegocioException.class)
            .hasMessageContaining("planilla(s) CERRADA(S)/APROBADA(S)");
    }

    // ── Test 7: Rechaza si ya está ANULADO ──────────────────────

    @Test
    void anularAfp_rechaza_siYaEstaAnulado() {
        AfpParametroVigencia v = vigenciaAfpVigente();
        v.setEstado("ANULADO");
        when(afpVigenciaRepo.findById(10L)).thenReturn(Optional.of(v));

        assertThatThrownBy(() ->
            service.anularAfpVigencia(10L, "Intento de doble anulacion", "analista01"))
            .isInstanceOf(NegocioException.class)
            .hasMessageContaining("ya se encuentra anulada");
    }

    // ── Test 8: Registra log con ACCION = ANULAR ────────────────

    @Test
    void anularAfp_registraLogConAccionAnular() {
        AfpParametroVigencia v = vigenciaAfpVigente();
        when(afpVigenciaRepo.findById(10L)).thenReturn(Optional.of(v));
        when(movimientoPlanillaRepo.findDistinctAfpVigenciaIdsByPeriodo(anyString())).thenReturn(List.of());
        when(periodoPlanillaRepo.countPlanillasCerradasEnRango(anyString(), any())).thenReturn(0L);

        ArgumentCaptor<PrevisionalLog> logCaptor = ArgumentCaptor.forClass(PrevisionalLog.class);
        when(logRepo.save(logCaptor.capture())).thenAnswer(i -> i.getArgument(0));

        service.anularAfpVigencia(10L, "Vigencia duplicada por error operativo", "analista01");

        PrevisionalLog log = logCaptor.getValue();
        assertThat(log.getAccion()).isEqualTo("ANULAR");
        assertThat(log.getTipo()).isEqualTo("AFP");
        assertThat(log.getUsuario()).isEqualTo("analista01");
        assertThat(log.getDescripcion()).contains("Motivo:");
    }

    // ── Test 9: Listado principal excluye ANULADO ───────────────

    @Test
    void listarAfpParametros_sinIncluirAnulados_noDevuelveAnulado() {
        AfpParametroVigencia vigente   = vigenciaAfpVigente();
        AfpParametroVigencia anulada   = vigenciaAfpVigente();
        anulada.setId(99L);
        anulada.setEstado("ANULADO");

        // findAllWithAfp ya filtra ANULADO en el repo — devuelve solo vigente
        when(afpVigenciaRepo.findAllWithAfp()).thenReturn(List.of(vigente));

        List<?> result = service.listarAfpParametros(null, false);

        assertThat(result).hasSize(1);
        verify(afpVigenciaRepo).findAllWithAfp();
        verify(afpVigenciaRepo, never()).findAllWithAfpIncluirAnulados();
    }

    // ── Test 10: Historial/auditoría incluye ANULADO ─────────────

    @Test
    void listarAfpParametros_conIncluirAnulados_devuelveAnulado() {
        AfpParametroVigencia vigente = vigenciaAfpVigente();
        AfpParametroVigencia anulada = vigenciaAfpVigente();
        anulada.setId(99L);
        anulada.setEstado("ANULADO");

        when(afpVigenciaRepo.findAllWithAfpIncluirAnulados()).thenReturn(List.of(vigente, anulada));

        List<?> result = service.listarAfpParametros(null, true);

        assertThat(result).hasSize(2);
        verify(afpVigenciaRepo).findAllWithAfpIncluirAnulados();
        verify(afpVigenciaRepo, never()).findAllWithAfp();
    }
}
