package com.indeci.rrhh.service.asistencia;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TipoDiaAsistenciaTest {

    @Test
    void etiqueta_codigo_conocido_devuelve_texto_oficial() {
        assertThat(TipoDiaAsistencia.etiqueta("LABORAL")).isEqualTo("Presente");
        assertThat(TipoDiaAsistencia.etiqueta("TARDANZA")).isEqualTo("Tardío");
        assertThat(TipoDiaAsistencia.etiqueta("VACACIONES")).isEqualTo("Vacaciones");
    }

    @Test
    void etiqueta_codigo_desconocido_devuelve_el_codigo_tal_cual() {
        assertThat(TipoDiaAsistencia.etiqueta("XYZ_NO_EXISTE")).isEqualTo("XYZ_NO_EXISTE");
    }

    @Test
    void etiqueta_nulo_devuelve_nulo() {
        assertThat(TipoDiaAsistencia.etiqueta(null)).isNull();
    }
}
