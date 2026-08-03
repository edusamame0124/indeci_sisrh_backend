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

        // Regla SERVIR/INDECI: "Marca Incompleta" = omisión de marcación (no observado/falta).
        AsistenciaDiaDto omision = AsistenciaMarcadorMapper.toDia(
                "Vie", LocalDate.of(2026, 5, 8), "", "", null, null, "08:00",
                0, 0, "Marca Incompleta");
        assertThat(omision.getTipoDia()).isEqualTo("OMISION_MARCACION");

        // Una sola marca (entrada sin salida) también es omisión de marcación.
        AsistenciaDiaDto omisionUnaMarca = AsistenciaMarcadorMapper.toDia(
                "Lun", LocalDate.of(2026, 5, 11), "08:00", "", null, null, "08:00",
                0, 0, "");
        assertThat(omisionUnaMarca.getTipoDia()).isEqualTo("OMISION_MARCACION");

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

    // ── Catálogo de feriados (INDECI_FERIADO) — caso real: Fiestas Patrias / Día de la Fuerza
    // Aérea quedaban como LABORAL porque el mapeo por texto solo reconocía "jueves/viernes santo".

    @Test
    void feriadoDelCatalogo_conObservacionDelMarcadorNoReconocida_esFeriado() {
        // El marcador rotula el nombre real del feriado ("Fiestas Patrias"), que el matching de
        // texto legado no reconocía → antes caía a LABORAL. El catálogo lo corrige.
        AsistenciaDiaDto dia = AsistenciaMarcadorMapper.toDia(
                "Mar", LocalDate.of(2026, 7, 28), "", "", null, null, "08:00",
                0, 0, "Fiestas Patrias", true);
        assertThat(dia.getTipoDia()).isEqualTo("FERIADO");
        assertThat(dia.getObservacion()).isEqualTo("Fiestas Patrias");
    }

    @Test
    void feriadoDelCatalogo_sinObservacionDelMarcador_esFeriadoNoObservado() {
        // Antes: obs vacía + sin marcas → OBSERVADO genérico, aunque fuera un feriado real.
        AsistenciaDiaDto dia = AsistenciaMarcadorMapper.toDia(
                "Jue", LocalDate.of(2026, 7, 23), "", "", null, null, "08:00",
                0, 0, "", true);
        assertThat(dia.getTipoDia()).isEqualTo("FERIADO");
    }

    @Test
    void feriadoDelCatalogo_conMarcacionReal_siguePresenteONoDescuentaComoFeriadoTrabajado() {
        // La persona SÍ fichó ese día (feriado trabajado) — no se reclasifica a FERIADO, sigue
        // como día laborado normal (el pago especial de feriado trabajado es otro mecanismo).
        AsistenciaDiaDto dia = AsistenciaMarcadorMapper.toDia(
                "Mar", LocalDate.of(2026, 7, 28), "08:00", "17:00", null, null, "08:00",
                0, 0, "", true);
        assertThat(dia.getTipoDia()).isEqualTo("LABORAL");
    }

    @Test
    void fechaFueraDelCatalogoDeFeriados_noSeReclasifica() {
        AsistenciaDiaDto dia = AsistenciaMarcadorMapper.toDia(
                "Mar", LocalDate.of(2026, 7, 14), "", "", null, null, "08:00",
                0, 0, "", false);
        assertThat(dia.getTipoDia()).isEqualTo("OBSERVADO");
    }
}
