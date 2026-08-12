package com.indeci.rrhh.service;

import com.indeci.audit.context.AuditoriaContext;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.AsistenciaDiaDto;
import com.indeci.rrhh.dto.AsistenciaGuardarDto;
import com.indeci.rrhh.dto.AsistenciaResponseDto;
import com.indeci.rrhh.entity.AsistenciaCabecera;
import com.indeci.rrhh.entity.AsistenciaDetalle;
import com.indeci.rrhh.entity.EmpleadoPlanilla;
import com.indeci.rrhh.entity.Feriado;
import com.indeci.rrhh.entity.SolicitudRrhh;
import com.indeci.rrhh.entity.TipoSolicitudRrhh;
import com.indeci.rrhh.repository.AsistenciaCabeceraRepository;
import com.indeci.rrhh.repository.AsistenciaDetalleRepository;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.FeriadoRepository;
import com.indeci.rrhh.service.asistencia.EmpleadoJornadaResolver;
import com.indeci.rrhh.repository.PeriodoPlanillaRepository;
import com.indeci.rrhh.repository.SolicitudRrhhRepository;
import com.indeci.rrhh.service.asistencia.BaseAsistenciaResolver;
import com.indeci.rrhh.service.asistencia.BaseAsistenciaResult;
import com.indeci.rrhh.service.asistencia.CalendarioLaboralService;
import com.indeci.rrhh.service.asistencia.PapeletaJustificacionResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.InOrder;

/**
 * Spec 010 / M04 — Tests del servicio de asistencia (SPEC §12.2 PANTALLA-02).
 *   - guardar feliz → recalcula agregados + descuento D.Leg. 276 Art. 24
 *   - guardar sin empleado/período → NegocioException (caso error normativo)
 *   - guardar tipo de día inválido → NegocioException (caso borde)
 *   - obtener inexistente → DTO vacío (id null)
 *   - descuento tardanza/falta — fórmula REGLA 276-02 verificada
 */
@ExtendWith(MockitoExtension.class)
class AsistenciaServiceTest {

    @Mock private AsistenciaCabeceraRepository cabeceraRepository;
    @Mock private AsistenciaDetalleRepository detalleRepository;
    @Mock private com.indeci.rrhh.service.asistencia.AsistenciaDetalleJdbcWriter detalleJdbcWriter;
    @Mock private AuditoriaContext auditoriaContext;
    @Mock private BaseAsistenciaResolver baseResolver;
    @Mock private EmpleadoJornadaResolver jornadaResolver;
    @Mock private EmpleadoPlanillaRepository empleadoPlanillaRepository;
    @Mock private PeriodoPlanillaRepository periodoPlanillaRepository;
    @Mock private PapeletaJustificacionResolver papeletaJustificacionResolver;
    @Mock private SolicitudRrhhRepository solicitudRrhhRepository;
    @Mock private FeriadoRepository feriadoRepository;
    @Mock private com.indeci.rrhh.repository.AsistenciaImportacionFilaRepository importacionFilaRepository;
    @Mock private com.indeci.rrhh.service.asistencia.CalendarioLaboralService calendarioLaboralService;
    @Mock private com.indeci.rrhh.service.asistencia.Turno24hReconciliadorService turno24hReconciliador;

    @InjectMocks private AsistenciaService service;

    private static final Long EMPLEADO_ID = 42L;
    private static final String PERIODO = "2026-05";

    private AsistenciaDiaDto dia(String tipo, int minutos, int diaMes) {
        AsistenciaDiaDto d = new AsistenciaDiaDto();
        d.setDia(LocalDate.of(2026, 5, diaMes));
        d.setTipoDia(tipo);
        d.setMinutosTardanza(minutos);
        return d;
    }

    private AsistenciaDetalle detalle(String tipo, int diaMes) {
        AsistenciaDetalle d = new AsistenciaDetalle();
        d.setDia(LocalDate.of(2026, 5, diaMes));
        d.setTipoDia(tipo);
        d.setMinutosTardanza(0);
        return d;
    }

    private AsistenciaDetalle detalleEnFecha(String tipo, LocalDate dia) {
        AsistenciaDetalle d = new AsistenciaDetalle();
        d.setDia(dia);
        d.setTipoDia(tipo);
        d.setMinutosTardanza(0);
        return d;
    }

    private AsistenciaGuardarDto dtoBase() {
        AsistenciaGuardarDto dto = new AsistenciaGuardarDto();
        dto.setEmpleadoId(EMPLEADO_ID);
        dto.setPeriodo(PERIODO);
        dto.setRemuneracionBase(3000.0);
        return dto;
    }

