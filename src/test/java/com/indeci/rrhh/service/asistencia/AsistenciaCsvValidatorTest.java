package com.indeci.rrhh.service.asistencia;

import com.indeci.rrhh.entity.Empleado;
import com.indeci.rrhh.entity.EmpleadoPlanilla;
import com.indeci.rrhh.entity.PeriodoPlanilla;
import com.indeci.rrhh.entity.Persona;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.EmpleadoRepository;
import com.indeci.rrhh.repository.PersonaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AsistenciaCsvValidatorTest {

    @Mock private PersonaRepository personaRepository;
    @Mock private EmpleadoRepository empleadoRepository;
    @Mock private EmpleadoPlanillaRepository empleadoPlanillaRepository;

    @InjectMocks private AsistenciaCsvValidator validator;

    private AsistenciaCsvParser parser;
    private PeriodoPlanilla periodo;

    @BeforeEach
    void setUp() {
        parser = new AsistenciaCsvParser();
        periodo = new PeriodoPlanilla();
        periodo.setFechaInicio(LocalDate.of(2026, 5, 1));
        periodo.setFechaFin(LocalDate.of(2026, 5, 31));
    }

    @Test
    void validaFila_dniInvalido_marcaErrorSinConsultarPersona() {
        MarcadorCsvRow fila = filaBase("123", LocalDate.of(2026, 5, 10));

        validator.validarFila(fila, periodo);

        assertThat(fila.getEstadoFila()).isEqualTo("ERROR");
        assertThat(fila.getErrores()).contains("DNI inválido — debe tener 8 dígitos.");
        assertThat(fila.getNombreSistema()).isNull();
        verify(personaRepository, never()).findByDniNormalizado(anyString());
    }

    @Test
    void validaFila_fechaNula_marcaError() {
        MarcadorCsvRow fila = filaBase("12345678", null);

        validator.validarFila(fila, periodo);

        assertThat(fila.getEstadoFila()).isEqualTo("ERROR");
        assertThat(fila.getErrores())
                .anyMatch(m -> m.contains("Fecha inválida") && m.contains("dd/MM/yyyy"));
        verify(personaRepository, never()).findByDniNormalizado(anyString());
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
        when(personaRepository.findByDniNormalizado("99999999")).thenReturn(Optional.empty());

        validator.validarFila(fila, periodo);

        assertThat(fila.getEstadoFila()).isEqualTo("ERROR");
        assertThat(fila.getErrores()).contains("DNI no encontrado en INDECI_PERSONA.");
    }

    @Test
    void validaFila_personaSinEmpleado_marcaErrorConNombre() {
        MarcadorCsvRow fila = filaBase("12345678", LocalDate.of(2026, 5, 10));

        Persona persona = new Persona();
        persona.setId(1L);
        persona.setNombreCompleto("JUAN PEREZ");
        when(personaRepository.findByDniNormalizado("12345678")).thenReturn(Optional.of(persona));
        when(empleadoRepository.findAllByPersonaId(1L)).thenReturn(List.of());

        validator.validarFila(fila, periodo);

        assertThat(fila.getEstadoFila()).isEqualTo("ERROR");
        assertThat(fila.getNombreSistema()).isEqualTo("JUAN PEREZ");
        assertThat(fila.getErrores())
                .contains("DNI existe en persona, pero no tiene empleado asociado.");
    }

    @Test
    void validaFila_empleadoInactivo_marcaErrorConNombre() {
        MarcadorCsvRow fila = filaBase("12345678", LocalDate.of(2026, 5, 10));

        Persona persona = new Persona();
        persona.setId(1L);
        persona.setNombreCompleto("JUAN PEREZ");
        when(personaRepository.findByDniNormalizado("12345678")).thenReturn(Optional.of(persona));

        Empleado empleado = new Empleado();
        empleado.setId(42L);
        empleado.setEstado("CESADO");
        when(empleadoRepository.findAllByPersonaId(1L)).thenReturn(List.of(empleado));
        when(empleadoRepository.findAllByPersonaIdAndEstado(1L, "ACTIVO")).thenReturn(List.of());

        validator.validarFila(fila, periodo);

        assertThat(fila.getEstadoFila()).isEqualTo("ERROR");
        assertThat(fila.getNombreSistema()).isEqualTo("JUAN PEREZ");
        assertThat(fila.getErrores()).contains("El empleado asociado no está activo.");
    }

    @Test
    void validaFila_empleadoActivoConMarcas_marcaValida() {
        MarcadorCsvRow fila = filaBase("12345678", LocalDate.of(2026, 5, 10));

        Persona persona = new Persona();
        persona.setId(1L);
        persona.setNombreCompleto("JUAN PEREZ");
        when(personaRepository.findByDniNormalizado("12345678")).thenReturn(Optional.of(persona));

        Empleado empleado = new Empleado();
        empleado.setId(42L);
        empleado.setEstado("ACTIVO");
        when(empleadoRepository.findAllByPersonaId(1L)).thenReturn(List.of(empleado));
        when(empleadoRepository.findAllByPersonaIdAndEstado(1L, "ACTIVO"))
                .thenReturn(List.of(empleado));
        when(empleadoPlanillaRepository.findByEmpleadoIdAndActivo(42L, 1)).thenReturn(List.of());

        validator.validarFila(fila, periodo);

        assertThat(fila.getEmpleadoId()).isEqualTo(42L);
        assertThat(fila.getNombreSistema()).isEqualTo("JUAN PEREZ");
        assertThat(fila.getEstadoFila()).isEqualTo("VALIDA");
        assertThat(fila.getErrores()).isEmpty();
    }

    @Test
    void validaFila_nombreMarcadorDistinto_marcaWarn() {
        MarcadorCsvRow fila = filaBase("12345678", LocalDate.of(2026, 5, 10));
        fila.setNombre("NOMBRE DISTINTO");

        Persona persona = new Persona();
        persona.setId(1L);
        persona.setNombreCompleto("JUAN PEREZ");
        when(personaRepository.findByDniNormalizado("12345678")).thenReturn(Optional.of(persona));

        Empleado empleado = new Empleado();
        empleado.setId(42L);
        empleado.setEstado("ACTIVO");
        when(empleadoRepository.findAllByPersonaId(1L)).thenReturn(List.of(empleado));
        when(empleadoRepository.findAllByPersonaIdAndEstado(1L, "ACTIVO"))
                .thenReturn(List.of(empleado));
        when(empleadoPlanillaRepository.findByEmpleadoIdAndActivo(42L, 1)).thenReturn(List.of());

        validator.validarFila(fila, periodo);

        assertThat(fila.getEstadoFila()).isEqualTo("WARN");
        assertThat(fila.getAdvertencias())
                .anyMatch(m -> m.contains("nombre del marcador difiere del registrado"));
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
        when(personaRepository.findByDniNormalizado("12345678")).thenReturn(Optional.of(persona));

        Empleado empleado = new Empleado();
        empleado.setId(42L);
        empleado.setEstado("ACTIVO");
        when(empleadoRepository.findAllByPersonaId(1L)).thenReturn(List.of(empleado));
        when(empleadoRepository.findAllByPersonaIdAndEstado(1L, "ACTIVO"))
                .thenReturn(List.of(empleado));
        when(empleadoPlanillaRepository.findByEmpleadoIdAndActivo(42L, 1)).thenReturn(List.of());

        validator.validarFila(fila, periodo);

        assertThat(fila.getEmpleadoId()).isEqualTo(42L);
        assertThat(fila.getEstadoFila()).isEqualTo("OBSERVADA");
    }

    @Test
    void validaFila_fechaFueraDelVinculo_marcaError() {
        MarcadorCsvRow fila = filaBase("12345678", LocalDate.of(2026, 5, 10));

        Persona persona = new Persona();
        persona.setId(1L);
        persona.setNombreCompleto("JUAN PEREZ");
        when(personaRepository.findByDniNormalizado("12345678")).thenReturn(Optional.of(persona));

        Empleado empleado = new Empleado();
        empleado.setId(42L);
        empleado.setEstado("ACTIVO");
        when(empleadoRepository.findAllByPersonaId(1L)).thenReturn(List.of(empleado));
        when(empleadoRepository.findAllByPersonaIdAndEstado(1L, "ACTIVO"))
                .thenReturn(List.of(empleado));

        EmpleadoPlanilla vinculo = new EmpleadoPlanilla();
        vinculo.setFechaIngreso(LocalDate.of(2026, 5, 20));
        when(empleadoPlanillaRepository.findByEmpleadoIdAndActivo(42L, 1))
                .thenReturn(List.of(vinculo));

        validator.validarFila(fila, periodo);

        assertThat(fila.getEstadoFila()).isEqualTo("ERROR");
        assertThat(fila.getErrores()).anyMatch(m -> m.contains("vínculo laboral"));
    }

    @Test
    void validaFila_multiplesVinculosVigentes_marcaErrorAmbiguo() {
        MarcadorCsvRow fila = filaBase("12345678", LocalDate.of(2026, 5, 10));

        Persona persona = new Persona();
        persona.setId(1L);
        persona.setNombreCompleto("JUAN PEREZ");
        when(personaRepository.findByDniNormalizado("12345678")).thenReturn(Optional.of(persona));

        Empleado empleado1 = new Empleado();
        empleado1.setId(41L);
        empleado1.setEstado("ACTIVO");
        Empleado empleado2 = new Empleado();
        empleado2.setId(42L);
        empleado2.setEstado("ACTIVO");
        when(empleadoRepository.findAllByPersonaId(1L)).thenReturn(List.of(empleado1, empleado2));
        when(empleadoRepository.findAllByPersonaIdAndEstado(1L, "ACTIVO"))
                .thenReturn(List.of(empleado1, empleado2));

        EmpleadoPlanilla vinculo1 = new EmpleadoPlanilla();
        vinculo1.setFechaIngreso(LocalDate.of(2026, 1, 1));
        EmpleadoPlanilla vinculo2 = new EmpleadoPlanilla();
        vinculo2.setFechaIngreso(LocalDate.of(2026, 1, 1));
        when(empleadoPlanillaRepository.findByEmpleadoIdAndActivo(41L, 1))
                .thenReturn(List.of(vinculo1));
        when(empleadoPlanillaRepository.findByEmpleadoIdAndActivo(42L, 1))
                .thenReturn(List.of(vinculo2));

        validator.validarFila(fila, periodo);

        assertThat(fila.getEstadoFila()).isEqualTo("ERROR");
        assertThat(fila.getErrores())
                .anyMatch(m -> m.contains("múltiples vínculos laborales vigentes"));
    }

    @Test
    void validaFila_multiplesActivos_unSoloVigente_resuelveEmpleadoCorrecto() {
        MarcadorCsvRow fila = filaBase("12345678", LocalDate.of(2026, 5, 10));

        Persona persona = new Persona();
        persona.setId(1L);
        persona.setNombreCompleto("JUAN PEREZ");
        when(personaRepository.findByDniNormalizado("12345678")).thenReturn(Optional.of(persona));

        Empleado empleadoCesado = new Empleado();
        empleadoCesado.setId(40L);
        empleadoCesado.setEstado("ACTIVO");
        Empleado empleadoVigente = new Empleado();
        empleadoVigente.setId(42L);
        empleadoVigente.setEstado("ACTIVO");
        when(empleadoRepository.findAllByPersonaId(1L))
                .thenReturn(List.of(empleadoCesado, empleadoVigente));
        when(empleadoRepository.findAllByPersonaIdAndEstado(1L, "ACTIVO"))
                .thenReturn(List.of(empleadoCesado, empleadoVigente));

        EmpleadoPlanilla vinculoCesado = new EmpleadoPlanilla();
        vinculoCesado.setFechaIngreso(LocalDate.of(2026, 1, 1));
        vinculoCesado.setFechaCese(LocalDate.of(2026, 4, 30));
        when(empleadoPlanillaRepository.findByEmpleadoIdAndActivo(40L, 1))
                .thenReturn(List.of(vinculoCesado));

        EmpleadoPlanilla vinculoVigente = new EmpleadoPlanilla();
        vinculoVigente.setFechaIngreso(LocalDate.of(2026, 5, 1));
        when(empleadoPlanillaRepository.findByEmpleadoIdAndActivo(42L, 1))
                .thenReturn(List.of(vinculoVigente));

        validator.validarFila(fila, periodo);

        assertThat(fila.getEmpleadoId()).isEqualTo(42L);
        assertThat(fila.getEstadoFila()).isEqualTo("VALIDA");
    }

    @Test
    void validarFilas_csvBiometricoReal_personaActiva_resuelveEmpleadoYEstados() throws IOException {
        Path csvPath = Path.of("data", "asistencia", "import", "22_Reporte.csv");
        var parseResult = parser.parse(Files.readAllBytes(csvPath));
        assertThat(parseResult.getFilas()).hasSize(10);

        Persona persona = new Persona();
        persona.setId(100L);
        persona.setNombreCompleto("LOPEZ BENITES ANA MELVA");
        when(personaRepository.findByDniNormalizado("08274536")).thenReturn(Optional.of(persona));

        Empleado empleado = new Empleado();
        empleado.setId(200L);
        empleado.setEstado("ACTIVO");
        when(empleadoRepository.findAllByPersonaId(100L)).thenReturn(List.of(empleado));
        when(empleadoRepository.findAllByPersonaIdAndEstado(100L, "ACTIVO"))
                .thenReturn(List.of(empleado));
        when(empleadoPlanillaRepository.findByEmpleadoIdAndActivo(200L, 1)).thenReturn(List.of());

        PeriodoPlanilla junio2026 = new PeriodoPlanilla();
        junio2026.setFechaInicio(LocalDate.of(2026, 6, 1));
        junio2026.setFechaFin(LocalDate.of(2026, 6, 30));

        validator.validarFilas(parseResult.getFilas(), junio2026);

        assertThat(parseResult.getFilas()).allSatisfy(fila -> {
            assertThat(fila.getNombreSistema()).isEqualTo("LOPEZ BENITES ANA MELVA");
            assertThat(fila.getEmpleadoId()).isEqualTo(200L);
            assertThat(fila.getFecha()).isNotNull();
            assertThat(fila.getEstadoFila()).isIn("VALIDA", "WARN", "OBSERVADA");
        });
        assertThat(parseResult.getFilas().stream().map(MarcadorCsvRow::getEstadoFila).distinct())
                .contains("VALIDA");
        assertThat(parseResult.getFilas().stream().filter(f -> f.getFecha().equals(LocalDate.of(2026, 6, 8))))
                .hasSize(1)
                .allMatch(f -> "VALIDA".equals(f.getEstadoFila()));
    }

    @Test
    void validarFilas_duplicadoEmpleadoFecha_segundaFilaError() {
        Persona persona = new Persona();
        persona.setId(1L);
        persona.setNombreCompleto("JUAN PEREZ");
        when(personaRepository.findByDniNormalizado("12345678")).thenReturn(Optional.of(persona));

        Empleado empleado = new Empleado();
        empleado.setId(42L);
        empleado.setEstado("ACTIVO");
        when(empleadoRepository.findAllByPersonaId(1L)).thenReturn(List.of(empleado));
        when(empleadoRepository.findAllByPersonaIdAndEstado(1L, "ACTIVO"))
                .thenReturn(List.of(empleado));
        when(empleadoPlanillaRepository.findByEmpleadoIdAndActivo(42L, 1)).thenReturn(List.of());

        MarcadorCsvRow fila1 = filaBase("12345678", LocalDate.of(2026, 5, 10));
        MarcadorCsvRow fila2 = filaBase("12345678", LocalDate.of(2026, 5, 10));

        validator.validarFilas(List.of(fila1, fila2), periodo);

        assertThat(fila1.getEstadoFila()).isNotEqualTo("ERROR");
        assertThat(fila2.getEstadoFila()).isEqualTo("ERROR");
        assertThat(fila2.getErrores()).anyMatch(m -> m.contains("duplicada"));
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
