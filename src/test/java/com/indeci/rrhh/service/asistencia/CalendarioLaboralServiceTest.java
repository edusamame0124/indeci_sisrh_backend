package com.indeci.rrhh.service.asistencia;

import com.indeci.rrhh.service.asistencia.CalendarioLaboralService.Calendario;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CalendarioLaboralServiceTest {

    // Junio 2026: Lun 1, Sáb 6, Dom 7, Lun 29 (San Pedro) — verificado vs reporte real.
    private static final LocalDate INICIO = LocalDate.of(2026, 6, 1);
    private static final LocalDate FIN = LocalDate.of(2026, 6, 30);

    @Test
    void diasFalta_excluyeFeriadosDescansosYDiasPresentes() {
        Calendario cal = new Calendario(
                Set.of(LocalDate.of(2026, 6, 29)),   // feriado San Pedro
                Set.of(6, 7),                         // descanso sábado + domingo
                Map.of());
        Set<LocalDate> presentes = Set.of(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2));

        List<LocalDate> faltas = cal.diasFalta(INICIO, FIN, null, null, null, presentes);

        assertThat(faltas)
                .doesNotContain(LocalDate.of(2026, 6, 1))   // presente
                .doesNotContain(LocalDate.of(2026, 6, 6))   // sábado
                .doesNotContain(LocalDate.of(2026, 6, 7))   // domingo
                .doesNotContain(LocalDate.of(2026, 6, 29))  // feriado
                .contains(LocalDate.of(2026, 6, 3));        // miércoles laborable sin marca
    }

    @Test
    void diasFalta_respetaVinculoIngreso() {
        Calendario cal = new Calendario(Set.of(), Set.of(6, 7), Map.of());

        List<LocalDate> faltas = cal.diasFalta(
                INICIO, FIN, LocalDate.of(2026, 6, 15), null, null, Set.of());

        assertThat(faltas).noneMatch(f -> f.isBefore(LocalDate.of(2026, 6, 15)));
        assertThat(faltas).contains(LocalDate.of(2026, 6, 15)); // lunes de ingreso
    }

    @Test
    void diasFalta_respetaVinculoCese() {
        Calendario cal = new Calendario(Set.of(), Set.of(6, 7), Map.of());

        List<LocalDate> faltas = cal.diasFalta(
                INICIO, FIN, null, LocalDate.of(2026, 6, 10), null, Set.of());

        assertThat(faltas).noneMatch(f -> f.isAfter(LocalDate.of(2026, 6, 10)));
    }

    @Test
    void esDescanso_globalYPorRegimen() {
        // Global: solo domingo(7). Régimen 5 además descansa sábado(6).
        Calendario cal = new Calendario(Set.of(), Set.of(7), Map.of(5L, Set.of(6)));

        assertThat(cal.esDescanso(LocalDate.of(2026, 6, 7), 9L)).isTrue();   // domingo global
        assertThat(cal.esDescanso(LocalDate.of(2026, 6, 6), 5L)).isTrue();   // sábado régimen 5
        assertThat(cal.esDescanso(LocalDate.of(2026, 6, 6), 9L)).isFalse();  // sábado otro régimen
    }

    @Test
    void esLaborable_feriadoNoEsLaborable() {
        Calendario cal = new Calendario(Set.of(LocalDate.of(2026, 6, 29)), Set.of(6, 7), Map.of());

        assertThat(cal.esLaborable(LocalDate.of(2026, 6, 29), null)).isFalse(); // feriado
        assertThat(cal.esLaborable(LocalDate.of(2026, 6, 3), null)).isTrue();   // miércoles
    }
}
