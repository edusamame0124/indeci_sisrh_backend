package com.indeci.rrhh.service.evento.importacion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * V012_42 F2 — diccionario MOTIVO (Excel) → CODIGO tipo evento, decisión RR.HH. 2026-07-23:
 * SANCION PAD se agrupa con SUSPENSION PAD bajo SUSPENSION_HISTORICA.
 */
class EventoHistoricoMotivoMapperTest {

    private final EventoHistoricoMotivoMapper mapper = new EventoHistoricoMotivoMapper();

    @Test
    void licencia_sin_goce_mapea_al_tipo_operativo_existente() {
        assertThat(mapper.resolverCodigo("LICENCIA SIN GOCE"))
                .isEqualTo(EventoHistoricoMotivoMapper.COD_LICENCIA_SIN_GOCE);
    }

    @Test
    void inasistencia_injustificada_mapea_a_falta_historica() {
        assertThat(mapper.resolverCodigo("INASISTENCIA INJUSTIFICADA"))
                .isEqualTo(EventoHistoricoMotivoMapper.COD_FALTA_HISTORICA);
    }

    @Test
    void suspension_pad_mapea_a_suspension_historica() {
        assertThat(mapper.resolverCodigo("SUSPENSION PAD"))
                .isEqualTo(EventoHistoricoMotivoMapper.COD_SUSPENSION_HISTORICA);
    }

    @Test
    void sancion_pad_se_agrupa_con_suspension_historica() {
        assertThat(mapper.resolverCodigo("SANCION PAD"))
                .isEqualTo(EventoHistoricoMotivoMapper.COD_SUSPENSION_HISTORICA);
    }

    @Test
    void tolera_mayusculas_minusculas_y_espacios() {
        assertThat(mapper.resolverCodigo("  licencia sin goce  "))
                .isEqualTo(EventoHistoricoMotivoMapper.COD_LICENCIA_SIN_GOCE);
    }

    @Test
    void motivo_desconocido_devuelve_null_no_adivina() {
        assertThat(mapper.resolverCodigo("VACACIONES")).isNull();
        assertThat(mapper.resolverCodigo(null)).isNull();
    }
}
