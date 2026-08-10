package com.indeci.rrhh.service;

import com.indeci.rrhh.dto.SolicitudCompensacionDetDto;
import com.indeci.rrhh.dto.SolicitudRrhhDto;
import com.indeci.rrhh.entity.JornadaRegimen;
import com.indeci.rrhh.entity.SolicitudCompensacionDet;
import com.indeci.rrhh.repository.SolicitudCompensacionDetRepository;
import com.indeci.rrhh.service.asistencia.EmpleadoJornadaResolver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

/**
 * Integridad de datos (RR.HH. 2026-08-09): el cronograma de compensación NUNCA debe
 * persistir las horas que manda el cliente tal cual — el backend las recalcula siempre
 * con la misma fórmula de intersección de refrigerio que el permiso principal. Antes de
 * este fix, {@code entity.setCantidadHoras(det.getCantidadHoras())} confiaba ciegamente
 * en el DTO del cliente.
 */
@ExtendWith(MockitoExtension.class)
class SolicitudRrhhServiceGuardarDetalleCompensacionTest {

    private static final Long EMPLEADO_ID = 42L;
    private static final Long SOLICITUD_ID = 500L;
    private static final LocalDate FECHA = LocalDate.of(2026, 8, 5);

    @Mock private SolicitudCompensacionDetRepository solicitudCompensacionDetRepository;
    @Mock private EmpleadoJornadaResolver jornadaResolver;

    @InjectMocks private SolicitudRrhhService service;

    @Test
    void ignoraLasHorasDelClienteYPersisteElValorRecalculado() {
        JornadaRegimen jornada = new JornadaRegimen();
        jornada.setRefrigerioInicio("13:00");
        jornada.setRefrigerioFin("14:00");
        lenient().when(jornadaResolver.resolverParaFecha(EMPLEADO_ID, FECHA)).thenReturn(jornada);

        SolicitudCompensacionDetDto det = new SolicitudCompensacionDetDto();
        det.setFechaCompensacion(FECHA);
        det.setHoraInicio("08:00");
        det.setHoraFin("17:00");
        // Valor "malicioso"/erróneo enviado por el cliente — el backend NO debe confiar en él.
        det.setCantidadHoras(999.0);

        SolicitudRrhhDto dto = new SolicitudRrhhDto();
        dto.setDetallesCompensacion(List.of(det));

        service.guardarDetalleCompensacion(SOLICITUD_ID, dto, EMPLEADO_ID);

        ArgumentCaptor<SolicitudCompensacionDet> captor =
                ArgumentCaptor.forClass(SolicitudCompensacionDet.class);
        verify(solicitudCompensacionDetRepository).save(captor.capture());

        SolicitudCompensacionDet guardado = captor.getValue();
        assertThat(guardado.getCantidadHoras()).isEqualTo(8.0);
        assertThat(guardado.getCantidadHoras()).isNotEqualTo(999.0);
    }
}
