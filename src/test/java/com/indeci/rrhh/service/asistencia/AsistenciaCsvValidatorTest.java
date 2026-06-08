package com.indeci.rrhh.service.asistencia;

import com.indeci.rrhh.entity.Empleado;
import com.indeci.rrhh.entity.PeriodoPlanilla;
import com.indeci.rrhh.entity.Persona;
import com.indeci.rrhh.repository.EmpleadoRepository;
import com.indeci.rrhh.repository.PersonaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AsistenciaCsvValidatorTest {

    @Mock private PersonaRepository personaRepository;
    @Mock private EmpleadoRepository empleadoRepository;

    @InjectMocks private AsistenciaCsvValidator validator;

    private PeriodoPlanilla periodo;

    @BeforeEach
    void setUp() {
        periodo = new PeriodoPlanilla();
        periodo.setFechaInicio(LocalDate.of(2026, 5, 1));
        periodo.setFechaFin(LocalDate.of(2026, 5, 31));
    }

    @Test
    void validaFila_fechaFueraDePeriodo_marcaError() {
        MarcadorCsvRow fila = filaBase("12345678", LocalDate.of(2026, 6, 1));

        validator.validarFila(fila, periodo);

        assertThat(fila.getEstadoFila()).isEqualTo("ERROR");
        assertThat(fila.getErrores()).anyMatch(m -> m.contains("período"));
    }

    @Test
    void validaFila_dniNoEncontrado_marcaError() {
        MarcadorCsvRow fila = filaBase("99999999", LocalDate.of(2026, 5, 10));
        when(personaRepository.findByDni("99999999")).thenReturn(Optional.empty());

        validator.validarFila(fila, periodo);

        assertThat(fila.getEstadoFila()).isEqualTo("ERROR");
        assertThat(fila.getErrores()).anyMatch(m -> m.contains("DNI no encontrado"));
    }

    @Test
    void validaFila_empleadoActivo_marcaObservadaSinMarcas() {
        MarcadorCsvRow fila = filaBase("12345678", LocalDate.of(2026, 5, 10));
        fila.setMarca1("");
        fila.setMarca2("");
        fila.setObservacion("");

        Persona persona = new Persona();
        persona.setId(1L);
        persona.setNombreCompleto("JUAN PEREZ");
        when(personaRepository.findByDni("12345678")).thenReturn(Optional.of(persona));

        Empleado empleado = new Empleado();
        empleado.setId(42L);
        empleado.setEstado("ACTIVO");
        when(empleadoRepository.findByPersonaId(1L)).thenReturn(Optional.of(empleado));

        validator.validarFila(fila, periodo);

        assertThat(fila.getEmpleadoId()).isEqualTo(42L);
        assertThat(fila.getEstadoFila()).isEqualTo("OBSERVADA");
    }

    private MarcadorCsvRow filaBase(String dni, LocalDate fecha) {
        MarcadorCsvRow fila = new MarcadorCsvRow();
        fila.setDni(dni);
        fila.setFecha(fecha);
        fila.setNombre("JUAN PEREZ");
        fila.setMarca1("08:00");
        fila.setMarca2("17:00");
        return fila;
    }
}
