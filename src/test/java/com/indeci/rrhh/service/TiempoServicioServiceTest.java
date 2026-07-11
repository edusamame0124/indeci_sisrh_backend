package com.indeci.rrhh.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.indeci.exception.VinculoNoEncontradoException;
import com.indeci.rrhh.dto.TiempoServicioDto;
import com.indeci.rrhh.entity.EmpleadoPlanilla;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;

/**
 * SPEC_VACACIONES F1 — TiempoServicioService.
 * Incluye regresión numérica 1:1 contra filas reales de la MATRIZ_VACACIONES
 * del especialista (columnas L/M/N de la hoja DATOS).
 */
@ExtendWith(MockitoExtension.class)
class TiempoServicioServiceTest {

    @Mock private EmpleadoPlanillaRepository repo;
    @InjectMocks private TiempoServicioService service;

    private static final Long EMP = 42L;

    private EmpleadoPlanilla vinculo(LocalDate inicioContrato, LocalDate fin, LocalDate cese) {
        EmpleadoPlanilla v = new EmpleadoPlanilla();
        v.setEmpleadoId(EMP);
        v.setActivo(1);
        v.setFechaInicioContrato(inicioContrato);
        v.setFechaFin(fin);
        v.setFechaCese(cese);
        return v;
    }

    // ---------- T1: feliz, 1 vínculo vigente ----------
    @Test
    void t1_un_vinculo_vigente() {
        when(repo.findByEmpleadoIdAndActivo(EMP, 1))
                .thenReturn(List.of(vinculo(LocalDate.of(2020, 1, 1), null, null)));

        TiempoServicioDto r = service.calcular(EMP, LocalDate.of(2026, 1, 1));

        // Dias360(2020-01-01, 2026-01-01)=2160, +1 inclusivo = 2161 → 6a 0m 1d
        assertThat(r.anios()).isEqualTo(6);
        assertThat(r.meses()).isEqualTo(0);
        assertThat(r.dias()).isEqualTo(1);
        assertThat(r.tieneTraslape()).isFalse();
        assertThat(r.numVinculos()).isEqualTo(1);
    }

    // ---------- T2: cese usa fechaCese, no HOY ----------
    @Test
    void t2_usa_fecha_cese() {
        when(repo.findByEmpleadoIdAndActivo(EMP, 1))
                .thenReturn(List.of(vinculo(LocalDate.of(2022, 3, 15), null, LocalDate.of(2024, 3, 14))));

        TiempoServicioDto r = service.calcular(EMP, LocalDate.of(2026, 1, 1));

        // Dias360(2022-03-15, 2024-03-14)=719, +1 = 720 → 2a 0m 0d
        assertThat(r.anios()).isEqualTo(2);
        assertThat(r.meses()).isEqualTo(0);
        assertThat(r.dias()).isEqualTo(0);
        assertThat(r.fechaCorte()).isEqualTo(LocalDate.of(2026, 1, 1));
    }

    // ---------- T3: 2 vínculos secuenciales sin traslape ----------
    @Test
    void t3_dos_vinculos_sin_traslape() {
        when(repo.findByEmpleadoIdAndActivo(EMP, 1)).thenReturn(List.of(
                vinculo(LocalDate.of(2018, 1, 1), LocalDate.of(2019, 12, 31), null),
                vinculo(LocalDate.of(2021, 1, 1), null, null)));

        TiempoServicioDto r = service.calcular(EMP, LocalDate.of(2023, 1, 1));

        // Intervalo A: 2018-01-01..2019-12-31 → Dias360 720, +1 = 721
        // Intervalo B: 2021-01-01..2023-01-01 → Dias360 720, +1 = 721
        // Hueco 2020 EXCLUIDO. Total 1442 → 4a 0m 2d (cada intervalo cuenta sus 2 extremos).
        assertThat(r.anios()).isEqualTo(4);
        assertThat(r.dias()).isEqualTo(2);
        assertThat(r.tieneTraslape()).isFalse();
    }

