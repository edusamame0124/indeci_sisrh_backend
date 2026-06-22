package com.indeci.rrhh.service.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Regla de aplicabilidad incrementos DS — plan config-remunerativa-ds.
 */
class IncrementosDsAplicabilidadHelperTest {

    @Test
    void regimen_276_nunca_aplica_aunque_condicion_sea_nombrado_o_contratado() {
        assertThat(IncrementosDsAplicabilidadHelper.aplica("276", "NOMBRADO")).isFalse();
        assertThat(IncrementosDsAplicabilidadHelper.aplica("276", "CONTRATADO")).isFalse();
        assertThat(IncrementosDsAplicabilidadHelper.aplica("276", null)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "728, NOMBRADO",
            "728, CONTRATADO",
            "728,"
    })
    void regimen_728_aplica_con_cualquier_condicion(String regimen, String condicion) {
        assertThat(IncrementosDsAplicabilidadHelper.aplica(regimen, blankToNull(condicion))).isTrue();
    }

    @Test
    void condicion_CAS_activa_incrementos_incluso_si_regimen_no_esta_en_lista_explicita() {
        assertThat(IncrementosDsAplicabilidadHelper.aplica("999", "CAS")).isTrue();
        assertThat(IncrementosDsAplicabilidadHelper.aplica("276", "CAS")).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "SERVIR,",
            "1057,",
            "CAS,",
            "29709,",
            "28091,"
    })
    void regimenes_elegibles_aplican_sin_condicion_cas(String regimen, String condicion) {
        assertThat(IncrementosDsAplicabilidadHelper.aplica(regimen, blankToNull(condicion))).isTrue();
    }

    @Test
    void regimen_desconocido_sin_condicion_cas_no_aplica() {
        assertThat(IncrementosDsAplicabilidadHelper.aplica("999", null)).isFalse();
        assertThat(IncrementosDsAplicabilidadHelper.aplica(null, null)).isFalse();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