    @Test
    void guardar_caso_feliz_recalcula_agregados_y_descuentos() {
        AsistenciaGuardarDto dto = dtoBase();
        dto.setDias(List.of(
                dia("LABORAL", 0, 4),
                dia("TARDANZA", 45, 5),
                dia("FALTA", 0, 6)));

        when(cabeceraRepository.findByEmpleadoIdAndPeriodoAndActivo(
                EMPLEADO_ID, PERIODO, 1)).thenReturn(Optional.empty());
        when(cabeceraRepository.save(any(AsistenciaCabecera.class)))
                .thenAnswer(inv -> {
                    AsistenciaCabecera c = inv.getArgument(0);
                    c.setId(10L);
                    return c;
                });

        service.guardar(dto);

        ArgumentCaptor<AsistenciaCabecera> capt =
                ArgumentCaptor.forClass(AsistenciaCabecera.class);
        verify(cabeceraRepository).save(capt.capture());
        AsistenciaCabecera cab = capt.getValue();

        assertThat(cab.getDiasLaborados()).isEqualTo(2); // LABORAL + TARDANZA
        assertThat(cab.getDiasFalta()).isEqualTo(1);
        assertThat(cab.getTotalMinTardanza()).isEqualTo(45);
        // (3000/30/8/60) * 45 = 9.375 -> 9.38
        assertThat(cab.getDescuentoTardanza()).isEqualTo(9.38);
        // (3000/30) * 1 = 100.00
        assertThat(cab.getDescuentoFalta()).isEqualTo(100.00);
        assertThat(cab.getEstado()).isEqualTo("BORRADOR");

        verify(detalleRepository).deleteByCabeceraId(10L);
        verify(detalleJdbcWriter).insertarLote(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void guardar_preserva_marcas_y_hace_flush_antes_de_insertar() {
        AsistenciaGuardarDto dto = dtoBase();
        // El front envía el día SIN marcas (el GET no las devuelve).
        dto.setDias(List.of(dia("TARDANZA", 20, 11)));

        AsistenciaCabecera cab = new AsistenciaCabecera();
        cab.setId(10L);
        cab.setEmpleadoId(EMPLEADO_ID);
        cab.setPeriodo(PERIODO);
        when(cabeceraRepository.findByEmpleadoIdAndPeriodoAndActivo(EMPLEADO_ID, PERIODO, 1))
                .thenReturn(Optional.of(cab));
        when(cabeceraRepository.save(any(AsistenciaCabecera.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Detalle previo con las marcas del marcador.
        AsistenciaDetalle prev = new AsistenciaDetalle();
        prev.setDia(LocalDate.of(2026, 5, 11));
        prev.setMarcaEntrada("08:50");
        prev.setMarca3("13:05");
        when(detalleRepository.findByCabeceraIdOrderByDia(10L)).thenReturn(List.of(prev));

        service.guardar(dto);

        // Fix 1 — el DELETE se vacía (flush) ANTES de los INSERT del JdbcWriter (evita ORA-00001).
        // Con JdbcTemplate el flush es aún más crítico: el JDBC crudo no ve el persistence context.
        InOrder orden = inOrder(detalleRepository, detalleJdbcWriter);
        orden.verify(detalleRepository).deleteByCabeceraId(10L);
        orden.verify(detalleRepository).flush();
        ArgumentCaptor<List<AsistenciaDetalle>> capt = ArgumentCaptor.forClass(List.class);
        orden.verify(detalleJdbcWriter).insertarLote(capt.capture());

        // Fix 2 — las marcas se conservan del detalle previo.
        AsistenciaDetalle guardado = capt.getValue().get(0);
        assertThat(guardado.getMarcaEntrada()).isEqualTo("08:50");
        assertThat(guardado.getMarca3()).isEqualTo("13:05");
    }

    @Test
    void guardarImportacion_primeraVez_creaVersion1_sinBorrarHistorico() {
        when(cabeceraRepository.findByEmpleadoIdAndPeriodoAndActivo(EMPLEADO_ID, PERIODO, 1))
                .thenReturn(Optional.empty());
        when(cabeceraRepository.maxVersion(EMPLEADO_ID, PERIODO)).thenReturn(null);
        when(cabeceraRepository.save(any(AsistenciaCabecera.class))).thenAnswer(inv -> {
            AsistenciaCabecera c = inv.getArgument(0);
            c.setId(11L);
            return c;
        });

        service.guardarImportacion(EMPLEADO_ID, PERIODO, 3000.0, "FALLBACK", "PREVALIDADA", 5L,
                List.of(dia("LABORAL", 0, 4)), null, null, null);

        ArgumentCaptor<AsistenciaCabecera> capt = ArgumentCaptor.forClass(AsistenciaCabecera.class);
        verify(cabeceraRepository).save(capt.capture());
        AsistenciaCabecera nueva = capt.getValue();
        assertThat(nueva.getVersion()).isEqualTo(1);
        assertThat(nueva.getActivo()).isEqualTo(1);
        assertThat(nueva.getMotivoRectificacion()).isNull();
        verify(detalleJdbcWriter).insertarLote(any());
        // NO se borra detalle histórico (req 5)
        verify(detalleRepository, never()).deleteByCabeceraId(anyLong());
        // Fix integridad — sin versión anterior no hay nada que fusionar: no se consulta detalle previo.
        verify(detalleRepository, never()).findByCabeceraIdOrderByDia(anyLong());
    }

    @Test
    void guardarImportacion_rectificacion_versiona_y_conserva_anterior() {
        AsistenciaCabecera anterior = new AsistenciaCabecera();
        anterior.setId(10L);
        anterior.setActivo(1);
        anterior.setEstado("VALIDADA");
        anterior.setVersion(1);
        when(cabeceraRepository.findByEmpleadoIdAndPeriodoAndActivo(EMPLEADO_ID, PERIODO, 1))
                .thenReturn(Optional.of(anterior));
        when(cabeceraRepository.maxVersion(EMPLEADO_ID, PERIODO)).thenReturn(1);
        when(cabeceraRepository.save(any(AsistenciaCabecera.class))).thenAnswer(inv -> {
            AsistenciaCabecera c = inv.getArgument(0);
            c.setId(11L);
            return c;
        });

        service.guardarImportacion(EMPLEADO_ID, PERIODO, 3000.0, "FALLBACK", "PREVALIDADA", 5L,
                List.of(dia("LABORAL", 0, 4)), "Marca manual validada", "rrhh", "jefe");

        // La anterior se conserva como ACTIVO=0 (req 4) y se desactiva ANTES (saveAndFlush).
        verify(cabeceraRepository).saveAndFlush(argThat(c ->
                c.getId().equals(10L) && c.getActivo() == 0));

        ArgumentCaptor<AsistenciaCabecera> capt = ArgumentCaptor.forClass(AsistenciaCabecera.class);
        verify(cabeceraRepository).save(capt.capture());
        AsistenciaCabecera nueva = capt.getValue();
        assertThat(nueva.getVersion()).isEqualTo(2);
        assertThat(nueva.getActivo()).isEqualTo(1);
        assertThat(nueva.getMotivoRectificacion()).isEqualTo("Marca manual validada");
        assertThat(nueva.getAutorizadoPor()).isEqualTo("jefe");
        // NO se borra detalle de versiones previas (req 5)
        verify(detalleRepository, never()).deleteByCabeceraId(anyLong());
    }

    @Test
    @SuppressWarnings("unchecked")
    void guardarImportacion_rectificacion_fusiona_dias_fuera_de_rango_de_version_anterior() {
        // Simula el bug real: quincena 1 (días 1-15, con una FALTA el día 5) ya está activa
        // cuando llega la carga de la quincena 2 (días 16-31, con otra FALTA el día 16).
        AsistenciaCabecera anterior = new AsistenciaCabecera();
        anterior.setId(10L);
        anterior.setActivo(1);

        List<AsistenciaDetalle> detallePrevio = new java.util.ArrayList<>();
        for (int d = 1; d <= 15; d++) {
            detallePrevio.add(detalle(d == 5 ? "FALTA" : "LABORAL", d));
        }

        when(cabeceraRepository.findByEmpleadoIdAndPeriodoAndActivo(EMPLEADO_ID, PERIODO, 1))
                .thenReturn(Optional.of(anterior));
        when(detalleRepository.findByCabeceraIdOrderByDia(10L)).thenReturn(detallePrevio);
        when(cabeceraRepository.maxVersion(EMPLEADO_ID, PERIODO)).thenReturn(1);
        when(cabeceraRepository.save(any(AsistenciaCabecera.class))).thenAnswer(inv -> {
            AsistenciaCabecera c = inv.getArgument(0);
            c.setId(11L);
            return c;
        });

        List<AsistenciaDiaDto> diasNuevoArchivo = new java.util.ArrayList<>();
        for (int d = 16; d <= 31; d++) {
            diasNuevoArchivo.add(dia(d == 16 ? "FALTA" : "LABORAL", 0, d));
        }

        service.guardarImportacion(EMPLEADO_ID, PERIODO, 3000.0, "FALLBACK", "PREVALIDADA", 5L,
                diasNuevoArchivo, "Carga segunda quincena", "rrhh", "jefe");

        // El detalle insertado cubre TODO el mes (31 días), sin duplicar fechas.
        ArgumentCaptor<List<AsistenciaDetalle>> detalleCapt = ArgumentCaptor.forClass(List.class);
        verify(detalleJdbcWriter).insertarLote(detalleCapt.capture());
        List<AsistenciaDetalle> insertado = detalleCapt.getValue();
        assertThat(insertado).hasSize(31);
        assertThat(insertado.stream().map(AsistenciaDetalle::getDia).distinct().count()).isEqualTo(31L);

        // Los agregados de la cabecera activa nueva reflejan el TOTAL fusionado (2 faltas, no 1) —
        // equivalente a haber subido el mes completo de una sola vez.
        ArgumentCaptor<AsistenciaCabecera> cabCapt = ArgumentCaptor.forClass(AsistenciaCabecera.class);
        verify(cabeceraRepository).save(cabCapt.capture());
        assertThat(cabCapt.getValue().getDiasFalta()).isEqualTo(2);
        assertThat(cabCapt.getValue().getDiasLaborados()).isEqualTo(29);
    }

    @Test
    @SuppressWarnings("unchecked")
    void guardarImportacion_rectificacion_dia_nuevo_pisa_dia_previo_del_mismo_dia() {
        AsistenciaCabecera anterior = new AsistenciaCabecera();
        anterior.setId(10L);
        anterior.setActivo(1);

        when(cabeceraRepository.findByEmpleadoIdAndPeriodoAndActivo(EMPLEADO_ID, PERIODO, 1))
                .thenReturn(Optional.of(anterior));
        when(detalleRepository.findByCabeceraIdOrderByDia(10L)).thenReturn(List.of(detalle("FALTA", 10)));
        when(cabeceraRepository.maxVersion(EMPLEADO_ID, PERIODO)).thenReturn(1);
        when(cabeceraRepository.save(any(AsistenciaCabecera.class))).thenAnswer(inv -> {
            AsistenciaCabecera c = inv.getArgument(0);
            c.setId(11L);
            return c;
        });

        // El archivo nuevo trae el MISMO día (10) con otro tipo — la rectificación debe ganar.
        service.guardarImportacion(EMPLEADO_ID, PERIODO, 3000.0, "FALLBACK", "PREVALIDADA", 5L,
                List.of(dia("TARDANZA", 20, 10)), "Corrección de marcación", "rrhh", "jefe");

        ArgumentCaptor<List<AsistenciaDetalle>> capt = ArgumentCaptor.forClass(List.class);
        verify(detalleJdbcWriter).insertarLote(capt.capture());
        List<AsistenciaDetalle> insertado = capt.getValue();
        assertThat(insertado).hasSize(1);
        assertThat(insertado.get(0).getTipoDia()).isEqualTo("TARDANZA");
        assertThat(insertado.get(0).getMinutosTardanza()).isEqualTo(20);
    }

    @Test
    @SuppressWarnings("unchecked")
    void guardarImportacion_rectificacion_version_anterior_con_detalle_vacio_no_agrega_dias() {
        AsistenciaCabecera anterior = new AsistenciaCabecera();
        anterior.setId(10L);
        anterior.setActivo(1);

        when(cabeceraRepository.findByEmpleadoIdAndPeriodoAndActivo(EMPLEADO_ID, PERIODO, 1))
                .thenReturn(Optional.of(anterior));
        when(detalleRepository.findByCabeceraIdOrderByDia(10L)).thenReturn(List.of());
        when(cabeceraRepository.maxVersion(EMPLEADO_ID, PERIODO)).thenReturn(1);
        when(cabeceraRepository.save(any(AsistenciaCabecera.class))).thenAnswer(inv -> {
            AsistenciaCabecera c = inv.getArgument(0);
            c.setId(11L);
            return c;
        });

        service.guardarImportacion(EMPLEADO_ID, PERIODO, 3000.0, "FALLBACK", "PREVALIDADA", 5L,
                List.of(dia("LABORAL", 0, 4), dia("FALTA", 0, 5)), "Rectificación", "rrhh", "jefe");

        ArgumentCaptor<List<AsistenciaDetalle>> capt = ArgumentCaptor.forClass(List.class);
        verify(detalleJdbcWriter).insertarLote(capt.capture());
        assertThat(capt.getValue()).hasSize(2);
    }

    @Test
    void guardar_sin_empleado_o_periodo_lanza_excepcion() {
        AsistenciaGuardarDto dto = new AsistenciaGuardarDto();
        dto.setPeriodo(PERIODO);

        assertThatThrownBy(() -> service.guardar(dto))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("obligatorios");
    }

    @Test
    void guardar_tipo_dia_invalido_lanza_excepcion() {
        AsistenciaGuardarDto dto = dtoBase();
        dto.setDias(List.of(dia("INVALIDO", 0, 1)));

        assertThatThrownBy(() -> service.guardar(dto))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("Tipo de día inválido");
    }

    @Test
    void guardar_sancion_pad_sin_observacion_lanza_excepcion() {
        AsistenciaGuardarDto dto = dtoBase();
        dto.setDias(List.of(dia("SANCION_PAD", 0, 8)));

        assertThatThrownBy(() -> service.guardar(dto))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("motivo/expediente PAD");
    }

    @Test
    void guardar_sancion_pad_con_observacion_descuenta_como_falta() {
        AsistenciaDiaDto sancionPad = dia("SANCION_PAD", 0, 8);
        sancionPad.setObservacion("Expediente PAD N° 045-2026 — Res. de sanción");

        AsistenciaGuardarDto dto = dtoBase();
        dto.setDias(List.of(dia("LABORAL", 0, 4), sancionPad));

        when(cabeceraRepository.findByEmpleadoIdAndPeriodoAndActivo(
                EMPLEADO_ID, PERIODO, 1)).thenReturn(Optional.empty());
        when(cabeceraRepository.save(any(AsistenciaCabecera.class)))
                .thenAnswer(inv -> {
                    AsistenciaCabecera c = inv.getArgument(0);
                    c.setId(11L);
                    return c;
                });

        service.guardar(dto);

        ArgumentCaptor<AsistenciaCabecera> capt =
                ArgumentCaptor.forClass(AsistenciaCabecera.class);
        verify(cabeceraRepository).save(capt.capture());
        AsistenciaCabecera cab = capt.getValue();

        assertThat(cab.getDiasFalta()).isEqualTo(1);
        // (3000/30) * 1 = 100.00
        assertThat(cab.getDescuentoFalta()).isEqualTo(100.00);
    }

    @Test
    void obtener_inexistente_devuelve_dto_vacio() {
        when(cabeceraRepository.findByEmpleadoIdAndPeriodoAndActivo(
                EMPLEADO_ID, PERIODO, 1)).thenReturn(Optional.empty());
        BaseAsistenciaResult base = new BaseAsistenciaResult();
        base.setRemuneracionBase(0.0);
        when(baseResolver.resolver(EMPLEADO_ID)).thenReturn(base);

        AsistenciaResponseDto dto = service.obtener(EMPLEADO_ID, PERIODO);

        assertThat(dto.getId()).isNull();
        assertThat(dto.getEmpleadoId()).isEqualTo(EMPLEADO_ID);
        assertThat(dto.getPeriodo()).isEqualTo(PERIODO);
        assertThat(dto.getDias()).isEmpty();
        assertThat(dto.getDescuentoTardanza()).isZero();
    }

    @Test
    void descuento_aplica_formula_276_art24() {
        // ROUND((3000/30/8/60) * 120, 2) = 25.00
        assertThat(service.calcularDescuentoTardanza(3000.0, 120))
                .isEqualTo(25.00);
        // ROUND((3000/30) * 2, 2) = 200.00
        assertThat(service.calcularDescuentoFalta(3000.0, 2))
                .isEqualTo(200.00);
        // Bordes: sin minutos/faltas o sin remuneración => 0
        assertThat(service.calcularDescuentoTardanza(3000.0, 0)).isZero();
        assertThat(service.calcularDescuentoFalta(0.0, 5)).isZero();
    }

    /**
     * Decisión RR.HH.: una papeleta de Vacaciones aprobada manda sobre una marcación física
     * real (LABORAL) — caso CASTILLO (papeleta N°270, 13-16/07/2026 aprobada, día 14 marcado
     * LABORAL por el marcador). reconciliarPorPapeletaAprobada debe sobrescribirlo a VACACIONES.
     */
    @Test
    void reconciliarPorPapeletaAprobada_vacaciones_sobrescribe_dia_laboral_a_vacaciones() {
        SolicitudRrhh solicitud = new SolicitudRrhh();
        solicitud.setId(270L);
        solicitud.setEmpleadoId(EMPLEADO_ID);
        solicitud.setFechaInicio(LocalDate.of(2026, 7, 13));
        solicitud.setFechaFin(LocalDate.of(2026, 7, 16));

        TipoSolicitudRrhh tipo = new TipoSolicitudRrhh();
        tipo.setCodigo("012");
        tipo.setJustificaAsistencia(1);

        AsistenciaCabecera cab = new AsistenciaCabecera();
        cab.setId(999L);
        cab.setEmpleadoId(EMPLEADO_ID);
        cab.setPeriodo("2026-07");

        AsistenciaDetalle det = new AsistenciaDetalle();
        det.setDia(LocalDate.of(2026, 7, 14));
        det.setTipoDia("LABORAL");

        when(periodoPlanillaRepository.findByPeriodoAndActivo("2026-07", 1))
                .thenReturn(Optional.empty());
        when(cabeceraRepository.findByEmpleadoIdAndPeriodoAndActivo(EMPLEADO_ID, "2026-07", 1))
                .thenReturn(Optional.of(cab));
        when(detalleRepository.findByCabeceraIdOrderByDia(999L))
                .thenReturn(List.of(det));

        List<SolicitudRrhh> justificantes = List.of(solicitud);
        when(papeletaJustificacionResolver.cargarJustificantes(EMPLEADO_ID, LocalDate.of(2026, 7, 16)))
                .thenReturn(justificantes);

        AsistenciaDiaDto reconciliado = new AsistenciaDiaDto();
        reconciliado.setDia(LocalDate.of(2026, 7, 14));
        reconciliado.setTipoDia("VACACIONES");
        reconciliado.setMinutosTardanza(0);
        reconciliado.setObservacion(
                "Se ignoró marcación física por papeleta de vacaciones aprobada N°270.");
        reconciliado.setOrigen("PAPELETA");
        when(papeletaJustificacionResolver.justificarVacacionSobreMarcacion(
                LocalDate.of(2026, 7, 14), justificantes))
                .thenReturn(Optional.of(reconciliado));

        service.reconciliarPorPapeletaAprobada(solicitud, tipo);

        assertThat(det.getTipoDia()).isEqualTo("VACACIONES");
        assertThat(det.getObservacion()).contains("Se ignoró marcación física");
        verify(detalleRepository).save(det);
    }

    /**
     * RIS INDECI Art. 25.5 (2026-08-07): una Omisión de marca sin papeleta 004 debe poder
     * reconciliarse igual por el TIPO de papeleta que efectivamente cubre el día — aquí un
     * Teletrabajo aprobado, sin ninguna papeleta 004. Antes solo se probaba la 004 y el día se
     * quedaba atascado en Omisión aunque hubiera una papeleta válida cubriéndolo.
     */
    @Test
    void reconciliarPorPapeletaAprobada_omision_sin_004_se_reconcilia_por_tipo_de_papeleta() {
        SolicitudRrhh solicitud = new SolicitudRrhh();
        solicitud.setId(400L);
        solicitud.setEmpleadoId(EMPLEADO_ID);
        solicitud.setFechaInicio(LocalDate.of(2026, 7, 13));
        solicitud.setFechaFin(LocalDate.of(2026, 7, 16));

        TipoSolicitudRrhh tipo = new TipoSolicitudRrhh();
        tipo.setCodigo("TELETRABAJO");
        tipo.setNombre("Reporte de Teletrabajo");
        tipo.setJustificaAsistencia(1);
        solicitud.setTipoSolicitud(tipo);

        AsistenciaCabecera cab = new AsistenciaCabecera();
        cab.setId(999L);
        cab.setEmpleadoId(EMPLEADO_ID);
        cab.setPeriodo("2026-07");

        AsistenciaDetalle det = new AsistenciaDetalle();
        det.setDia(LocalDate.of(2026, 7, 14));
        det.setTipoDia("OMISION_MARCACION");

        when(periodoPlanillaRepository.findByPeriodoAndActivo("2026-07", 1))
                .thenReturn(Optional.empty());
        when(cabeceraRepository.findByEmpleadoIdAndPeriodoAndActivo(EMPLEADO_ID, "2026-07", 1))
                .thenReturn(Optional.of(cab));
        when(detalleRepository.findByCabeceraIdOrderByDia(999L))
                .thenReturn(List.of(det));

        List<SolicitudRrhh> justificantes = List.of(solicitud);
        when(papeletaJustificacionResolver.cargarJustificantes(EMPLEADO_ID, LocalDate.of(2026, 7, 16)))
                .thenReturn(justificantes);
        // Sin papeleta 004 cubriendo la fecha.
        when(papeletaJustificacionResolver.justificarOmision(LocalDate.of(2026, 7, 14), justificantes))
                .thenReturn(Optional.empty());

        AsistenciaDiaDto reconciliado = new AsistenciaDiaDto();
        reconciliado.setDia(LocalDate.of(2026, 7, 14));
        reconciliado.setTipoDia("TELETRABAJO");
        reconciliado.setMinutosTardanza(0);
        reconciliado.setOrigen("PAPELETA");
        when(papeletaJustificacionResolver.justificar(LocalDate.of(2026, 7, 14), justificantes))
                .thenReturn(Optional.of(reconciliado));

        service.reconciliarPorPapeletaAprobada(solicitud, tipo);

        assertThat(det.getTipoDia()).isEqualTo("TELETRABAJO");
        verify(detalleRepository).save(det);
    }

    /**
     * P3 (2026-08-07): si la cabecera existe pero su planilla ya está CERRADA/APROBADA
     * (LEY-05, inmutable), reconciliarDetalleCabecera no toca nada — y ahora
     * reconciliarPorPapeletaAprobada debe devolver el período como advertencia, para que
     * SolicitudRrhhService.aprobarRrhh se lo pueda avisar a RR.HH. en vez de aprobar en
     * silencio sin efecto real.
     */
    @Test
    void reconciliarPorPapeletaAprobada_periodo_cerrado_no_reconcilia_y_devuelve_advertencia() {
        SolicitudRrhh solicitud = new SolicitudRrhh();
        solicitud.setId(270L);
        solicitud.setEmpleadoId(EMPLEADO_ID);
        solicitud.setFechaInicio(LocalDate.of(2026, 7, 13));
        solicitud.setFechaFin(LocalDate.of(2026, 7, 16));

        TipoSolicitudRrhh tipo = new TipoSolicitudRrhh();
        tipo.setCodigo("012");
        tipo.setJustificaAsistencia(1);

        AsistenciaCabecera cab = new AsistenciaCabecera();
        cab.setId(999L);
        cab.setEmpleadoId(EMPLEADO_ID);
        cab.setPeriodo("2026-07");

        com.indeci.rrhh.entity.PeriodoPlanilla periodoCerrado = new com.indeci.rrhh.entity.PeriodoPlanilla();
        periodoCerrado.setEstado("CERRADO");
        when(periodoPlanillaRepository.findByPeriodoAndActivo("2026-07", 1))
                .thenReturn(Optional.of(periodoCerrado));
        when(cabeceraRepository.findByEmpleadoIdAndPeriodoAndActivo(EMPLEADO_ID, "2026-07", 1))
                .thenReturn(Optional.of(cab));
        when(papeletaJustificacionResolver.cargarJustificantes(EMPLEADO_ID, LocalDate.of(2026, 7, 16)))
                .thenReturn(List.of(solicitud));

        List<String> periodosBloqueados = service.reconciliarPorPapeletaAprobada(solicitud, tipo);

        assertThat(periodosBloqueados).containsExactly("2026-07");
        verify(detalleRepository, never()).findByCabeceraIdOrderByDia(any());
        verify(detalleRepository, never()).save(any());
    }

    /**
     * Fase A (decisión RR.HH.): si nunca se importó un marcador para el período, la vacación
     * no puede depender de una marcación física para "existir" — se crea la cabecera desde
     * cero con los días de la papeleta ya en VACACIONES (caso feliz).
     */
    @Test
    void reconciliarPorPapeletaAprobada_vacaciones_sin_cabecera_crea_cabecera_con_detalle() {
        SolicitudRrhh solicitud = new SolicitudRrhh();
        solicitud.setId(500L);
        solicitud.setEmpleadoId(EMPLEADO_ID);
        solicitud.setFechaInicio(LocalDate.of(2026, 7, 6)); // lunes
        solicitud.setFechaFin(LocalDate.of(2026, 7, 10)); // viernes

        TipoSolicitudRrhh tipo = new TipoSolicitudRrhh();
        tipo.setCodigo("012");
        tipo.setNombre("Solicitud de Vacaciones");
        tipo.setJustificaAsistencia(0);
        solicitud.setTipoSolicitud(tipo);

        when(cabeceraRepository.findByEmpleadoIdAndPeriodoAndActivo(EMPLEADO_ID, "2026-07", 1))
                .thenReturn(Optional.empty());
        when(periodoPlanillaRepository.findByPeriodoAndActivo("2026-07", 1))
                .thenReturn(Optional.empty());
        when(papeletaJustificacionResolver.cargarJustificantes(EMPLEADO_ID, LocalDate.of(2026, 7, 10)))
                .thenReturn(List.of(solicitud));
        when(empleadoPlanillaRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.empty());
        when(calendarioLaboralService.paraPeriodo(any(), any()))
                .thenReturn(new CalendarioLaboralService.Calendario(Set.of(), Set.of(6, 7), Map.of()));
        BaseAsistenciaResult base = new BaseAsistenciaResult();
        base.setRemuneracionBase(3000.0);
        when(baseResolver.resolver(EMPLEADO_ID)).thenReturn(base);
        when(cabeceraRepository.save(any(AsistenciaCabecera.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.reconciliarPorPapeletaAprobada(solicitud, tipo);

        ArgumentCaptor<AsistenciaCabecera> cabCaptor = ArgumentCaptor.forClass(AsistenciaCabecera.class);
        verify(cabeceraRepository).save(cabCaptor.capture());
        AsistenciaCabecera guardada = cabCaptor.getValue();
        assertThat(guardada.getEmpleadoId()).isEqualTo(EMPLEADO_ID);
        assertThat(guardada.getPeriodo()).isEqualTo("2026-07");
        assertThat(guardada.getActivo()).isEqualTo(1);
        assertThat(guardada.getEstado()).isEqualTo("BORRADOR");
        assertThat(guardada.getRemuneracionBase()).isEqualTo(3000.0);

        ArgumentCaptor<List<AsistenciaDetalle>> detCaptor = ArgumentCaptor.forClass(List.class);
        verify(detalleJdbcWriter).insertarLote(detCaptor.capture());
        List<AsistenciaDetalle> detalles = detCaptor.getValue();
        assertThat(detalles).hasSize(5); // lunes a viernes, sin fin de semana en el rango
        assertThat(detalles).extracting(AsistenciaDetalle::getTipoDia).containsOnly("VACACIONES");
        assertThat(detalles.get(0).getObservacion()).contains("Vacaciones registradas por papeleta aprobada N°500");
    }

    /** Caso borde: el rango de la papeleta cruza el fin de semana → sábado/domingo quedan DESCANSO. */
    @Test
    void reconciliarPorPapeletaAprobada_vacaciones_sin_cabecera_marca_fin_de_semana_como_descanso() {
        SolicitudRrhh solicitud = new SolicitudRrhh();
        solicitud.setId(501L);
        solicitud.setEmpleadoId(EMPLEADO_ID);
        solicitud.setFechaInicio(LocalDate.of(2026, 7, 10)); // viernes
        solicitud.setFechaFin(LocalDate.of(2026, 7, 13)); // lunes

        TipoSolicitudRrhh tipo = new TipoSolicitudRrhh();
        tipo.setCodigo("012");
        tipo.setNombre("Solicitud de Vacaciones");
        tipo.setJustificaAsistencia(0);
        solicitud.setTipoSolicitud(tipo);

        when(cabeceraRepository.findByEmpleadoIdAndPeriodoAndActivo(EMPLEADO_ID, "2026-07", 1))
                .thenReturn(Optional.empty());
        when(periodoPlanillaRepository.findByPeriodoAndActivo("2026-07", 1))
                .thenReturn(Optional.empty());
        when(papeletaJustificacionResolver.cargarJustificantes(EMPLEADO_ID, LocalDate.of(2026, 7, 13)))
                .thenReturn(List.of(solicitud));
        when(empleadoPlanillaRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.empty());
        when(calendarioLaboralService.paraPeriodo(any(), any()))
                .thenReturn(new CalendarioLaboralService.Calendario(Set.of(), Set.of(6, 7), Map.of()));
        when(baseResolver.resolver(EMPLEADO_ID)).thenReturn(new BaseAsistenciaResult());
        when(cabeceraRepository.save(any(AsistenciaCabecera.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.reconciliarPorPapeletaAprobada(solicitud, tipo);

        ArgumentCaptor<List<AsistenciaDetalle>> detCaptor = ArgumentCaptor.forClass(List.class);
        verify(detalleJdbcWriter).insertarLote(detCaptor.capture());
        java.util.Map<LocalDate, String> porDia = detCaptor.getValue().stream()
                .collect(java.util.stream.Collectors.toMap(AsistenciaDetalle::getDia, AsistenciaDetalle::getTipoDia));
        assertThat(porDia.get(LocalDate.of(2026, 7, 10))).isEqualTo("VACACIONES"); // viernes
        assertThat(porDia.get(LocalDate.of(2026, 7, 11))).isEqualTo("DESCANSO"); // sábado
        assertThat(porDia.get(LocalDate.of(2026, 7, 12))).isEqualTo("DESCANSO"); // domingo
        assertThat(porDia.get(LocalDate.of(2026, 7, 13))).isEqualTo("VACACIONES"); // lunes
    }

    /** Caso borde: período CERRADO/APROBADO (LEY-05, inmutable) → no crea nada. */
    @Test
    void reconciliarPorPapeletaAprobada_vacaciones_sin_cabecera_periodo_cerrado_no_crea_nada() {
        SolicitudRrhh solicitud = new SolicitudRrhh();
        solicitud.setId(502L);
        solicitud.setEmpleadoId(EMPLEADO_ID);
        solicitud.setFechaInicio(LocalDate.of(2026, 7, 6));
        solicitud.setFechaFin(LocalDate.of(2026, 7, 10));

        TipoSolicitudRrhh tipo = new TipoSolicitudRrhh();
        tipo.setCodigo("012");
        tipo.setJustificaAsistencia(0);
        solicitud.setTipoSolicitud(tipo);

        com.indeci.rrhh.entity.PeriodoPlanilla periodoCerrado = new com.indeci.rrhh.entity.PeriodoPlanilla();
        periodoCerrado.setPeriodo("2026-07");
        periodoCerrado.setEstado("CERRADO");

        when(cabeceraRepository.findByEmpleadoIdAndPeriodoAndActivo(EMPLEADO_ID, "2026-07", 1))
                .thenReturn(Optional.empty());
        when(periodoPlanillaRepository.findByPeriodoAndActivo("2026-07", 1))
                .thenReturn(Optional.of(periodoCerrado));
        when(papeletaJustificacionResolver.cargarJustificantes(EMPLEADO_ID, LocalDate.of(2026, 7, 10)))
                .thenReturn(List.of(solicitud));

        service.reconciliarPorPapeletaAprobada(solicitud, tipo);

        verify(cabeceraRepository, never()).save(any());
        verify(detalleJdbcWriter, never()).insertarLote(any());
    }

    @Test
    void reconciliarPorPapeletaAprobada_tipo_sin_justificaAsistencia_ni_permiso_no_hace_nada() {
        SolicitudRrhh solicitud = new SolicitudRrhh();
        solicitud.setId(1L);
        solicitud.setEmpleadoId(EMPLEADO_ID);
        solicitud.setFechaInicio(LocalDate.of(2026, 7, 13));

        TipoSolicitudRrhh tipo = new TipoSolicitudRrhh();
        // "099" no está en CODIGOS_PERMISO_ASISTENCIA (001-006,008-012) ni tiene el flag —
        // no debe disparar ni la reconciliación de FALTA ni la autorización de TARDANZA.
        tipo.setCodigo("099");
        tipo.setJustificaAsistencia(0);

        service.reconciliarPorPapeletaAprobada(solicitud, tipo);

        org.mockito.Mockito.verifyNoInteractions(cabeceraRepository);
    }

    /**
     * Punto 2 (decisión RR.HH.): una papeleta cuyo tipo está en CODIGOS_PERMISO_ASISTENCIA
     * (p.ej. "006" Comisión de Servicio) pero SIN el flag JUSTIFICA_ASISTENCIA autoriza
     * automáticamente la TARDANZA que cubre — no exige repetir la decisión en Consulta diaria.
     */
    @Test
    void reconciliarPorPapeletaAprobada_tipo_permiso_sin_flag_autoriza_tardanza() {
        SolicitudRrhh solicitud = new SolicitudRrhh();
        solicitud.setId(348L);
        solicitud.setEmpleadoId(EMPLEADO_ID);
        solicitud.setFechaInicio(LocalDate.of(2026, 7, 17));
        solicitud.setFechaFin(LocalDate.of(2026, 7, 17));

        TipoSolicitudRrhh tipo = new TipoSolicitudRrhh();
        tipo.setCodigo("006");
        tipo.setNombre("Comisión de Servicio");
        tipo.setJustificaAsistencia(0);
        solicitud.setTipoSolicitud(tipo);

        AsistenciaCabecera cab = new AsistenciaCabecera();
        cab.setId(9001L);
        cab.setEmpleadoId(EMPLEADO_ID);
        cab.setPeriodo("2026-07");
        cab.setActivo(1);

        AsistenciaDetalle det = new AsistenciaDetalle();
        det.setId(555L);
        det.setCabeceraId(cab.getId());
        det.setDia(LocalDate.of(2026, 7, 17));
        det.setTipoDia("TARDANZA");
        det.setMinutosTardanza(289);

        when(cabeceraRepository.findByEmpleadoIdAndPeriodoAndActivo(EMPLEADO_ID, "2026-07", 1))
                .thenReturn(java.util.Optional.of(cab));
        when(detalleRepository.findByCabeceraIdOrderByDia(cab.getId())).thenReturn(List.of(det));
        when(papeletaJustificacionResolver.justificarVacacionSobreMarcacion(any(), any()))
                .thenReturn(java.util.Optional.empty());

        service.reconciliarPorPapeletaAprobada(solicitud, tipo);

        assertThat(det.getTipoDia()).isEqualTo("LABORAL");
        assertThat(det.getMinutosTardanza()).isEqualTo(0);
        assertThat(det.getPapeletaAutorizada()).isEqualTo(1);
        assertThat(det.getObservacion()).contains("Comisión de Servicio");
    }

    /**
     * Caso real reportado (TINEO PONGO PERCY, día 17): el día ya había sido autorizado
     * MANUALMENTE antes de que existiera la limpieza de MINUTOS_TARDANZA/OBSERVACION (Punto 1
     * del bug) — quedó con TIPO_DIA=LABORAL, PAPELETA_AUTORIZADA=1, pero 289 min y "NO
     * AUTORIZADO" arrastrados. El backfill debe repararlo igual, no solo los que siguen en
     * TARDANZA (si no, un registro "ya autorizado" queda sucio para siempre).
     */
    @Test
    void reconciliarPorPapeletaAprobada_repara_dia_ya_autorizado_con_datos_viejos() {
        SolicitudRrhh solicitud = new SolicitudRrhh();
        solicitud.setId(348L);
        solicitud.setEmpleadoId(EMPLEADO_ID);
        solicitud.setFechaInicio(LocalDate.of(2026, 7, 17));
        solicitud.setFechaFin(LocalDate.of(2026, 7, 17));

        TipoSolicitudRrhh tipo = new TipoSolicitudRrhh();
        tipo.setCodigo("006");
        tipo.setNombre("Comisión de Servicio");
        tipo.setJustificaAsistencia(0);
        solicitud.setTipoSolicitud(tipo);

        AsistenciaCabecera cab = new AsistenciaCabecera();
        cab.setId(9001L);
        cab.setEmpleadoId(EMPLEADO_ID);
        cab.setPeriodo("2026-07");
        cab.setActivo(1);

        AsistenciaDetalle det = new AsistenciaDetalle();
        det.setId(51849L);
        det.setCabeceraId(cab.getId());
        det.setDia(LocalDate.of(2026, 7, 17));
        det.setTipoDia("LABORAL");
        det.setMinutosTardanza(289);
        det.setObservacion("NO AUTORIZADO");
        det.setPapeletaAutorizada(1);

        when(cabeceraRepository.findByEmpleadoIdAndPeriodoAndActivo(EMPLEADO_ID, "2026-07", 1))
                .thenReturn(java.util.Optional.of(cab));
        when(detalleRepository.findByCabeceraIdOrderByDia(cab.getId())).thenReturn(List.of(det));
        when(papeletaJustificacionResolver.justificarVacacionSobreMarcacion(any(), any()))
                .thenReturn(java.util.Optional.empty());

        service.reconciliarPorPapeletaAprobada(solicitud, tipo);

        assertThat(det.getTipoDia()).isEqualTo("LABORAL");
        assertThat(det.getMinutosTardanza()).isEqualTo(0);
        assertThat(det.getPapeletaAutorizada()).isEqualTo(1);
        assertThat(det.getObservacion()).doesNotContain("NO AUTORIZADO");
        assertThat(det.getObservacion()).contains("Comisión de Servicio");
    }

    /** Backfill (punto 5 de la directiva): reconcilia cada papeleta APROBADA de Vacaciones. */
    @Test
    void backfillReconciliarVacacionesAprobadas_reconcilia_cada_papeleta_encontrada() {
        TipoSolicitudRrhh tipoVacaciones = new TipoSolicitudRrhh();
        tipoVacaciones.setCodigo("012");
        tipoVacaciones.setJustificaAsistencia(1);

        SolicitudRrhh s1 = new SolicitudRrhh();
        s1.setId(243L);
        s1.setEmpleadoId(2021L);
        s1.setFechaInicio(LocalDate.of(2026, 7, 10));
        s1.setFechaFin(LocalDate.of(2026, 7, 15));
        s1.setTipoSolicitud(tipoVacaciones);

        SolicitudRrhh s2 = new SolicitudRrhh();
        s2.setId(270L);
        s2.setEmpleadoId(1767L);
        s2.setFechaInicio(LocalDate.of(2026, 7, 13));
        s2.setFechaFin(LocalDate.of(2026, 7, 16));
        s2.setTipoSolicitud(tipoVacaciones);

        when(solicitudRrhhRepository.findAprobadasQueJustificanAsistencia(9L))
                .thenReturn(List.of(s1, s2));
        when(solicitudRrhhRepository.findAprobadasPorTipoCodigoIn(anyLong(), any()))
                .thenReturn(List.of());
        // Sin cabeceras/justificantes configurados: cada llamada a reconciliar termina creando
        // la cabecera de vacaciones desde cero (Fase A) — lo que importa aquí es que procese
        // las 2 papeletas; los detalles de esa creación tienen sus propios tests dedicados.
        when(papeletaJustificacionResolver.cargarJustificantes(any(), any())).thenReturn(List.of());
        when(calendarioLaboralService.paraPeriodo(any(), any()))
                .thenReturn(new CalendarioLaboralService.Calendario(Set.of(), Set.of(6, 7), Map.of()));
        when(baseResolver.resolver(any())).thenReturn(new BaseAsistenciaResult());
        when(cabeceraRepository.save(any(AsistenciaCabecera.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        int procesadas = service.backfillReconciliarVacacionesAprobadas();

        assertThat(procesadas).isEqualTo(2);
        verify(papeletaJustificacionResolver).cargarJustificantes(2021L, LocalDate.of(2026, 7, 15));
        verify(papeletaJustificacionResolver).cargarJustificantes(1767L, LocalDate.of(2026, 7, 16));
    }

    private Feriado feriado(LocalDate fecha) {
        Feriado f = new Feriado();
        f.setAnio(fecha.getYear());
        f.setFecha(fecha);
        f.setNombre("Fiestas Patrias");
        f.setTipo("NACIONAL");
        f.setActivo(1);
        return f;
    }

    /** Backfill de feriados (caso real: 23/28/29-jul-2026 quedaron como LABORAL sin marcación). */
    @Test
    void backfillFeriadosMalClasificados_corrige_LABORAL_sin_marcacion_a_FERIADO() {
        AsistenciaCabecera cab = new AsistenciaCabecera();
        cab.setId(2024L);
        cab.setEmpleadoId(1767L);
        cab.setPeriodo("2026-07");

        AsistenciaDetalle diaFeriadoSinMarcar = new AsistenciaDetalle();
        diaFeriadoSinMarcar.setDia(LocalDate.of(2026, 7, 28));
        diaFeriadoSinMarcar.setTipoDia("LABORAL");
        diaFeriadoSinMarcar.setMarcaEntrada("");
        diaFeriadoSinMarcar.setMarcaSalida("");

        AsistenciaDetalle diaFeriadoTrabajado = new AsistenciaDetalle();
        diaFeriadoTrabajado.setDia(LocalDate.of(2026, 7, 29));
        diaFeriadoTrabajado.setTipoDia("LABORAL");
        diaFeriadoTrabajado.setMarcaEntrada("08:00");
        diaFeriadoTrabajado.setMarcaSalida("17:00");

        AsistenciaDetalle diaNormal = new AsistenciaDetalle();
        diaNormal.setDia(LocalDate.of(2026, 7, 14));
        diaNormal.setTipoDia("LABORAL");
        diaNormal.setMarcaEntrada("08:00");
        diaNormal.setMarcaSalida("17:00");

        when(cabeceraRepository.findByActivo(1)).thenReturn(List.of(cab));
        when(feriadoRepository.findByAnioInAndActivo(Set.of(2026), 1))
                .thenReturn(List.of(feriado(LocalDate.of(2026, 7, 28))));
        when(periodoPlanillaRepository.findByPeriodoAndActivo("2026-07", 1)).thenReturn(Optional.empty());
        when(detalleRepository.findByCabeceraIdOrderByDia(2024L))
                .thenReturn(List.of(diaFeriadoSinMarcar, diaFeriadoTrabajado, diaNormal));

        int corregidos = service.backfillFeriadosMalClasificados();

        assertThat(corregidos).isEqualTo(1);
        assertThat(diaFeriadoSinMarcar.getTipoDia()).isEqualTo("FERIADO");
        // Feriado TRABAJADO (sí fichó) no se toca — sigue LABORAL.
        assertThat(diaFeriadoTrabajado.getTipoDia()).isEqualTo("LABORAL");
        // Día normal (no es feriado del catálogo) tampoco se toca.
        assertThat(diaNormal.getTipoDia()).isEqualTo("LABORAL");
        verify(detalleRepository).save(diaFeriadoSinMarcar);
    }

    /**
     * Caso real 2026-08-11: el 27/07/2026 (D.S. N° 075-2026-PCM, día no laborable
     * compensable) estaba ausente de INDECI_FERIADO al momento del import, así que el
     * relleno de calendario ({@code AsistenciaImportService#diaFalta}) lo generó como
     * FALTA para todos los empleados. Tras sembrar el feriado (V012_59), este backfill
     * debe corregir también FALTA → FERIADO, no solo LABORAL/OBSERVADO.
     */
    @Test
    void backfillFeriadosMalClasificados_corrige_FALTA_sin_marcacion_a_FERIADO() {
        AsistenciaCabecera cab = new AsistenciaCabecera();
        cab.setId(2025L);
        cab.setEmpleadoId(1767L);
        cab.setPeriodo("2026-07");

        AsistenciaDetalle diaFeriadoComoFalta = new AsistenciaDetalle();
        diaFeriadoComoFalta.setDia(LocalDate.of(2026, 7, 27));
        diaFeriadoComoFalta.setTipoDia("FALTA");
        diaFeriadoComoFalta.setMarcaEntrada("");
        diaFeriadoComoFalta.setMarcaSalida("");

        AsistenciaDetalle diaNormal = new AsistenciaDetalle();
        diaNormal.setDia(LocalDate.of(2026, 7, 14));
        diaNormal.setTipoDia("LABORAL");
        diaNormal.setMarcaEntrada("08:00");
        diaNormal.setMarcaSalida("17:00");

        when(cabeceraRepository.findByActivo(1)).thenReturn(List.of(cab));
        when(feriadoRepository.findByAnioInAndActivo(Set.of(2026), 1))
                .thenReturn(List.of(feriado(LocalDate.of(2026, 7, 27))));
        when(detalleRepository.findByCabeceraIdOrderByDia(2025L))
                .thenReturn(List.of(diaFeriadoComoFalta, diaNormal));

        int corregidos = service.backfillFeriadosMalClasificados();

        assertThat(corregidos).isEqualTo(1);
        assertThat(diaFeriadoComoFalta.getTipoDia()).isEqualTo("FERIADO");
        assertThat(diaNormal.getTipoDia()).isEqualTo("LABORAL");
        verify(detalleRepository).save(diaFeriadoComoFalta);
    }

    @Test
    void backfillFeriadosMalClasificados_periodo_bloqueado_no_se_toca() {
        AsistenciaCabecera cab = new AsistenciaCabecera();
        cab.setId(3000L);
        cab.setEmpleadoId(500L);
        cab.setPeriodo("2026-07");

        when(cabeceraRepository.findByActivo(1)).thenReturn(List.of(cab));
        when(feriadoRepository.findByAnioInAndActivo(Set.of(2026), 1))
                .thenReturn(List.of(feriado(LocalDate.of(2026, 7, 28))));

        com.indeci.rrhh.entity.PeriodoPlanilla periodoCerrado = new com.indeci.rrhh.entity.PeriodoPlanilla();
        periodoCerrado.setEstado("CERRADO");
        when(periodoPlanillaRepository.findByPeriodoAndActivo("2026-07", 1))
                .thenReturn(Optional.of(periodoCerrado));

        int corregidos = service.backfillFeriadosMalClasificados();

        assertThat(corregidos).isEqualTo(0);
        verify(detalleRepository, never()).findByCabeceraIdOrderByDia(any());
    }

    /** Backfill de días huérfanos (caso real que motiva el fix de fusión en guardarImportacion). */
    @Test
    @SuppressWarnings("unchecked")
    void backfillFusionarDiasHuerfanos_inserta_dias_faltantes_de_version_inactiva_y_recalcula() {
        AsistenciaCabecera activa = new AsistenciaCabecera();
        activa.setId(2024L);
        activa.setEmpleadoId(1767L);
        activa.setPeriodo("2026-07");
        activa.setActivo(1);
        activa.setVersion(2);
        activa.setRemuneracionBase(3000.0);

        AsistenciaCabecera inactiva = new AsistenciaCabecera();
        inactiva.setId(2023L);
        inactiva.setEmpleadoId(1767L);
        inactiva.setPeriodo("2026-07");
        inactiva.setActivo(0);
        inactiva.setVersion(1);

        List<AsistenciaDetalle> detalleActivoInicial = new java.util.ArrayList<>();
        for (int d = 16; d <= 31; d++) {
            detalleActivoInicial.add(detalleEnFecha("LABORAL", LocalDate.of(2026, 7, d)));
        }
        List<AsistenciaDetalle> detalleInactivo = new java.util.ArrayList<>();
        for (int d = 1; d <= 15; d++) {
            detalleInactivo.add(detalleEnFecha("LABORAL", LocalDate.of(2026, 7, d)));
        }
        // Segunda invocación (dentro de recalcularCabeceraDesdeDetalle): simula el detalle YA
        // fusionado tras el insertarLote, con los 31 días del mes.
        List<AsistenciaDetalle> detalleActivoFusionado = new java.util.ArrayList<>();
        detalleActivoFusionado.addAll(detalleInactivo);
        detalleActivoFusionado.addAll(detalleActivoInicial);

        when(cabeceraRepository.findByActivo(1)).thenReturn(List.of(activa));
        when(periodoPlanillaRepository.findByPeriodoAndActivo("2026-07", 1)).thenReturn(Optional.empty());
        when(cabeceraRepository.findByEmpleadoIdAndPeriodoOrderByVersionDesc(1767L, "2026-07"))
                .thenReturn(List.of(activa, inactiva));
        when(detalleRepository.findByCabeceraIdOrderByDia(2024L))
                .thenReturn(detalleActivoInicial, detalleActivoFusionado);
        when(detalleRepository.findByCabeceraIdOrderByDia(2023L)).thenReturn(detalleInactivo);

        int corregidas = service.backfillFusionarDiasHuerfanos();

        assertThat(corregidas).isEqualTo(1);

        ArgumentCaptor<List<AsistenciaDetalle>> capt = ArgumentCaptor.forClass(List.class);
        verify(detalleJdbcWriter).insertarLote(capt.capture());
        assertThat(capt.getValue()).hasSize(15);
        assertThat(capt.getValue().stream().map(AsistenciaDetalle::getDia).distinct().count()).isEqualTo(15L);

        // recalcularCabeceraDesdeDetalle recalcula sobre el detalle YA fusionado (31 días).
        verify(cabeceraRepository).save(argThat(c ->
                c.getId().equals(2024L) && c.getDiasLaborados() == 31));
    }

    @Test
    void backfillFusionarDiasHuerfanos_excluye_periodo_cerrado_o_aprobado() {
        AsistenciaCabecera activa = new AsistenciaCabecera();
        activa.setId(3000L);
        activa.setEmpleadoId(500L);
        activa.setPeriodo("2026-07");
        activa.setActivo(1);

        when(cabeceraRepository.findByActivo(1)).thenReturn(List.of(activa));
        com.indeci.rrhh.entity.PeriodoPlanilla periodoCerrado = new com.indeci.rrhh.entity.PeriodoPlanilla();
        periodoCerrado.setEstado("CERRADO");
        when(periodoPlanillaRepository.findByPeriodoAndActivo("2026-07", 1))
                .thenReturn(Optional.of(periodoCerrado));

        int corregidas = service.backfillFusionarDiasHuerfanos();

        assertThat(corregidas).isEqualTo(0);
        verify(detalleRepository, never()).findByCabeceraIdOrderByDia(any());
        verify(detalleJdbcWriter, never()).insertarLote(any());
    }

    @Test
    void backfillFusionarDiasHuerfanos_es_idempotente_no_duplica_dias_ya_presentes() {
        AsistenciaCabecera activa = new AsistenciaCabecera();
        activa.setId(2024L);
        activa.setEmpleadoId(1767L);
        activa.setPeriodo("2026-07");
        activa.setActivo(1);

        AsistenciaCabecera inactiva = new AsistenciaCabecera();
        inactiva.setId(2023L);
        inactiva.setEmpleadoId(1767L);
        inactiva.setPeriodo("2026-07");
        inactiva.setActivo(0);

        // El detalle activo YA tiene el mes completo — nada que arrastrar de la versión inactiva.
        List<AsistenciaDetalle> detalleActivoCompleto = new java.util.ArrayList<>();
        for (int d = 1; d <= 31; d++) {
            detalleActivoCompleto.add(detalleEnFecha("LABORAL", LocalDate.of(2026, 7, d)));
        }

        when(cabeceraRepository.findByActivo(1)).thenReturn(List.of(activa));
        when(periodoPlanillaRepository.findByPeriodoAndActivo("2026-07", 1)).thenReturn(Optional.empty());
        when(cabeceraRepository.findByEmpleadoIdAndPeriodoOrderByVersionDesc(1767L, "2026-07"))
                .thenReturn(List.of(activa, inactiva));
        when(detalleRepository.findByCabeceraIdOrderByDia(2024L)).thenReturn(detalleActivoCompleto);
        when(detalleRepository.findByCabeceraIdOrderByDia(2023L))
                .thenReturn(List.of(detalleEnFecha("LABORAL", LocalDate.of(2026, 7, 5))));

        int corregidas = service.backfillFusionarDiasHuerfanos();

        assertThat(corregidas).isEqualTo(0);
        verify(detalleJdbcWriter, never()).insertarLote(any());
        verify(cabeceraRepository, never()).save(any());
    }

    // ── Backfill de salida anticipada (2026-08-07) ──

    private com.indeci.rrhh.entity.AsistenciaImportacionFila filaStaging(Long empleadoId, LocalDate fecha, int tiempoAntesSalMin) {
        com.indeci.rrhh.entity.AsistenciaImportacionFila f = new com.indeci.rrhh.entity.AsistenciaImportacionFila();
        f.setEmpleadoId(empleadoId);
        f.setFecha(fecha);
        f.setTiempoAntesSalMin(tiempoAntesSalMin);
        return f;
    }

    /** Caso real (staging): DNI 10002521, 2026-07-20 — entrada 08:26, salida 16:30, 60 min T/AS, sin tardanza. */
    @Test
    void backfillSalidaAnticipada_corrige_LABORAL_con_dato_crudo_a_OBSERVADO() {
        AsistenciaCabecera cab = new AsistenciaCabecera();
        cab.setId(2024L);
        cab.setEmpleadoId(1767L);
        cab.setPeriodo("2026-07");

        AsistenciaDetalle diaAfectado = detalleEnFecha("LABORAL", LocalDate.of(2026, 7, 20));
        AsistenciaDetalle diaSinDatoCrudo = detalleEnFecha("LABORAL", LocalDate.of(2026, 7, 21));

        when(cabeceraRepository.findByActivo(1)).thenReturn(List.of(cab));
        when(periodoPlanillaRepository.findByPeriodoAndActivo("2026-07", 1)).thenReturn(Optional.empty());
        when(detalleRepository.findByCabeceraIdOrderByDia(2024L))
                .thenReturn(List.of(diaAfectado, diaSinDatoCrudo));
        when(importacionFilaRepository.findByEmpleadoIdAndFecha(1767L, LocalDate.of(2026, 7, 20)))
                .thenReturn(List.of(filaStaging(1767L, LocalDate.of(2026, 7, 20), 60)));
        when(importacionFilaRepository.findByEmpleadoIdAndFecha(1767L, LocalDate.of(2026, 7, 21)))
                .thenReturn(List.of());

        int corregidos = service.backfillSalidaAnticipada();

        assertThat(corregidos).isEqualTo(1);
        assertThat(diaAfectado.getTipoDia()).isEqualTo("OBSERVADO");
        assertThat(diaAfectado.getMinutosSalidaAnticipada()).isEqualTo(60);
        assertThat(diaAfectado.getObservacion()).contains("backfill de salida anticipada");
        // Sin dato crudo real: no se toca aunque siga LABORAL.
        assertThat(diaSinDatoCrudo.getTipoDia()).isEqualTo("LABORAL");
        verify(detalleRepository).save(diaAfectado);
        verify(detalleRepository, never()).save(diaSinDatoCrudo);
    }

    @Test
    void backfillSalidaAnticipada_nunca_toca_dias_que_no_son_exactamente_LABORAL() {
        AsistenciaCabecera cab = new AsistenciaCabecera();
        cab.setId(2024L);
        cab.setEmpleadoId(1767L);
        cab.setPeriodo("2026-07");

        AsistenciaDetalle tardanza = detalleEnFecha("TARDANZA", LocalDate.of(2026, 7, 14));
        AsistenciaDetalle vacaciones = detalleEnFecha("VACACIONES", LocalDate.of(2026, 7, 15));

        when(cabeceraRepository.findByActivo(1)).thenReturn(List.of(cab));
        when(periodoPlanillaRepository.findByPeriodoAndActivo("2026-07", 1)).thenReturn(Optional.empty());
        when(detalleRepository.findByCabeceraIdOrderByDia(2024L)).thenReturn(List.of(tardanza, vacaciones));

        int corregidos = service.backfillSalidaAnticipada();

        assertThat(corregidos).isEqualTo(0);
        assertThat(tardanza.getTipoDia()).isEqualTo("TARDANZA");
        assertThat(vacaciones.getTipoDia()).isEqualTo("VACACIONES");
        verify(importacionFilaRepository, never()).findByEmpleadoIdAndFecha(any(), any());
        verify(detalleRepository, never()).save(any());
    }

    @Test
    void backfillSalidaAnticipada_periodo_bloqueado_no_se_toca() {
        AsistenciaCabecera cab = new AsistenciaCabecera();
        cab.setId(3000L);
        cab.setEmpleadoId(500L);
        cab.setPeriodo("2026-07");

        com.indeci.rrhh.entity.PeriodoPlanilla periodoAprobado = new com.indeci.rrhh.entity.PeriodoPlanilla();
        periodoAprobado.setEstado("APROBADO");
        when(cabeceraRepository.findByActivo(1)).thenReturn(List.of(cab));
        when(periodoPlanillaRepository.findByPeriodoAndActivo("2026-07", 1))
                .thenReturn(Optional.of(periodoAprobado));

        int corregidos = service.backfillSalidaAnticipada();

        assertThat(corregidos).isEqualTo(0);
        verify(detalleRepository, never()).findByCabeceraIdOrderByDia(any());
    }

    @Test
    void backfillTurno24h_delegaAlReconciliadorYRecalculaCabecera_cuandoHuboCambios() {
        AsistenciaCabecera cab = new AsistenciaCabecera();
        cab.setId(4001L);
        cab.setEmpleadoId(9001L);
        cab.setPeriodo("2026-07");

        when(cabeceraRepository.findByActivo(1)).thenReturn(List.of(cab));
        when(periodoPlanillaRepository.findByPeriodoAndActivo("2026-07", 1)).thenReturn(Optional.empty());
        when(turno24hReconciliador.reconciliar(cab)).thenReturn(2);
        when(detalleRepository.findByCabeceraIdOrderByDia(4001L))
                .thenReturn(List.of(detalleEnFecha("LABORAL", LocalDate.of(2026, 7, 10))));

        int corregidos = service.backfillTurno24h();

        assertThat(corregidos).isEqualTo(2);
        verify(turno24hReconciliador).reconciliar(cab);
        verify(cabeceraRepository).save(cab);
    }

    @Test
    void backfillTurno24h_sinCambios_noRecalculaCabecera() {
        AsistenciaCabecera cab = new AsistenciaCabecera();
        cab.setId(4002L);
        cab.setEmpleadoId(9002L);
        cab.setPeriodo("2026-07");

        when(cabeceraRepository.findByActivo(1)).thenReturn(List.of(cab));
        when(periodoPlanillaRepository.findByPeriodoAndActivo("2026-07", 1)).thenReturn(Optional.empty());
        when(turno24hReconciliador.reconciliar(cab)).thenReturn(0);

        int corregidos = service.backfillTurno24h();

        assertThat(corregidos).isEqualTo(0);
        verify(cabeceraRepository, never()).save(any());
        verify(detalleRepository, never()).findByCabeceraIdOrderByDia(any());
    }

    @Test
    void backfillTurno24h_periodoBloqueado_noSeToca() {
        AsistenciaCabecera cab = new AsistenciaCabecera();
        cab.setId(4003L);
        cab.setEmpleadoId(9003L);
        cab.setPeriodo("2026-07");

        com.indeci.rrhh.entity.PeriodoPlanilla periodoCerrado = new com.indeci.rrhh.entity.PeriodoPlanilla();
        periodoCerrado.setEstado("CERRADO");
        when(cabeceraRepository.findByActivo(1)).thenReturn(List.of(cab));
        when(periodoPlanillaRepository.findByPeriodoAndActivo("2026-07", 1))
                .thenReturn(Optional.of(periodoCerrado));

        int corregidos = service.backfillTurno24h();

        assertThat(corregidos).isEqualTo(0);
        verify(turno24hReconciliador, never()).reconciliar(any());
    }

    // ── Backfill de recálculo — RIS INDECI Art. 25.5 (2026-08-07) ──

    /**
     * Caso real que motivó este backfill: DNI 06025079, período 2026-07 — antes de este fix, 2
     * días en Omisión de marca no se reflejaban en DIAS_FALTA (quedaba en 0 aportado por ellos).
     */
    @Test
    void backfillRecalcularOmisionComoFalta_recalcula_cabecera_con_omision() {
        AsistenciaCabecera cab = new AsistenciaCabecera();
        cab.setId(3084L);
        cab.setEmpleadoId(1320L);
        cab.setPeriodo("2026-07");
        cab.setRemuneracionBase(1567.73);
        // Valores desactualizados (pre-fix): las 2 omisiones de abajo aún no contaban.
        cab.setDiasFalta(0);
        cab.setDescuentoFalta(0.0);

        AsistenciaDetalle omision1 = detalleEnFecha("OMISION_MARCACION", LocalDate.of(2026, 7, 14));
        AsistenciaDetalle omision2 = detalleEnFecha("OMISION_MARCACION", LocalDate.of(2026, 7, 22));
        AsistenciaDetalle laboral = detalleEnFecha("LABORAL", LocalDate.of(2026, 7, 15));

        when(cabeceraRepository.findByActivo(1)).thenReturn(List.of(cab));
        when(periodoPlanillaRepository.findByPeriodoAndActivo("2026-07", 1)).thenReturn(Optional.empty());
        when(detalleRepository.findByCabeceraIdOrderByDia(3084L))
                .thenReturn(List.of(omision1, omision2, laboral));

        int corregidas = service.backfillRecalcularOmisionComoFalta();

        assertThat(corregidas).isEqualTo(1);
        // recalcularCabeceraDesdeDetalle reemplaza el agregado completo: las 2 omisiones ahora
        // cuentan como Falta (RIS Art. 25.5) → 2 días, 2 × (1567.73/30) = S/ 104.52.
        assertThat(cab.getDiasFalta()).isEqualTo(2);
        assertThat(cab.getDescuentoFalta()).isEqualTo(104.52);
        verify(cabeceraRepository).save(cab);
    }

    @Test
    void backfillRecalcularOmisionComoFalta_sin_omision_no_se_toca() {
        AsistenciaCabecera cab = new AsistenciaCabecera();
        cab.setId(3085L);
        cab.setEmpleadoId(1321L);
        cab.setPeriodo("2026-07");

        when(cabeceraRepository.findByActivo(1)).thenReturn(List.of(cab));
        when(periodoPlanillaRepository.findByPeriodoAndActivo("2026-07", 1)).thenReturn(Optional.empty());
        when(detalleRepository.findByCabeceraIdOrderByDia(3085L))
                .thenReturn(List.of(detalleEnFecha("LABORAL", LocalDate.of(2026, 7, 15))));

        int corregidas = service.backfillRecalcularOmisionComoFalta();

        assertThat(corregidas).isZero();
        verify(cabeceraRepository, never()).save(any());
    }

    @Test
    void backfillRecalcularOmisionComoFalta_periodo_bloqueado_no_se_toca() {
        AsistenciaCabecera cab = new AsistenciaCabecera();
        cab.setId(3086L);
        cab.setEmpleadoId(1322L);
        cab.setPeriodo("2026-07");

        com.indeci.rrhh.entity.PeriodoPlanilla periodoAprobado = new com.indeci.rrhh.entity.PeriodoPlanilla();
        periodoAprobado.setEstado("APROBADO");
        when(cabeceraRepository.findByActivo(1)).thenReturn(List.of(cab));
        when(periodoPlanillaRepository.findByPeriodoAndActivo("2026-07", 1))
                .thenReturn(Optional.of(periodoAprobado));

        int corregidas = service.backfillRecalcularOmisionComoFalta();

        assertThat(corregidas).isZero();
        verify(detalleRepository, never()).findByCabeceraIdOrderByDia(any());
    }
}