    // ---------- T4: traslape (rotación CAS) NO se doble-cuenta ----------
    @Test
    void t4_traslape_no_duplica() {
        when(repo.findByEmpleadoIdAndActivo(EMP, 1)).thenReturn(List.of(
                vinculo(LocalDate.of(2020, 1, 1), null, LocalDate.of(2022, 6, 30)), // cesado
                vinculo(LocalDate.of(2022, 4, 1), null, null)));                    // nuevo, traslapa abr-jun

        TiempoServicioDto r = service.calcular(EMP, LocalDate.of(2024, 1, 1));

        // Fusionado: 2020-01-01..2024-01-01 → Dias360=1440, +1=1441 → 4a 0m 1d
        // (sin fusión serían ~2 intervalos y se contaría abr-jun 2022 dos veces).
        assertThat(r.anios()).isEqualTo(4);
        assertThat(r.dias()).isEqualTo(1);
        assertThat(r.tieneTraslape()).isTrue();
    }

    // ---------- T7: sin vínculo activo → 404 ----------
    @Test
    void t7_sin_vinculo_lanza_404() {
        when(repo.findByEmpleadoIdAndActivo(EMP, 1)).thenReturn(List.of());

        assertThatThrownBy(() -> service.calcular(EMP, LocalDate.of(2026, 1, 1)))
                .isInstanceOf(VinculoNoEncontradoException.class);
    }

    // ---------- T8: inicio null (fechaInicioContrato y fechaIngreso nulos) → descartado → 404 ----------
    @Test
    void t8_inicio_null_se_descarta() {
        EmpleadoPlanilla sinFecha = vinculo(null, null, null); // sin ancla
        when(repo.findByEmpleadoIdAndActivo(EMP, 1)).thenReturn(List.of(sinFecha));

        assertThatThrownBy(() -> service.calcular(EMP, LocalDate.of(2026, 1, 1)))
                .isInstanceOf(VinculoNoEncontradoException.class);
    }

    // ---------- T8b: usa fechaIngreso legacy si fechaInicioContrato es null ----------
    @Test
    void t8b_respaldo_fecha_ingreso_legacy() {
        EmpleadoPlanilla v = vinculo(null, null, null);
        v.setFechaIngreso(LocalDate.of(2020, 1, 1)); // legacy poblada
        when(repo.findByEmpleadoIdAndActivo(EMP, 1)).thenReturn(List.of(v));

        TiempoServicioDto r = service.calcular(EMP, LocalDate.of(2026, 1, 1));
        assertThat(r.anios()).isEqualTo(6); // usa fechaIngreso como ancla
    }

    // ================= REGRESIÓN CONTRA EL EXCEL DEL ESPECIALISTA =================
    // Hoja DATOS: fechas seriales Excel convertidas; columnas L/M/N esperadas.

    // ---------- T9: AGUIRRE HUAMANI (ingreso 2018-12-14, corte 2026-05-30) → L=7 M=5 N=17 ----------
    @Test
    void t9_regresion_excel_aguirre() {
        when(repo.findByEmpleadoIdAndActivo(EMP, 1))
                .thenReturn(List.of(vinculo(LocalDate.of(2018, 12, 14), null, null)));

        TiempoServicioDto r = service.calcular(EMP, LocalDate.of(2026, 5, 30));

        assertThat(r.anios()).isEqualTo(7);
        assertThat(r.meses()).isEqualTo(5);
        assertThat(r.dias()).isEqualTo(17);
    }

    // ---------- T10: ACAL HERRERA (ingreso 2019-09-10, corte 2026-05-30) → L=6 M=8 N=21 ----------
    @Test
    void t10_regresion_excel_acal() {
        when(repo.findByEmpleadoIdAndActivo(EMP, 1))
                .thenReturn(List.of(vinculo(LocalDate.of(2019, 9, 10), null, null)));

        TiempoServicioDto r = service.calcular(EMP, LocalDate.of(2026, 5, 30));

        assertThat(r.anios()).isEqualTo(6);
        assertThat(r.meses()).isEqualTo(8);
        assertThat(r.dias()).isEqualTo(21);
    }

    // ---------- T11: ALARCON GODOY (ingreso 2016-11-03, corte 2026-05-30) → L=9 M=6 N=28 ----------
    @Test
    void t11_regresion_excel_alarcon() {
        when(repo.findByEmpleadoIdAndActivo(EMP, 1))
                .thenReturn(List.of(vinculo(LocalDate.of(2016, 11, 3), null, null)));

        TiempoServicioDto r = service.calcular(EMP, LocalDate.of(2026, 5, 30));

        assertThat(r.anios()).isEqualTo(9);
        assertThat(r.meses()).isEqualTo(6);
        assertThat(r.dias()).isEqualTo(28);
    }
}
