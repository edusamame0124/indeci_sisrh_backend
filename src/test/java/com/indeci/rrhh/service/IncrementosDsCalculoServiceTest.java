package com.indeci.rrhh.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.IncrementosDsResponseDto;
import com.indeci.rrhh.entity.CondicionLaboral;
import com.indeci.rrhh.entity.RegimenLaboral;
import com.indeci.rrhh.repository.CondicionLaboralRepository;
import com.indeci.rrhh.repository.RegimenLaboralRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IncrementosDsCalculoServiceTest {

    private static final LocalDate FECHA = LocalDate.of(2026, 6, 1);

    @Mock private ParametroRemunerativoService parametroService;
    @Mock private RegimenLaboralRepository regimenLaboralRepository;
    @Mock private CondicionLaboralRepository condicionLaboralRepository;
    @InjectMocks private IncrementosDsCalculoService service;

    @BeforeEach
    void catálogos_cas() {
        when(regimenLaboralRepository.findById(10L))
                .thenReturn(Optional.of(regimen("CAS")));
        when(condicionLaboralRepository.findById(20L))
                .thenReturn(Optional.of(condicion("CAS")));
        when(regimenLaboralRepository.findById(11L))
                .thenReturn(Optional.of(regimen("276")));
    }

    @Test
    void calcular_feliz_suma_cinco_parametros_y_remuneracion() {
        stubParametro("INCREMENTO_DS_311_2022", "64.19");
        stubParametro("INCREMENTO_DS_313_2023", "50.00");
        stubParametro("INCREMENTO_DS_265_2024", "50.00");
        stubParametro("INCREMENTO_DS_279_2024", "100.00");
        stubParametro("INCREMENTO_DS_327_2025", "100.00");

        IncrementosDsResponseDto r = service.calcular(10L, 20L, new BigDecimal("4500"), FECHA);

        assertThat(r.aplica()).isTrue();
        assertThat(r.montoContrato()).isEqualByComparingTo("4500.00");
        assertThat(r.incrementos()).hasSize(5);
        assertThat(r.totalIncrementos()).isEqualByComparingTo("364.19");
        assertThat(r.remuneracionMensual()).isEqualByComparingTo("4864.19");
    }

    @Test
    void calcular_sin_parametros_en_bd_usa_cero_defensivo() {
        when(parametroService.obtenerValorOpcionalEnFecha(any(), eq(FECHA), eq(null)))
                .thenReturn(Optional.empty());

        IncrementosDsResponseDto r = service.calcular(10L, 20L, new BigDecimal("4500"), FECHA);

        assertThat(r.aplica()).isTrue();
        assertThat(r.totalIncrementos()).isEqualByComparingTo("0.00");
        assertThat(r.remuneracionMensual()).isEqualByComparingTo("4500.00");
    }

    @Test
    void calcular_regimen_276_no_aplica_incrementos() {
        IncrementosDsResponseDto r = service.calcular(11L, null, new BigDecimal("3000"), FECHA);

        assertThat(r.aplica()).isFalse();
        assertThat(r.incrementos()).isEmpty();
        assertThat(r.totalIncrementos()).isEqualByComparingTo("0.00");
        assertThat(r.remuneracionMensual()).isEqualByComparingTo("3000.00");
    }

    @Test
    void calcular_monto_cero_lanza_negocio() {
        assertThatThrownBy(() -> service.calcular(10L, 20L, BigDecimal.ZERO, FECHA))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("Monto contratado");

        assertThatThrownBy(() -> service.calcular(10L, 20L, null, FECHA))
                .isInstanceOf(NegocioException.class);
    }

    private void stubParametro(String codigo, String valor) {
        when(parametroService.obtenerValorOpcionalEnFecha(eq(codigo), eq(FECHA), eq(null)))
                .thenReturn(Optional.of(new BigDecimal(valor)));
    }

    private static RegimenLaboral regimen(String codigo) {
        RegimenLaboral r = new RegimenLaboral();
        r.setCodigo(codigo);
        return r;
    }

    private static CondicionLaboral condicion(String codigo) {
        CondicionLaboral c = new CondicionLaboral();
        c.setCodigo(codigo);
        return c;
    }
}
