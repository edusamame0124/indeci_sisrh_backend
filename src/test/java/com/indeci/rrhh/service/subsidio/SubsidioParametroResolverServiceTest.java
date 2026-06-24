package com.indeci.rrhh.service.subsidio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.indeci.rrhh.entity.SubsidioParametro;
import com.indeci.rrhh.entity.SubsidioParametroVersion;
import com.indeci.rrhh.repository.SubsidioParametroRepository;
import com.indeci.rrhh.repository.SubsidioParametroVersionRepository;
import com.indeci.rrhh.service.ParametroRemunerativoService;

/**
 * Tests del resolutor de parámetros versionados de subsidios. Prioridad: versión
 * vigente del catálogo propio → fallback al catálogo remunerativo general → 0.
 * También arma el mapa de la fórmula (incluido TOPE_MENSUAL = UIT × %).
 */
@ExtendWith(MockitoExtension.class)
class SubsidioParametroResolverServiceTest {

    private static final LocalDate FECHA = LocalDate.of(2026, 5, 1);
    private static final int ANIO = 2026;

    @Mock private SubsidioParametroRepository parametroRepository;
    @Mock private SubsidioParametroVersionRepository versionRepository;
    @Mock private ParametroRemunerativoService parametroRemunerativoService;

    private SubsidioParametroResolverService service;

    @BeforeEach
    void setUp() {
        service = new SubsidioParametroResolverService(
                parametroRepository, versionRepository, parametroRemunerativoService);
    }

    @Test
    void version_vigente_propia_tiene_prioridad_sobre_fallback() {
        SubsidioParametro param = parametro(1L);
        when(parametroRepository.findByCodigoAndActivo("BIM_PCT_CAS", 1))
                .thenReturn(Optional.of(param));
        when(versionRepository.findVigente(1L, FECHA))
                .thenReturn(Optional.of(version(new BigDecimal("0.45"))));

        BigDecimal valor = service.obtenerNumerico("BIM_PCT_CAS", FECHA, ANIO);

        assertThat(valor).isEqualByComparingTo("0.45");
    }

    @Test
    void sin_version_vigente_cae_al_catalogo_general() {
        when(parametroRepository.findByCodigoAndActivo("BIM_PCT_CAS", 1))
                .thenReturn(Optional.of(parametro(1L)));
        when(versionRepository.findVigente(1L, FECHA)).thenReturn(Optional.empty());
        when(parametroRemunerativoService.obtenerValor(eq("SUBSIDIO_TOPE_PCT_UIT"), eq(ANIO), isNull()))
                .thenReturn(new BigDecimal("0.45"));

        BigDecimal valor = service.obtenerNumerico("BIM_PCT_CAS", FECHA, ANIO);

        assertThat(valor).isEqualByComparingTo("0.45");
    }

    @Test
    void parametro_inexistente_con_fallback_consulta_catalogo_general() {
        when(parametroRepository.findByCodigoAndActivo("UIT_REF", 1)).thenReturn(Optional.empty());
        when(parametroRemunerativoService.obtenerValor(eq("UIT"), eq(ANIO), isNull()))
                .thenReturn(new BigDecimal("5500"));

        BigDecimal valor = service.obtenerNumerico("UIT_REF", FECHA, ANIO);

        assertThat(valor).isEqualByComparingTo("5500");
    }

    @Test
    void codigo_sin_fallback_definido_retorna_cero() {
        when(parametroRepository.findByCodigoAndActivo("CODIGO_DESCONOCIDO", 1))
                .thenReturn(Optional.empty());

        BigDecimal valor = service.obtenerNumerico("CODIGO_DESCONOCIDO", FECHA, ANIO);

        assertThat(valor).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void mapa_numerico_arma_tope_mensual_como_uit_por_porcentaje() {
        // Sin parámetros propios: todo resuelve por fallback al catálogo general.
        when(parametroRepository.findByCodigoAndActivo(any(), eq(1))).thenReturn(Optional.empty());
        when(parametroRemunerativoService.obtenerValor(eq("SUBSIDIO_TOPE_PCT_UIT"), anyInt(), isNull()))
                .thenReturn(new BigDecimal("0.45"));
        when(parametroRemunerativoService.obtenerValor(eq("UIT"), anyInt(), isNull()))
                .thenReturn(new BigDecimal("5500"));
        // Estos fallbacks se consultan pero no afectan la aserción principal.
        lenient().when(parametroRemunerativoService.obtenerValor(
                eq("SUBSIDIO_DIVISOR_PROMEDIO"), anyInt(), isNull())).thenReturn(new BigDecimal("360"));
        lenient().when(parametroRemunerativoService.obtenerValor(
                eq("SUBSIDIO_DIAS_ASUME_ENTIDAD_ENFERMEDAD"), anyInt(), isNull()))
                .thenReturn(new BigDecimal("20"));
        lenient().when(parametroRemunerativoService.obtenerValor(
                eq("SUBSIDIO_DIAS_ASUME_ENTIDAD_MATERNIDAD"), anyInt(), isNull()))
                .thenReturn(BigDecimal.ZERO);

        Map<String, BigDecimal> mapa = service.mapaNumerico(FECHA, ANIO);

        assertThat(mapa.get("UIT_REF")).isEqualByComparingTo("5500");
        assertThat(mapa.get("BIM_PCT_CAS")).isEqualByComparingTo("0.45");
        // 5500 × 0.45 = 2475.00
        assertThat(mapa.get("TOPE_MENSUAL")).isEqualByComparingTo("2475.00");
        assertThat(mapa.get("DIVISOR_PROMEDIO")).isEqualByComparingTo("360");
        assertThat(mapa.get("DIAS_ENTIDAD_ENF")).isEqualByComparingTo("20");
        // Código sin fallback ni versión → cero.
        assertThat(mapa.get("GAP_CITT_DIAS")).isEqualByComparingTo(BigDecimal.ZERO);
    }

    private static SubsidioParametro parametro(Long id) {
        SubsidioParametro p = new SubsidioParametro();
        p.setId(id);
        p.setCodigo("BIM_PCT_CAS");
        p.setActivo(1);
        return p;
    }

    private static SubsidioParametroVersion version(BigDecimal valor) {
        SubsidioParametroVersion v = new SubsidioParametroVersion();
        v.setId(50L);
        v.setValorNumerico(valor);
        return v;
    }
}
