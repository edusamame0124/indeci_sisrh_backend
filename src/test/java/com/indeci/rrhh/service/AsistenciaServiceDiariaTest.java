package com.indeci.rrhh.service;

import com.indeci.audit.context.AuditoriaContext;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.AsistenciaDiariaEditDto;
import com.indeci.rrhh.dto.AsistenciaDiariaRowDto;
import com.indeci.rrhh.entity.AsistenciaCabecera;
import com.indeci.rrhh.entity.AsistenciaDetalle;
import com.indeci.rrhh.entity.PeriodoPlanilla;
import com.indeci.rrhh.entity.SolicitudRrhh;
import com.indeci.rrhh.entity.TipoSolicitudRrhh;
import com.indeci.rrhh.repository.AsistenciaCabeceraRepository;
import com.indeci.rrhh.repository.AsistenciaDetalleRepository;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.EmpleadoRepository;
import com.indeci.rrhh.repository.JornadaRegimenRepository;
import com.indeci.rrhh.repository.PeriodoPlanillaRepository;
import com.indeci.rrhh.repository.SolicitudRrhhRepository;
import com.indeci.rrhh.repository.TipoSolicitudRrhhRepository;
import com.indeci.rrhh.service.asistencia.BaseAsistenciaResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AsistenciaServiceDiariaTest {

    @Mock private AsistenciaCabeceraRepository cabeceraRepository;
    @Mock private AsistenciaDetalleRepository detalleRepository;
    @Mock private EmpleadoRepository empleadoRepository;
    @Mock private PeriodoPlanillaRepository periodoPlanillaRepository;
    @Mock private AuditoriaContext auditoriaContext;
    @Mock private BaseAsistenciaResolver baseResolver;
    @Mock private SolicitudRrhhRepository solicitudRrhhRepository;
    @Mock private TipoSolicitudRrhhRepository tipoSolicitudRrhhRepository;
    @Mock private JornadaRegimenRepository jornadaRegimenRepository;
    @Mock private EmpleadoPlanillaRepository empleadoPlanillaRepository;
    @Mock private com.indeci.rrhh.repository.TeletrabajoReporteDetRepository teletrabajoReporteDetRepository;

    @InjectMocks private AsistenciaService service;

    private static final LocalDate FECHA = LocalDate.of(2026, 6, 18);

    @Test
    void listarDiaria_caso_feliz_mapea_fila() {
        Object[] row = new Object[] {
                10L, 20L, 42L, "43872244", "CHAMORRO GIAMPIETRI GIAN CARLO", FECHA,
                "08:28", "17:22", "TARDANZA",
                480, 0, "2026-06", "IMPORT_MARCADOR",
                15, null, null, null, "08:00",
                0, 0, 0, 0, null, null,
                30L // importacionId (row[24]) — lote origen
        };
        Pageable pageable = PageRequest.of(0, 10);
        List<Object[]> content = Collections.singletonList(row);
        when(detalleRepository.buscarDiariaRango(eq(FECHA), eq(FECHA), eq("43872244"), eq(null), eq(pageable)))
                .thenReturn(new PageImpl<>(content, pageable, 1));
        when(tipoSolicitudRrhhRepository.findAll()).thenReturn(List.of());

        // fechaFin = null → consulta de un solo día (compatibilidad).
        Page<AsistenciaDiariaRowDto> page =
                service.listarDiaria(FECHA, null, "43872244", null, pageable);

        assertThat(page.getTotalElements()).isEqualTo(1);
        AsistenciaDiariaRowDto dto = page.getContent().get(0);
        assertThat(dto.getDetalleId()).isEqualTo(10L);
        assertThat(dto.getDni()).isEqualTo("43872244");
        assertThat(dto.getTipoDia()).isEqualTo("TARDANZA");
        assertThat(dto.getMarcaEntrada()).isEqualTo("08:28");
    }

    @Test
    void listarDiaria_sin_fecha_inicio_lanza_negocio() {
        assertThatThrownBy(() -> service.listarDiaria(null, null, null, null, PageRequest.of(0, 10)))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("fecha");
    }

    @Test
    void editarDia_periodo_cerrado_bloquea() {
        AsistenciaDetalle det = new AsistenciaDetalle();
        det.setId(10L);
        det.setCabeceraId(20L);
        det.setDia(FECHA);
        det.setTipoDia("LABORAL");

        AsistenciaCabecera cab = new AsistenciaCabecera();
        cab.setId(20L);
        cab.setEmpleadoId(42L);
        cab.setPeriodo("2026-06");
        cab.setActivo(1);

        PeriodoPlanilla periodo = new PeriodoPlanilla();
        periodo.setPeriodo("2026-06");
        periodo.setEstado("CERRADO");

        when(detalleRepository.findById(10L)).thenReturn(Optional.of(det));
        when(cabeceraRepository.findById(20L)).thenReturn(Optional.of(cab));
        when(periodoPlanillaRepository.findByPeriodoAndActivo("2026-06", 1))
                .thenReturn(Optional.of(periodo));

        AsistenciaDiariaEditDto edit = new AsistenciaDiariaEditDto();
        edit.setTipoDia("FALTA");

        assertThatThrownBy(() -> service.editarDia(10L, edit))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("cerrado");
    }

    @Test
    void editarDia_caso_feliz_actualiza_y_recalcula() {
        AsistenciaDetalle det = new AsistenciaDetalle();
        det.setId(10L);
        det.setCabeceraId(20L);
        det.setDia(FECHA);
        det.setTipoDia("LABORAL");
        det.setMinutosTardanza(0);

        AsistenciaCabecera cab = new AsistenciaCabecera();
        cab.setId(20L);
        cab.setEmpleadoId(42L);
        cab.setPeriodo("2026-06");
        cab.setActivo(1);
        cab.setRemuneracionBase(3000.0);

        when(detalleRepository.findById(10L)).thenReturn(Optional.of(det));
        when(cabeceraRepository.findById(20L)).thenReturn(Optional.of(cab));
        when(periodoPlanillaRepository.findByPeriodoAndActivo("2026-06", 1))
                .thenReturn(Optional.empty());
        when(tipoSolicitudRrhhRepository.findAll()).thenReturn(List.of());
        when(detalleRepository.findByCabeceraIdOrderByDia(20L)).thenReturn(List.of(det));
        when(empleadoRepository.findPersonaResumenByEmpleadoIds(List.of(42L)))
                .thenReturn(List.<Object[]>of(new Object[] {42L, "NOMBRE TEST", "12345678"}));

        AsistenciaDiariaEditDto edit = new AsistenciaDiariaEditDto();
        edit.setTipoDia("TARDANZA");
        edit.setMinutosTardanza(20);

        AsistenciaDiariaRowDto result = service.editarDia(10L, edit);

        assertThat(result.getTipoDia()).isEqualTo("TARDANZA");
        assertThat(result.getMinutosTardanza()).isEqualTo(20);
        assertThat(result.getDni()).isEqualTo("12345678");
        verify(detalleRepository).save(det);
        verify(cabeceraRepository).save(cab);
    }

    @Test
    void editarDia_con_papeleta_autorizar_pasa_a_presente() {
        AsistenciaDetalle det = detalleBase();
        AsistenciaCabecera cab = cabeceraBase();
        mockEditarContexto(det, cab);

        TipoSolicitudRrhh tipo = tipoPermiso(1L, "003", "Permiso personal");
        SolicitudRrhh solicitud = solicitudAprobada(42L, tipo.getId(), FECHA, FECHA);

        when(tipoSolicitudRrhhRepository.findAll()).thenReturn(List.of(tipo));
        when(solicitudRrhhRepository.findByEmpleadoIdInAndActivo(List.of(42L), 1))
                .thenReturn(List.of(solicitud));

        AsistenciaDiariaEditDto edit = new AsistenciaDiariaEditDto();
        edit.setPapeletaAutorizada(true);

        AsistenciaDiariaRowDto result = service.editarDia(10L, edit);

        assertThat(result.getTipoDia()).isEqualTo("LABORAL");
        assertThat(result.getPapeletaAutorizada()).isEqualTo(1);
        assertThat(result.isTienePapeletaAprobada()).isTrue();
        assertThat(det.getPapeletaMotivoRechazo()).isNull();
    }

    @Test
    void editarDia_con_papeleta_no_autorizar_sin_motivo_falla() {
        AsistenciaDetalle det = detalleBase();
        AsistenciaCabecera cab = cabeceraBase();

        when(detalleRepository.findById(10L)).thenReturn(Optional.of(det));
        when(cabeceraRepository.findById(20L)).thenReturn(Optional.of(cab));
        when(periodoPlanillaRepository.findByPeriodoAndActivo("2026-06", 1))
                .thenReturn(Optional.empty());

        TipoSolicitudRrhh tipo = tipoPermiso(1L, "003", "Permiso personal");
        SolicitudRrhh solicitud = solicitudAprobada(42L, tipo.getId(), FECHA, FECHA);

        when(tipoSolicitudRrhhRepository.findAll()).thenReturn(List.of(tipo));
        when(solicitudRrhhRepository.findByEmpleadoIdInAndActivo(List.of(42L), 1))
                .thenReturn(List.of(solicitud));

        AsistenciaDiariaEditDto edit = new AsistenciaDiariaEditDto();
        edit.setPapeletaAutorizada(false);

        assertThatThrownBy(() -> service.editarDia(10L, edit))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("motivo");
    }

    @Test
    void editarDia_con_papeleta_no_autorizar_con_motivo_pasa_a_observado() {
        AsistenciaDetalle det = detalleBase();
        AsistenciaCabecera cab = cabeceraBase();
        mockEditarContexto(det, cab);

        TipoSolicitudRrhh tipo = tipoPermiso(1L, "003", "Permiso personal");
        SolicitudRrhh solicitud = solicitudAprobada(42L, tipo.getId(), FECHA, FECHA);

        when(tipoSolicitudRrhhRepository.findAll()).thenReturn(List.of(tipo));
        when(solicitudRrhhRepository.findByEmpleadoIdInAndActivo(List.of(42L), 1))
                .thenReturn(List.of(solicitud));

        AsistenciaDiariaEditDto edit = new AsistenciaDiariaEditDto();
        edit.setPapeletaAutorizada(false);
        edit.setPapeletaMotivoRechazo("No cumple sustento");

        AsistenciaDiariaRowDto result = service.editarDia(10L, edit);

        assertThat(result.getTipoDia()).isEqualTo("OBSERVADO");
        assertThat(result.getPapeletaAutorizada()).isZero();
        assertThat(result.getPapeletaMotivoRechazo()).isEqualTo("No cumple sustento");
        assertThat(cab.getDescuentoFalta()).isEqualTo(100.0);
    }

    private AsistenciaDetalle detalleBase() {
        AsistenciaDetalle det = new AsistenciaDetalle();
        det.setId(10L);
        det.setCabeceraId(20L);
        det.setDia(FECHA);
        det.setTipoDia("FALTA");
        det.setMinutosTardanza(0);
        return det;
    }

    private AsistenciaCabecera cabeceraBase() {
        AsistenciaCabecera cab = new AsistenciaCabecera();
        cab.setId(20L);
        cab.setEmpleadoId(42L);
        cab.setPeriodo("2026-06");
        cab.setActivo(1);
        cab.setRemuneracionBase(3000.0);
        return cab;
    }

    private void mockEditarContexto(AsistenciaDetalle det, AsistenciaCabecera cab) {
        when(detalleRepository.findById(10L)).thenReturn(Optional.of(det));
        when(cabeceraRepository.findById(20L)).thenReturn(Optional.of(cab));
        when(periodoPlanillaRepository.findByPeriodoAndActivo("2026-06", 1))
                .thenReturn(Optional.empty());
        when(detalleRepository.findByCabeceraIdOrderByDia(20L)).thenReturn(List.of(det));
        when(empleadoRepository.findPersonaResumenByEmpleadoIds(List.of(42L)))
                .thenReturn(List.<Object[]>of(new Object[] {42L, "NOMBRE TEST", "12345678"}));
    }

    private TipoSolicitudRrhh tipoPermiso(Long id, String codigo, String nombre) {
        TipoSolicitudRrhh tipo = new TipoSolicitudRrhh();
        tipo.setId(id);
        tipo.setCodigo(codigo);
        tipo.setNombre(nombre);
        return tipo;
    }

    private SolicitudRrhh solicitudAprobada(
            Long empleadoId,
            Long tipoId,
            LocalDate inicio,
            LocalDate fin) {
        SolicitudRrhh s = new SolicitudRrhh();
        s.setEmpleadoId(empleadoId);
        s.setTipoSolicitudId(tipoId);
        s.setEstadoSolicitudId(9L);
        s.setFechaInicio(inicio);
        s.setFechaFin(fin);
        s.setMotivo("Motivo permiso");
        s.setHoraInicio("09:00");
        s.setHoraFin("12:00");
        s.setCantidadHoras(3.0);
        s.setActivo(1);
        return s;
    }
}
