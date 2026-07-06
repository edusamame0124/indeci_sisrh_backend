package com.indeci.rrhh.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import com.indeci.rrhh.entity.EmpleadoPlanilla;

/**
 * Fase 2 (vínculos secuenciales) — valida contra H2 real que
 * {@code findVinculosVigentesEnPeriodo} selecciona el vínculo cuyo rango
 * {@code [inicio, cese/fin]} traslapa el período generado, soportando la
 * rotación CAS (junio → CAS cesado, julio → CAS nuevo).
 *
 * <p>Nombrado *IT: se EXCLUYE de {@code mvn test} por defecto (misma razón que
 * {@link B3RepositoryDataJpaIT}: caché de contexto Spring + mem DB compartida).
 * Ejecutar on-demand: {@code mvnw test -Dtest=EmpleadoPlanillaVinculoPeriodoIT}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class EmpleadoPlanillaVinculoPeriodoIT {

    private static final Long EMP = 700L;

    @Autowired private EmpleadoPlanillaRepository repository;

    private EmpleadoPlanilla vinculo(Long empleadoId, int activo,
            LocalDate inicioContrato, LocalDate cese, LocalDate fin) {
        EmpleadoPlanilla p = new EmpleadoPlanilla();
        p.setEmpleadoId(empleadoId);
        p.setActivo(activo);
        p.setFechaInicioContrato(inicioContrato);
        p.setFechaCese(cese);
        p.setFechaFin(fin);
        p.setSueldoBasico(3000.0);
        return repository.save(p);
    }

    @Test
    void junio_toma_el_cas_cesado_y_no_el_nuevo() {
        EmpleadoPlanilla casCesado = vinculo(EMP, 1,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 15), null);
        vinculo(EMP, 1, LocalDate.of(2026, 7, 1), null, null); // CAS nuevo

        List<EmpleadoPlanilla> junio = repository.findVinculosVigentesEnPeriodo(
                EMP, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));

        assertThat(junio).extracting(EmpleadoPlanilla::getId)
                .containsExactly(casCesado.getId());
    }

    @Test
    void julio_toma_el_cas_nuevo_y_no_el_cesado() {
        vinculo(EMP, 1, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 15), null); // cesado
        EmpleadoPlanilla casNuevo =
                vinculo(EMP, 1, LocalDate.of(2026, 7, 1), null, null);

        List<EmpleadoPlanilla> julio = repository.findVinculosVigentesEnPeriodo(
                EMP, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

        assertThat(julio).extracting(EmpleadoPlanilla::getId)
                .containsExactly(casNuevo.getId());
    }

    @Test
    void periodo_sin_vinculo_que_traslape_no_devuelve_nada() {
        // Único vínculo cesado en junio; se genera agosto → sin traslape.
        vinculo(EMP, 1, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 15), null);

        List<EmpleadoPlanilla> agosto = repository.findVinculosVigentesEnPeriodo(
                EMP, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        assertThat(agosto).isEmpty();
    }

    @Test
    void vinculo_anulado_activo_cero_nunca_se_selecciona() {
        vinculo(EMP, 0, LocalDate.of(2026, 1, 1), null, null); // ANULADO

        List<EmpleadoPlanilla> junio = repository.findVinculosVigentesEnPeriodo(
                EMP, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));

        assertThat(junio).isEmpty();
    }
}
