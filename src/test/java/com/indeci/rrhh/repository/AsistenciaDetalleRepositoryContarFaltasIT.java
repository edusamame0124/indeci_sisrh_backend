package com.indeci.rrhh.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import com.indeci.rrhh.entity.AsistenciaCabecera;
import com.indeci.rrhh.entity.AsistenciaDetalle;

/**
 * SPEC_VACACIONES F9.1 — verifica contra H2 real que
 * {@link AsistenciaDetalleRepository#contarFaltas} cuenta tanto FALTA como
 * SANCION_PAD (2026-07), sin duplicar otros tipos de día (caso borde).
 *
 * <p>Nombrado *IT: excluido de {@code mvn test} por defecto (ver
 * {@link B3RepositoryDataJpaIT}). Ejecutar on-demand:
 * {@code mvnw test -Dtest=AsistenciaDetalleRepositoryContarFaltasIT}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class AsistenciaDetalleRepositoryContarFaltasIT {

    @Autowired private AsistenciaCabeceraRepository cabeceraRepository;
    @Autowired private AsistenciaDetalleRepository detalleRepository;

    private static final Long EMPLEADO_ID = 900L;

    @Test
    void contarFaltas_suma_falta_y_sancion_pad_caso_feliz() {
        Long cabeceraId = cabecera("2026-06");
        detalleRepository.save(detalle(cabeceraId, LocalDate.of(2026, 6, 8), "FALTA"));
        detalleRepository.save(detalle(cabeceraId, LocalDate.of(2026, 6, 9), "SANCION_PAD"));
        detalleRepository.save(detalle(cabeceraId, LocalDate.of(2026, 6, 10), "LABORAL"));

        long total = detalleRepository.contarFaltas(
                EMPLEADO_ID, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));

        assertThat(total).isEqualTo(2);
    }

    @Test
    void contarFaltas_sin_incidencias_devuelve_cero_caso_borde() {
        Long cabeceraId = cabecera("2026-07");
        detalleRepository.save(detalle(cabeceraId, LocalDate.of(2026, 7, 1), "LABORAL"));
        detalleRepository.save(detalle(cabeceraId, LocalDate.of(2026, 7, 2), "OBSERVADO"));

        long total = detalleRepository.contarFaltas(
                EMPLEADO_ID, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

        assertThat(total).isZero();
    }

    /**
     * Bug real (Melina, DNI 46112220, julio 2026): una re-importación del mismo mes deja la
     * versión anterior en activo=0 (single-active) y crea la nueva en activo=1, ambas con las
     * MISMAS faltas. Sin el filtro {@code cab.activo = 1} se contaban las dos (13+13 = duplicado).
     * Debe contar SOLO la versión vigente.
     */
    @Test
    void contarFaltas_ignora_version_desactivada_solo_cuenta_la_vigente() {
        // Versión v1 SUPERADA (activo=0) con 2 faltas.
        Long julioV1 = cabecera("2026-07", 0);
        detalleRepository.save(detalle(julioV1, LocalDate.of(2026, 7, 2), "FALTA"));
        detalleRepository.save(detalle(julioV1, LocalDate.of(2026, 7, 6), "FALTA"));

        // Versión v2 VIGENTE (activo=1) con las mismas 2 faltas.
        Long julioV2 = cabecera("2026-07", 1);
        detalleRepository.save(detalle(julioV2, LocalDate.of(2026, 7, 2), "FALTA"));
        detalleRepository.save(detalle(julioV2, LocalDate.of(2026, 7, 6), "FALTA"));

        long total = detalleRepository.contarFaltas(
                EMPLEADO_ID, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

        assertThat(total).as("solo la versión vigente (2), no las dos (4)").isEqualTo(2);
    }

    private Long cabecera(String periodo) {
        return cabecera(periodo, 1);
    }

    private Long cabecera(String periodo, int activo) {
        AsistenciaCabecera cab = new AsistenciaCabecera();
        cab.setEmpleadoId(EMPLEADO_ID);
        cab.setPeriodo(periodo);
        cab.setActivo(activo);
        return cabeceraRepository.save(cab).getId();
    }

    private AsistenciaDetalle detalle(Long cabeceraId, LocalDate dia, String tipo) {
        AsistenciaDetalle det = new AsistenciaDetalle();
        det.setCabeceraId(cabeceraId);
        det.setDia(dia);
        det.setTipoDia(tipo);
        if ("SANCION_PAD".equals(tipo)) {
            det.setObservacion("Expediente PAD N° 045-2026");
        }
        return det;
    }
}
