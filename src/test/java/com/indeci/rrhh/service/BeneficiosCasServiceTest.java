package com.indeci.rrhh.service;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.BeneficioCasCalculadoDto;
import com.indeci.rrhh.entity.ReglaBeneficioCas;
import com.indeci.rrhh.repository.ReglaBeneficioCasRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * F2bis — Tests de BeneficiosCasService (aguinaldos CAS + bonif extraordinaria).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BeneficiosCasServiceTest {

    @Mock private ParametroRemunerativoService parametroService;
    @Mock private ReglaBeneficioCasRepository reglaRepository;
    @InjectMocks private BeneficiosCasService service;

    @BeforeEach
    void setUp() {
        // Default: flag OFF (replica application.properties default).
        ReflectionTestUtils.setField(service, "beneficiosCasEnabled", false);
    }

    // =================== Flag OFF ===================

    @Test
    void flag_off_devuelve_no_aplica_aunque_sea_julio_CAS() {
        ReflectionTestUtils.setField(service, "beneficiosCasEnabled", false);
        BeneficioCasCalculadoDto r = service.calcular("202607", "CAS");
        assertThat(r.aplica()).isFalse();
        assertThat(r.tipo()).isEqualTo("NO_APLICA");
        assertThat(r.total()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void isEnabled_refleja_el_flag() {
        ReflectionTestUtils.setField(service, "beneficiosCasEnabled", false);
        assertThat(service.isEnabled()).isFalse();
        ReflectionTestUtils.setField(service, "beneficiosCasEnabled", true);
        assertThat(service.isEnabled()).isTrue();
    }

    // =================== Filtros (régimen / mes) ===================

    @Test
    void flag_on_regimen_728_no_aplica() {
        ReflectionTestUtils.setField(service, "beneficiosCasEnabled", true);
        assertThat(service.calcular("202607", "728").aplica()).isFalse();
        assertThat(service.calcular("202612", "276").aplica()).isFalse();
        assertThat(service.calcular("202607", "SERVIR").aplica()).isFalse();
    }

    @Test
    void flag_on_CAS_pero_mes_distinto_de_julio_y_diciembre_no_aplica() {
        ReflectionTestUtils.setField(service, "beneficiosCasEnabled", true);
        for (String periodo : new String[]{
                "202601", "202602", "202603", "202604", "202605", "202606",
                "202608", "202609", "202610", "202611"}) {
            BeneficioCasCalculadoDto r = service.calcular(periodo, "CAS");
            assertThat(r.aplica()).as("Mes %s no debe aplicar", periodo).isFalse();
        }
    }

    // =================== Julio (Fiestas Patrias) ===================

    @Test
    void flag_on_CAS_julio_aplica_aguinaldo_y_bonificacion_extraord() {
        ReflectionTestUtils.setField(service, "beneficiosCasEnabled", true);
        when(parametroService.obtenerValorOpcionalEnFecha(
                eq("AGUINALDO_CAS_JULIO"), any(LocalDate.class), eq(null)))
                .thenReturn(Optional.of(new BigDecimal("300.00")));
        when(parametroService.obtenerValorOpcionalEnFecha(
                eq("BONIF_EXTRAORD_PCT"), any(LocalDate.class), eq(null)))
                .thenReturn(Optional.of(new BigDecimal("0.09")));

        BeneficioCasCalculadoDto r = service.calcular("202607", "CAS");

        assertThat(r.aplica()).isTrue();
        assertThat(r.tipo()).isEqualTo("AGUINALDO_JULIO");
        assertThat(r.montoAguinaldo()).isEqualByComparingTo("300.00");
        // 300 × 0.09 = 27.00
        assertThat(r.montoBonifExtraord()).isEqualByComparingTo("27.00");
        assertThat(r.total()).isEqualByComparingTo("327.00");
    }

    @Test
    void flag_on_CAS_julio_acepta_periodo_con_guion() {
        ReflectionTestUtils.setField(service, "beneficiosCasEnabled", true);
        when(parametroService.obtenerValorOpcionalEnFecha(
                eq("AGUINALDO_CAS_JULIO"), any(LocalDate.class), eq(null)))
                .thenReturn(Optional.of(new BigDecimal("300.00")));

        BeneficioCasCalculadoDto r = service.calcular("2026-07", "CAS");
        assertThat(r.aplica()).isTrue();
        assertThat(r.tipo()).isEqualTo("AGUINALDO_JULIO");
    }

    // =================== Diciembre (Navidad) ===================

    @Test
    void flag_on_CAS_diciembre_usa_parametro_AGUINALDO_DICIEMBRE() {
        ReflectionTestUtils.setField(service, "beneficiosCasEnabled", true);
        when(parametroService.obtenerValorOpcionalEnFecha(
                eq("AGUINALDO_CAS_DICIEMBRE"), any(LocalDate.class), eq(null)))
                .thenReturn(Optional.of(new BigDecimal("300.00")));
        when(parametroService.obtenerValorOpcionalEnFecha(
                eq("BONIF_EXTRAORD_PCT"), any(LocalDate.class), eq(null)))
                .thenReturn(Optional.of(new BigDecimal("0.09")));

        BeneficioCasCalculadoDto r = service.calcular("202612", "CAS");

        assertThat(r.aplica()).isTrue();
        assertThat(r.tipo()).isEqualTo("AGUINALDO_DICIEMBRE");
        assertThat(r.montoAguinaldo()).isEqualByComparingTo("300.00");
        assertThat(r.montoBonifExtraord()).isEqualByComparingTo("27.00");
    }

    // =================== Defensivos ===================

    @Test
    void flag_on_CAS_julio_sin_parametros_sembrados_devuelve_cero_no_lanza() {
        // Caso típico hasta que MEF publique el DS: parámetros opcionales vacíos.
        ReflectionTestUtils.setField(service, "beneficiosCasEnabled", true);
        when(parametroService.obtenerValorOpcionalEnFecha(any(), any(), any()))
                .thenReturn(Optional.empty());

        BeneficioCasCalculadoDto r = service.calcular("202607", "CAS");

        assertThat(r.aplica()).isTrue();
        assertThat(r.tipo()).isEqualTo("AGUINALDO_JULIO");
        assertThat(r.montoAguinaldo()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(r.montoBonifExtraord()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(r.total()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void flag_on_CAS_julio_aguinaldo_sin_bonif_pct_devuelve_aguinaldo_solo() {
        ReflectionTestUtils.setField(service, "beneficiosCasEnabled", true);
        when(parametroService.obtenerValorOpcionalEnFecha(
                eq("AGUINALDO_CAS_JULIO"), any(LocalDate.class), eq(null)))
                .thenReturn(Optional.of(new BigDecimal("300.00")));
        // BONIF_EXTRAORD_PCT no sembrado.
        when(parametroService.obtenerValorOpcionalEnFecha(
                eq("BONIF_EXTRAORD_PCT"), any(LocalDate.class), eq(null)))
                .thenReturn(Optional.empty());

        BeneficioCasCalculadoDto r = service.calcular("202607", "CAS");

        assertThat(r.montoAguinaldo()).isEqualByComparingTo("300.00");
        assertThat(r.montoBonifExtraord()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(r.total()).isEqualByComparingTo("300.00");
    }

    @Test
    void regimen_cas_case_insensitive() {
        ReflectionTestUtils.setField(service, "beneficiosCasEnabled", true);
        when(parametroService.obtenerValorOpcionalEnFecha(any(), any(), any()))
                .thenReturn(Optional.empty());

        assertThat(service.calcular("202607", "cas").aplica()).isTrue();
        assertThat(service.calcular("202607", "Cas").aplica()).isTrue();
    }

    @Test
    void periodo_invalido_lanza_negocio() {
        ReflectionTestUtils.setField(service, "beneficiosCasEnabled", true);
        assertThatThrownBy(() -> service.calcular("2026", "CAS"))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("Período inválido");

        assertThatThrownBy(() -> service.calcular("202613", "CAS"))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("Período inválido");

        assertThatThrownBy(() -> service.calcular(null, "CAS"))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("Período requerido");
    }

    // =================== Track B F4 — Gratificación CAS 100% (Ley 32563) ===================

    private ReglaBeneficioCas regla(String codigo, String mef) {
        ReglaBeneficioCas r = new ReglaBeneficioCas();
        r.setCodigoBeneficio(codigo);
        r.setCodigoMef(mef);
        r.setMontoTipo("PCT_REMUNERACION");
        r.setFactor(new BigDecimal("1.0000"));
        r.setEstado("ACTIVO");
        return r;
    }

    @Test
    void gratificacion_julio_cas_fiestas_patrias_100pct() {
        when(reglaRepository.findGratificacionesVigentes(eq("CAS"), eq(7), any()))
                .thenReturn(java.util.List.of(regla("GRATIFICACION_FIESTAS_PATRIAS_CAS", "0077")));

        var r = service.calcularGratificacion("2026-07", "CAS", new BigDecimal("3000.00"));

        assertThat(r).isPresent();
        assertThat(r.get().codigoBeneficio()).isEqualTo("GRATIFICACION_FIESTAS_PATRIAS_CAS");
        assertThat(r.get().codigoMef()).isEqualTo("0077");
        assertThat(r.get().monto()).isEqualByComparingTo("3000.00");
    }

    @Test
    void gratificacion_diciembre_cas_navidad_100pct() {
        when(reglaRepository.findGratificacionesVigentes(eq("CAS"), eq(12), any()))
                .thenReturn(java.util.List.of(regla("GRATIFICACION_NAVIDAD_CAS", "0025")));

        var r = service.calcularGratificacion("2026-12", "CAS", new BigDecimal("4864.19"));

        assertThat(r).isPresent();
        assertThat(r.get().codigoBeneficio()).isEqualTo("GRATIFICACION_NAVIDAD_CAS");
        assertThat(r.get().monto()).isEqualByComparingTo("4864.19");
    }

    @Test
    void gratificacion_mes_normal_no_genera() {
        assertThat(service.calcularGratificacion("2026-05", "CAS", new BigDecimal("3000")))
                .isEmpty();
    }

    @Test
    void gratificacion_regimen_no_cas_no_genera() {
        assertThat(service.calcularGratificacion("2026-07", "276", new BigDecimal("3000")))
                .isEmpty();
    }

    @Test
    void gratificacion_sin_regla_vigente_no_genera() {
        when(reglaRepository.findGratificacionesVigentes(eq("CAS"), eq(7), any()))
                .thenReturn(java.util.List.of());

        assertThat(service.calcularGratificacion("2026-07", "CAS", new BigDecimal("3000")))
                .isEmpty();
    }
}
