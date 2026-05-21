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

    private ParametroRemunerativo parametro(BigDecimal valor) {
        ParametroRemunerativo p = new ParametroRemunerativo();
        p.setValorNumerico(valor);
        p.setActivo(1);
        return p;
    }
}
