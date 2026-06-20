package com.indeci.rrhh.service.asistencia;

import com.indeci.rrhh.dto.AsistenciaDiaDto;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class AsistenciaMarcadorMapperTest {

    @Test
    void mapeaFaltaDescansoFeriadoObservadoYTardanza() {
        AsistenciaDiaDto falta = AsistenciaMarcadorMapper.toDia(
                "Lun", LocalDate.of(2026, 5, 1), "08:00", "17:00", null, null, "08:00",
                0, 0, "Falta");
        assertThat(falta.getTipoDia()).isEqualTo("FALTA");

        AsistenciaDiaDto descanso = AsistenciaMarcadorMapper.toDia(
                "Dom", LocalDate.of(2026, 5, 4), "", "", null, null, "08:00",
                0, 0, "Descanso");
        assertThat(descanso.getTipoDia()).isEqualTo("DESCANSO");

        AsistenciaDiaDto feriado = AsistenciaMarcadorMapper.toDia(
                "Jue", LocalDate.of(2026, 4, 2), "", "", null, null, "08:00",
                0, 0, "Jueves Santo");
        assertThat(feriado.getTipoDia()).isEqualTo("FERIADO");

        AsistenciaDiaDto observado = AsistenciaMarcadorMapper.toDia(
                "Vie", LocalDate.of(2026, 5, 8), "", "", null, null, "08:00",
                0, 0, "Marca Incompleta");
        assertThat(observado.getTipoDia()).isEqualTo("OBSERVADO");

        AsistenciaDiaDto tardanza = AsistenciaMarcadorMapper.toDia(
                "Lun", LocalDate.of(2026, 5, 5), "08:10", "17:00", null, null, "08:00",
                10, 0, "");
        assertThat(tardanza.getTipoDia()).isEqualTo("TARDANZA");
        assertThat(tardanza.getMinutosTardanza()).isEqualTo(10);
    }

    @Test
    void observacionVaciaSinMarcas_esObservado() {
        AsistenciaDiaDto dia = AsistenciaMarcadorMapper.toDia(
                "Mar", LocalDate.of(2026, 5, 6), "", "", null, null, "08:00",
                0, 0, "");
        assertThat(dia.getTipoDia()).isEqualTo("OBSERVADO");
    }
}
