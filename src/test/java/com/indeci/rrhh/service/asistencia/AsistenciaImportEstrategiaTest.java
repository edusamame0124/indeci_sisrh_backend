package com.indeci.rrhh.service.asistencia;

import com.indeci.exception.NegocioException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AsistenciaImportEstrategiaTest {

    @Test
    void desde_vacio_usaOmitirExistentes() {
        assertThat(AsistenciaImportEstrategia.desde(null))
                .isEqualTo(AsistenciaImportEstrategia.OMITIR_EXISTENTES);
        assertThat(AsistenciaImportEstrategia.desde(""))
                .isEqualTo(AsistenciaImportEstrategia.OMITIR_EXISTENTES);
    }

    @Test
    void reemplazarPeriodoCompleto_esEstrategiaDeReemplazo() {
        AsistenciaImportEstrategia estrategia =
                AsistenciaImportEstrategia.desde("REEMPLAZAR_PERIODO_COMPLETO");

        assertThat(estrategia.reemplazaExistente()).isTrue();
        assertThat(estrategia.omiteExistente()).isFalse();
        assertThat(estrategia.cancelaConConflicto()).isFalse();
    }

    @Test
    void desde_valorNoReconocido_lanzaNegocioException() {
        assertThatThrownBy(() -> AsistenciaImportEstrategia.desde("BORRAR_TODO"))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("Estrategia");
    }
}
