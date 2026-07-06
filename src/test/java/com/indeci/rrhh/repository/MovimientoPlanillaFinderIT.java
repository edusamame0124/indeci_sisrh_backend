package com.indeci.rrhh.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import com.indeci.rrhh.entity.MovimientoPlanilla;

/**
 * Track B — Regresión del finder del motor regular: {@code findByEmpleadoIdAndPeriodoAndActivo}
 * EXCLUYE el movimiento AGUINALDO (excepción quirúrgica al #7), aun cuando el
 * aguinaldo tenga id MAYOR que la regular. Así, regenerar la regular no toma ni
 * pisa el aguinaldo.
 *
 * <p>*IT: se excluye de {@code mvn test} por defecto (contexto Spring + H2
 * compartida). On-demand: {@code mvnw test -Dtest=MovimientoPlanillaFinderIT}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class MovimientoPlanillaFinderIT {

    private static final Long EMP = 900L;
    private static final String PERIODO = "2026-07";

    @Autowired private MovimientoPlanillaRepository repo;

    private MovimientoPlanilla guardar(String tipoPlanilla) {
        MovimientoPlanilla m = new MovimientoPlanilla();
        m.setEmpleadoId(EMP);
        m.setPeriodo(PERIODO);
        m.setTipoPlanilla(tipoPlanilla);
        m.setActivo(1);
        m.setEstado("GENERADO");
        m.setTotalIngresos(100.0);
        m.setTotalDescuentos(0.0);
        m.setNetoPagar(100.0);
        return repo.save(m);
    }

    @Test
    void periodo_sin_aguinaldo_devuelve_el_regular() {
        MovimientoPlanilla reg = guardar("ORDINARIA");

        Optional<MovimientoPlanilla> found =
                repo.findByEmpleadoIdAndPeriodoAndActivo(EMP, PERIODO, 1);

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(reg.getId());
    }

    @Test
    void con_aguinaldo_de_id_mayor_el_finder_devuelve_la_regular_y_el_aguinaldo_queda_intacto() {
        MovimientoPlanilla reg = guardar("ORDINARIA");   // id menor
        MovimientoPlanilla agui = guardar("AGUINALDO");  // id mayor (guardado después)

        // (b) El finder del motor NO toma el aguinaldo, aunque tenga id mayor.
        Optional<MovimientoPlanilla> found =
                repo.findByEmpleadoIdAndPeriodoAndActivo(EMP, PERIODO, 1);
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(reg.getId());
        assertThat(found.get().getTipoPlanilla()).isEqualTo("ORDINARIA");

        // El aguinaldo sigue íntegro (el motor no lo ve → borrarMovimientoAnterior no lo borra).
        assertThat(repo.findById(agui.getId())).isPresent();
        assertThat(repo.findAllByEmpleadoIdAndPeriodoAndActivo(EMP, PERIODO, 1)).hasSize(2);
    }
}
