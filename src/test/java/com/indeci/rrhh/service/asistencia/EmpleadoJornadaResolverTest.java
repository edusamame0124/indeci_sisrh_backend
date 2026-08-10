package com.indeci.rrhh.service.asistencia;

import com.indeci.rrhh.entity.EmpleadoJornadaExcepcion;
import com.indeci.rrhh.entity.EmpleadoPlanilla;
import com.indeci.rrhh.entity.JornadaRegimen;
import com.indeci.rrhh.repository.EmpleadoJornadaExcepcionRepository;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.JornadaRegimenRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class EmpleadoJornadaResolverTest {

    @Mock private EmpleadoPlanillaRepository empleadoPlanillaRepository;
    @Mock private JornadaRegimenRepository jornadaRegimenRepository;
    @Mock private EmpleadoJornadaExcepcionRepository excepcionRepository;

    @InjectMocks private EmpleadoJornadaResolver resolver;

    private JornadaRegimen regimen728;

    @BeforeEach
    void setUp() {
        regimen728 = new JornadaRegimen();
        regimen728.setRegimenLaboralId(2L);
        regimen728.setHoraIngreso("08:00");
        regimen728.setHoraSalida("17:00");
        regimen728.setToleranciaIngresoMin(5);
        regimen728.setToleranciaAlmuerzoMin(10);
        regimen728.setUmbralTardanzaDiariaMin(10);
        regimen728.setTopeTardanzaMensualMin(60);
        regimen728.setJornadaHoras(BigDecimal.valueOf(8));

        EmpleadoPlanilla vinculo = new EmpleadoPlanilla();
        vinculo.setRegimenLaboralId(2L);
        lenient().when(empleadoPlanillaRepository.findFirstByEmpleadoIdAndActivo(10L, 1))
                .thenReturn(Optional.of(vinculo));
        lenient().when(jornadaRegimenRepository.findByRegimenLaboralId(2L))
                .thenReturn(Optional.of(regimen728));
    }

    @Test
    void resolverParaFecha_conExcepcionVigente_usaHorasDeLaExcepcion() {
        EmpleadoJornadaExcepcion excepcion = excepcion(
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), "07:00", "15:00");

        JornadaRegimen base = resolver.regimenDe(10L);
        JornadaRegimen efectiva = resolver.resolverParaFecha(base, List.of(excepcion), LocalDate.of(2026, 8, 14));

        assertThat(efectiva.getHoraIngreso()).isEqualTo("07:00");
        assertThat(efectiva.getHoraSalida()).isEqualTo("15:00");
        // Heredado del régimen — la excepción no lo redefine.
        assertThat(efectiva.getToleranciaIngresoMin()).isEqualTo(5);
        assertThat(efectiva.getUmbralTardanzaDiariaMin()).isEqualTo(10);
        assertThat(efectiva.getTopeTardanzaMensualMin()).isEqualTo(60);
    }

    @Test
    void resolverParaFecha_sinExcepcion_caeAlRegimen() {
        JornadaRegimen base = resolver.regimenDe(10L);
        JornadaRegimen efectiva = resolver.resolverParaFecha(base, List.of(), LocalDate.of(2026, 8, 14));

        assertThat(efectiva.getHoraIngreso()).isEqualTo("08:00");
        assertThat(efectiva.getHoraSalida()).isEqualTo("17:00");
    }

    @Test
    void resolverParaFecha_fueraDeVigencia_caeAlRegimen() {
        // Excepción vence el 13/08; el 14/08 ya debe caer al régimen (borde de fecha).
        EmpleadoJornadaExcepcion excepcion = excepcion(
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 13), "07:00", "15:00");

        JornadaRegimen base = resolver.regimenDe(10L);
        JornadaRegimen efectiva = resolver.resolverParaFecha(base, List.of(excepcion), LocalDate.of(2026, 8, 14));

        assertThat(efectiva.getHoraIngreso()).isEqualTo("08:00");
    }

    @Test
    void resolverParaFecha_ultimoDiaDeVigencia_todaviaUsaLaExcepcion() {
        EmpleadoJornadaExcepcion excepcion = excepcion(
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 13), "07:00", "15:00");

        JornadaRegimen base = resolver.regimenDe(10L);
        JornadaRegimen efectiva = resolver.resolverParaFecha(base, List.of(excepcion), LocalDate.of(2026, 8, 13));

        assertThat(efectiva.getHoraIngreso()).isEqualTo("07:00");
    }

    private EmpleadoJornadaExcepcion excepcion(
            LocalDate ini, LocalDate fin, String horaIngreso, String horaSalida) {
        EmpleadoJornadaExcepcion e = new EmpleadoJornadaExcepcion();
        e.setEmpleadoId(10L);
        e.setFechaInicio(ini);
        e.setFechaFin(fin);
        e.setHoraIngreso(horaIngreso);
        e.setHoraSalida(horaSalida);
        e.setDocumentoAutorizacion("Resolución Jefatural N° 123-2026");
        e.setActivo(1);
        return e;
    }
}
