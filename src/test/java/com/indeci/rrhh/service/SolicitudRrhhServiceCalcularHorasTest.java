package com.indeci.rrhh.service;

import com.indeci.rrhh.entity.JornadaRegimen;
import com.indeci.rrhh.service.asistencia.EmpleadoJornadaResolver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Horas EFECTIVAS de un permiso por horas (RR.HH. 2026-08-09) — normativa SERVIR: el
 * permiso puede cruzar el refrigerio, pero ese tramo no cuenta como tiempo efectivo. Antes
 * de este fix, {@code calcularHoras} era una simple resta de reloj sin descontar el
 * refrigerio, dando 9h en vez de 8h para una jornada completa 08:00-17:00.
 */
@ExtendWith(MockitoExtension.class)
class SolicitudRrhhServiceCalcularHorasTest {

    private static final Long EMPLEADO_ID = 42L;
    private static final LocalDate FECHA = LocalDate.of(2026, 8, 5);

    @Mock private EmpleadoJornadaResolver jornadaResolver;

    @InjectMocks private SolicitudRrhhService service;

    private JornadaRegimen jornadaConRefrigerio(String refrigerioInicio, String refrigerioFin) {
        JornadaRegimen j = new JornadaRegimen();
        j.setHoraIngreso("08:00");
        j.setHoraSalida("17:00");
        j.setRefrigerioInicio(refrigerioInicio);
        j.setRefrigerioFin(refrigerioFin);
        return j;
    }

    @Test
    void casoFeliz_jornadaCompleta_conRefrigerio_da8HorasEfectivas() {
        when(jornadaResolver.resolverParaFecha(EMPLEADO_ID, FECHA))
                .thenReturn(jornadaConRefrigerio("13:00", "14:00"));

        Double horas = service.calcularHoras(EMPLEADO_ID, FECHA, "08:00", "17:00");

        assertThat(horas).isEqualTo(8.0);
    }

    @Test
    void casoNegocio_permisoCruzaParcialmenteElRefrigerio_soloDescuentaLaInterseccion() {
        // 12:00 a 15:00 (3h de reloj), refrigerio 13:00-14:00 → se solapa 1h → 2h efectivas.
        when(jornadaResolver.resolverParaFecha(EMPLEADO_ID, FECHA))
                .thenReturn(jornadaConRefrigerio("13:00", "14:00"));

        Double horas = service.calcularHoras(EMPLEADO_ID, FECHA, "12:00", "15:00");

        assertThat(horas).isEqualTo(2.0);
    }

    @Test
    void casoBorde_sinJornadaConfigurada_noDescuentaNada() {
        when(jornadaResolver.resolverParaFecha(EMPLEADO_ID, FECHA)).thenReturn(null);

        Double horas = service.calcularHoras(EMPLEADO_ID, FECHA, "08:00", "17:00");

        assertThat(horas).isEqualTo(9.0);
    }

    @Test
    void casoBorde_jornadaSinRefrigerioDefinido_noDescuentaNada() {
        when(jornadaResolver.resolverParaFecha(EMPLEADO_ID, FECHA))
                .thenReturn(jornadaConRefrigerio(null, null));

        Double horas = service.calcularHoras(EMPLEADO_ID, FECHA, "08:00", "17:00");

        assertThat(horas).isEqualTo(9.0);
    }

    @Test
    void casoBorde_permisoQueNoTocaElRefrigerio_noDescuentaNada() {
        when(jornadaResolver.resolverParaFecha(EMPLEADO_ID, FECHA))
                .thenReturn(jornadaConRefrigerio("13:00", "14:00"));

        Double horas = service.calcularHoras(EMPLEADO_ID, FECHA, "08:00", "12:00");

        assertThat(horas).isEqualTo(4.0);
    }
}
