package com.indeci.rrhh.service.asistencia;

import com.indeci.rrhh.dto.AsistenciaDiaDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AsistenciaResumenCalculatorTest {

    @Test
    void observado_no_autorizado_cuenta_como_falta() {
        AsistenciaDiaDto dia = new AsistenciaDiaDto();
        dia.setTipoDia("OBSERVADO");
        dia.setPapeletaAutorizada(0);

        AsistenciaResumenCalculator.Resumen resumen =
                AsistenciaResumenCalculator.calcular(List.of(dia), 3000.0);

        assertThat(resumen.getDiasFalta()).isEqualTo(1);
        assertThat(resumen.getDescuentoFalta()).isEqualTo(100.0);
    }

    @Test
    void observado_sin_decision_papeleta_no_cuenta_como_falta() {
        AsistenciaDiaDto dia = new AsistenciaDiaDto();
        dia.setTipoDia("OBSERVADO");
        dia.setObservacion("Marca incompleta");

        AsistenciaResumenCalculator.Resumen resumen =
                AsistenciaResumenCalculator.calcular(List.of(dia), 3000.0);

        assertThat(resumen.getDiasFalta()).isZero();
        assertThat(resumen.getDescuentoFalta()).isZero();
        assertThat(resumen.getMarcasIncompletas()).isEqualTo(1);
    }

    @Test
    void observado_autorizado_no_cuenta_como_falta() {
        AsistenciaDiaDto dia = new AsistenciaDiaDto();
        dia.setTipoDia("OBSERVADO");
        dia.setPapeletaAutorizada(1);

        AsistenciaResumenCalculator.Resumen resumen =
                AsistenciaResumenCalculator.calcular(List.of(dia), 3000.0);

        assertThat(resumen.getDiasFalta()).isZero();
    }
}
