package com.indeci.rrhh.service;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.entity.ParametroRemunerativo;
import com.indeci.rrhh.repository.ParametroRemunerativoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParametroRemunerativoServiceTest {

    @Mock private ParametroRemunerativoRepository repository;
    @InjectMocks private ParametroRemunerativoService service;

    @Test
    void obtenerValor_prefiere_parametro_por_regimen_sobre_global() {
        when(repository.findVigenteByRegimen("TASA_ESPECIAL", 2026, 1L))
                .thenReturn(Optional.of(parametro(new BigDecimal("0.15"))));

        BigDecimal v = service.obtenerValor("TASA_ESPECIAL", 2026, 1L);
        assertThat(v).isEqualByComparingTo("0.15");
    }

    @Test
    void obtenerValor_fallback_global_cuando_no_hay_por_regimen() {
        when(repository.findVigenteByRegimen("TASA_ONP", 2026, 1L))
                .thenReturn(Optional.empty());
        when(repository.findVigenteGlobal("TASA_ONP", 2026))
                .thenReturn(Optional.of(parametro(new BigDecimal("0.13"))));

        BigDecimal v = service.obtenerValor("TASA_ONP", 2026, 1L);
        assertThat(v).isEqualByComparingTo("0.13");
    }

    @Test
    void obtenerValor_busca_global_cuando_regimen_es_null() {
        when(repository.findVigenteGlobal("UIT", 2026))
                .thenReturn(Optional.of(parametro(new BigDecimal("5350"))));

        BigDecimal v = service.obtenerValor("UIT", 2026, null);
        assertThat(v).isEqualByComparingTo("5350");
    }

    @Test
    void obtenerValor_lanza_negocio_si_no_existe() {
        when(repository.findVigenteByRegimen("FANTASMA", 2026, 1L))
                .thenReturn(Optional.empty());
        when(repository.findVigenteGlobal("FANTASMA", 2026))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtenerValor("FANTASMA", 2026, 1L))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("FANTASMA");
    }

    @Test
    void obtenerValorOpcional_retorna_empty_si_no_existe() {
        when(repository.findVigenteGlobal("VACIO", 2026))
                .thenReturn(Optional.empty());

        assertThat(service.obtenerValorOpcional("VACIO", 2026, null)).isEmpty();
    }

    // ======================================================================
    // F1.3a — Lookup por fecha de devengue (C4 RRHH: vigencias mensuales).
    // ======================================================================

    @Test
    void obtenerValorEnFecha_prefiere_regimen_sobre_global() {
        LocalDate fecha = LocalDate.of(2026, 5, 1);

        when(repository.findVigenteByRegimenEnFecha("TASA_ESPECIAL", 1L, fecha))
                .thenReturn(Optional.of(parametro(new BigDecimal("0.155"))));

        BigDecimal v = service.obtenerValorEnFecha("TASA_ESPECIAL", fecha, 1L);
        assertThat(v).isEqualByComparingTo("0.155");
    }

    @Test
    void obtenerValorEnFecha_fallback_global_cuando_no_hay_por_regimen() {
        LocalDate fecha = LocalDate.of(2026, 5, 1);

        when(repository.findVigenteByRegimenEnFecha("TOPE_SEGURO_AFP", 1L, fecha))
                .thenReturn(Optional.empty());
        when(repository.findVigenteGlobalEnFecha("TOPE_SEGURO_AFP", fecha))
                .thenReturn(Optional.of(parametro(new BigDecimal("12598.91"))));

        BigDecimal v = service.obtenerValorEnFecha("TOPE_SEGURO_AFP", fecha, 1L);
        assertThat(v).isEqualByComparingTo("12598.91");
    }

    @Test
    void obtenerValorEnFecha_lanza_negocio_si_no_vigente() {
        LocalDate fecha = LocalDate.of(2026, 5, 1);

        when(repository.findVigenteByRegimenEnFecha("FANTASMA", 1L, fecha))
                .thenReturn(Optional.empty());
        when(repository.findVigenteGlobalEnFecha("FANTASMA", fecha))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtenerValorEnFecha("FANTASMA", fecha, 1L))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("FANTASMA")
                .hasMessageContaining("2026-05-01");
    }

    @Test
    void obtenerValorEnPeriodo_deriva_fecha_inicio_del_mes() {
        // Período "202605" debe traducirse a 2026-05-01.
        LocalDate fechaEsperada = LocalDate.of(2026, 5, 1);

        when(repository.findVigenteGlobalEnFecha("TOPE_SEGURO_AFP", fechaEsperada))
                .thenReturn(Optional.of(parametro(new BigDecimal("12598.91"))));

        BigDecimal v = service.obtenerValorEnPeriodo("TOPE_SEGURO_AFP", "202605", null);
        assertThat(v).isEqualByComparingTo("12598.91");
    }

    @Test
    void obtenerValorEnPeriodo_lanza_si_periodo_invalido() {
        assertThatThrownBy(() -> service.obtenerValorEnPeriodo("RMV", "2026", null))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("Período inválido");

        assertThatThrownBy(() -> service.obtenerValorEnPeriodo("RMV", "202613", null))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("Período inválido");
    }

    @Test
    void obtenerValorOpcionalEnFecha_retorna_empty_si_no_vigente() {
        LocalDate fecha = LocalDate.of(2026, 5, 1);

        when(repository.findVigenteGlobalEnFecha("VACIO", fecha))
                .thenReturn(Optional.empty());

        assertThat(service.obtenerValorOpcionalEnFecha("VACIO", fecha, null)).isEmpty();
    }

    private ParametroRemunerativo parametro(BigDecimal valor) {
        ParametroRemunerativo p = new ParametroRemunerativo();
        p.setValorNumerico(valor);
        p.setActivo(1);
        return p;
    }
}
