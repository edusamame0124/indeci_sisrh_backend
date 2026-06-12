package com.indeci.rrhh.service;

import com.indeci.audit.context.AuditoriaContext;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.EventoDistribucionMesDto;
import com.indeci.rrhh.dto.EventoPeriodoDto;
import com.indeci.rrhh.dto.EventoPeriodoPageDto;
import com.indeci.rrhh.dto.EventoPeriodoResponseDto;
import com.indeci.rrhh.entity.EmpleadoEvento;
import com.indeci.rrhh.entity.TipoEvento;
import com.indeci.rrhh.repository.EmpleadoEventoRepository;
import com.indeci.rrhh.repository.EmpleadoRepository;
import com.indeci.rrhh.repository.EventoDistribucionMesRepository;
import com.indeci.rrhh.repository.TipoEventoRepository;
import com.indeci.rrhh.service.support.DistribucionMensualCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F2.5 — Tests del CRUD de eventos del período + validador no-solape.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EventoPeriodoServiceTest {

    @Mock private EmpleadoEventoRepository repository;
    @Mock private EmpleadoRepository empleadoRepository;
    @Mock private TipoEventoRepository tipoRepository;
    @Mock private EventoDistribucionMesRepository distribucionRepository;
    @Mock private AuditoriaContext auditoriaContext;

    @InjectMocks private EventoPeriodoService service;

    private static final Long EMP_ID = 42L;
    private static final Long TIPO_MATERNIDAD = 1L;
    private static final Long TIPO_LACTANCIA = 5L;
    private static final Long TIPO_PERMISO   = 10L;

    private TipoEvento tipoMaternidad;
    private TipoEvento tipoLactancia;
    private TipoEvento tipoPermiso;

    @BeforeEach
    void setUp() {
        tipoMaternidad = tipo(TIPO_MATERNIDAD, "MATERNIDAD",
                /*afectaDias*/ "S", /*generaSubsidio*/ "S",
                /*requiereAdjunto*/ "S", /*permiteSolape*/ "N");
        tipoLactancia = tipo(TIPO_LACTANCIA, "LACTANCIA",
                "N", "N", "N", "S");
        tipoPermiso = tipo(TIPO_PERMISO, "PERMISO_PERSONAL",
                "S", "N", "S", "N");

        when(tipoRepository.findById(TIPO_MATERNIDAD)).thenReturn(Optional.of(tipoMaternidad));
        when(tipoRepository.findById(TIPO_LACTANCIA)).thenReturn(Optional.of(tipoLactancia));
        when(tipoRepository.findById(TIPO_PERMISO)).thenReturn(Optional.of(tipoPermiso));

        // Sin solapes por default.
        when(repository.findSolapados(any(), any(), any(), any()))
                .thenReturn(List.of());

        // save echo (con id asignado).
        when(repository.save(any(EmpleadoEvento.class)))
                .thenAnswer(inv -> {
                    EmpleadoEvento e = inv.getArgument(0);
                    if (e.getId() == null) e.setId(100L);
                    return e;
                });

        when(empleadoRepository.findPersonaResumenByEmpleadoIds(any()))
                .thenAnswer(inv -> {
                    List<Long> ids = inv.getArgument(0);
                    return ids.stream()
                            .map(id -> new Object[] {
                                    id, "EMPLEADO " + id, "1234567" + id % 10 })
                            .toList();
                });
    }

    // ===================== CASOS FELICES =====================

    @Test
    void crear_maternidad_98dias_deriva_periodo_y_distribucion() {
        LocalDate inicio = LocalDate.of(2026, 5, 1);
        LocalDate fin = DistribucionMensualCalculator.calcularFechaFin(inicio, 98);
        EventoPeriodoDto dto = dtoMaternidadCompleto(inicio, fin, 98, null);
        dto.setSustentoLegajoDocId(99L);

        EventoPeriodoResponseDto r = service.crear(dto);

        assertThat(r.getId()).isEqualTo(100L);
        assertThat(r.getEstado()).isEqualTo("REGISTRADO");
        assertThat(r.getPeriodo()).isEqualTo("202605");
        assertThat(r.getDiasAfectos()).isEqualTo(98);
        assertThat(r.getDuracionLegal()).isEqualTo(98);
        assertThat(r.getTipoEventoCodigo()).isEqualTo("MATERNIDAD");

        verify(distribucionRepository, atLeastOnce()).save(any());
    }

    @Test
    void crear_maternidad_128_multimes_persiste_tramos() {
        LocalDate inicio = LocalDate.of(2026, 5, 1);
        LocalDate fin = DistribucionMensualCalculator.calcularFechaFin(inicio, 128);
        EventoPeriodoDto dto = dtoMaternidadCompleto(inicio, fin, 128, "NACIMIENTO_MULTIPLE");
        dto.setSustentoLegajoDocId(99L);

        EventoPeriodoResponseDto r = service.crear(dto);

        assertThat(r.getDiasAfectos()).isEqualTo(128);
        assertThat(r.getMotivoExtension()).isEqualTo("NACIMIENTO_MULTIPLE");
        verify(distribucionRepository, atLeastOnce()).save(any());
    }

    @Test
    void crear_normaliza_periodo_con_guion_a_canonico() {
        LocalDate inicio = LocalDate.of(2026, 5, 1);
        LocalDate fin = DistribucionMensualCalculator.calcularFechaFin(inicio, 98);
        EventoPeriodoDto dto = dtoMaternidadCompleto(inicio, fin, 98, null);
        dto.setSustentoLegajoDocId(99L);
        dto.setPeriodo("2026-05");

        EventoPeriodoResponseDto r = service.crear(dto);

        // Guarda en formato YYYYMM sin guión.
        assertThat(r.getPeriodo()).isEqualTo("202605");
    }

    @Test
    void crear_lactancia_permite_solape_aunque_haya_otro_evento() {
        // LACTANCIA permite solape (lactancia parcial sobre maternidad).
        // Incluso si findSolapados devolviera filas, no debería bloquear...
        // PERO al permiteSolape=S, el service ni siquiera consulta solapes.
        EventoPeriodoDto dto = baseDto(TIPO_LACTANCIA,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 12, 31));

        // El tipo lactancia NO requiere adjunto.
        EventoPeriodoResponseDto r = service.crear(dto);

        assertThat(r.getTipoEventoCodigo()).isEqualTo("LACTANCIA");
        // Verifica que NO se consultó solape (porque tipo.permiteSolape='S').
        verify(repository, never()).findSolapados(any(), any(), any(), any());
    }

    @Test
    void actualizar_excluye_propio_id_del_check_solape() {
        EmpleadoEvento existente = new EmpleadoEvento();
        existente.setId(200L);
        existente.setEmpleadoId(EMP_ID);
        existente.setTipoEventoId(TIPO_MATERNIDAD);
        existente.setFechaInicio(LocalDate.of(2026, 5, 1));
        existente.setFechaFin(LocalDate.of(2026, 5, 30));
        existente.setEstado("REGISTRADO");
        existente.setActivo(1);
        when(repository.findById(200L)).thenReturn(Optional.of(existente));

        LocalDate inicio = LocalDate.of(2026, 5, 5);
        LocalDate fin = DistribucionMensualCalculator.calcularFechaFin(inicio, 98);
        EventoPeriodoDto dto = dtoMaternidadCompleto(inicio, fin, 98, null);
        dto.setSustentoLegajoDocId(99L);

        service.actualizar(200L, dto);

        // Verifica que findSolapados se llamó con idExcluir = 200.
        verify(repository).findSolapados(eq(EMP_ID), any(), any(), eq(200L));
    }

    @Test
    void cambiar_estado_a_VALIDADO_funciona() {
        EmpleadoEvento e = new EmpleadoEvento();
        e.setId(200L);
        e.setEstado("REGISTRADO");
        when(repository.findById(200L)).thenReturn(Optional.of(e));

        EventoPeriodoResponseDto r = service.cambiarEstado(200L, "validado");

        assertThat(r.getEstado()).isEqualTo("VALIDADO");
    }

    @Test
    void eliminar_baja_logica_activo_0() {
        EmpleadoEvento e = new EmpleadoEvento();
        e.setId(200L);
        e.setActivo(1);
        when(repository.findById(200L)).thenReturn(Optional.of(e));

        service.eliminar(200L);

        assertThat(e.getActivo()).isEqualTo(0);
        verify(repository).save(e);
    }

    // ===================== CASOS DE ERROR =====================

    @Test
    void crear_sin_empleadoId_lanza() {
        EventoPeriodoDto dto = baseDto(TIPO_MATERNIDAD,
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 30));
        dto.setEmpleadoId(null);

        assertThatThrownBy(() -> service.crear(dto))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("empleadoId");
    }

    @Test
    void crear_sin_tipoEventoId_lanza() {
        EventoPeriodoDto dto = baseDto(null,
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 30));

        assertThatThrownBy(() -> service.crear(dto))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("tipoEventoId");
    }

    @Test
    void crear_tipo_inexistente_lanza() {
        EventoPeriodoDto dto = baseDto(999L,
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 30));
        when(tipoRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.crear(dto))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("no existe");
    }

    @Test
    void crear_tipo_inactivo_lanza() {
        TipoEvento inactivo = tipo(50L, "OLD",
                "N", "N", "N", "N");
        inactivo.setActivo(0);
        when(tipoRepository.findById(50L)).thenReturn(Optional.of(inactivo));

        EventoPeriodoDto dto = baseDto(50L,
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 30));

        assertThatThrownBy(() -> service.crear(dto))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("inactivo");
    }

    @Test
    void crear_sin_fechas_lanza() {
        EventoPeriodoDto dto = baseDto(TIPO_MATERNIDAD, null, null);

        assertThatThrownBy(() -> service.crear(dto))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("fecha");
    }

    @Test
    void crear_fechaFin_anterior_a_fechaInicio_lanza() {

        EventoPeriodoDto dto = baseDto(
                TIPO_PERMISO,
                LocalDate.of(2026, 5, 30),
                LocalDate.of(2026, 5, 1));

        dto.setSustentoLegajoDocId(99L);

        assertThatThrownBy(() -> service.crear(dto))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("fecha fin");
    }

    @Test
    void crear_diasAfectos_negativos_lanza() {
        EventoPeriodoDto dto = baseDto(TIPO_PERMISO,
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 30));
        dto.setSustentoLegajoDocId(99L);
        dto.setDiasAfectos(-3);

        assertThatThrownBy(() -> service.crear(dto))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("negativos");
    }

    @Test
    void crear_maternidad_sin_adjunto_lanza_porque_requiereAdjunto_S() {
        LocalDate inicio = LocalDate.of(2026, 5, 1);
        LocalDate fin = DistribucionMensualCalculator.calcularFechaFin(inicio, 98);
        EventoPeriodoDto dto = dtoMaternidadCompleto(inicio, fin, 98, null);

        assertThatThrownBy(() -> service.crear(dto))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("sustento documental");
    }

    @Test
    void crear_maternidad_sin_campos_normativos_lanza() {
        EventoPeriodoDto dto = baseDto(TIPO_MATERNIDAD,
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 8, 6));
        dto.setSustentoLegajoDocId(99L);

        assertThatThrownBy(() -> service.crear(dto))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("98 días");
    }

    @Test
    void crear_solapado_y_tipo_no_permite_lanza() {
        EmpleadoEvento existente = new EmpleadoEvento();
        existente.setId(300L);
        existente.setFechaInicio(LocalDate.of(2026, 5, 10));
        existente.setFechaFin(LocalDate.of(2026, 5, 20));
        when(repository.findSolapados(eq(EMP_ID), any(), any(), isNull()))
                .thenReturn(List.of(existente));

        LocalDate inicio = LocalDate.of(2026, 5, 15);
        LocalDate fin = DistribucionMensualCalculator.calcularFechaFin(inicio, 98);
        EventoPeriodoDto dto = dtoMaternidadCompleto(inicio, fin, 98, null);
        dto.setSustentoLegajoDocId(99L);

        assertThatThrownBy(() -> service.crear(dto))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("solapa");
    }

    @Test
    void cambiar_estado_invalido_lanza() {
        EmpleadoEvento e = new EmpleadoEvento();
        e.setId(200L);
        when(repository.findById(200L)).thenReturn(Optional.of(e));

        assertThatThrownBy(() -> service.cambiarEstado(200L, "FOO"))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("Estado inválido");
    }

    @Test
    void listar_paginado_devuelve_eventos_con_empleado_y_dni() {
        EmpleadoEvento e1 = new EmpleadoEvento();
        e1.setId(1L);
        e1.setEmpleadoId(EMP_ID);
        e1.setTipoEventoId(TIPO_MATERNIDAD);
        e1.setEstado("VALIDADO");
        EmpleadoEvento e2 = new EmpleadoEvento();
        e2.setId(2L);
        e2.setEmpleadoId(99L);
        e2.setTipoEventoId(TIPO_PERMISO);
        e2.setEstado("REGISTRADO");

        Page<EmpleadoEvento> page = new PageImpl<>(List.of(e1, e2));
        when(repository.findBandejaPaginada(isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(page);

        EventoPeriodoPageDto r = service.listarPaginado(null, null, null, 0, 20);

        assertThat(r.getContent()).hasSize(2);
        assertThat(r.getContent().get(0).getEmpleadoNombre()).isEqualTo("EMPLEADO 42");
        assertThat(r.getContent().get(0).getEmpleadoDni()).isNotBlank();
        assertThat(r.getContent().get(1).getTipoEventoCodigo()).isEqualTo("PERMISO_PERSONAL");
    }

    @Test
    void listar_devuelve_eventos_del_empleado() {
        EmpleadoEvento e1 = new EmpleadoEvento();
        e1.setId(1L);
        e1.setEmpleadoId(EMP_ID);
        e1.setTipoEventoId(TIPO_MATERNIDAD);
        e1.setTipoEvento(tipoMaternidad);
        e1.setEstado("VALIDADO");
        EmpleadoEvento e2 = new EmpleadoEvento();
        e2.setId(2L);
        e2.setEmpleadoId(EMP_ID);
        e2.setTipoEventoId(TIPO_PERMISO);
        e2.setTipoEvento(tipoPermiso);
        e2.setEstado("REGISTRADO");

        when(repository.findByEmpleadoIdAndActivoOrderByFechaInicioDesc(EMP_ID, 1))
                .thenReturn(List.of(e1, e2));

        List<EventoPeriodoResponseDto> r = service.listarPorEmpleado(EMP_ID);

        assertThat(r).hasSize(2);
        assertThat(r.get(0).getTipoEventoCodigo()).isEqualTo("MATERNIDAD");
        assertThat(r.get(0).getEmpleadoNombre()).isEqualTo("EMPLEADO 42");
        assertThat(r.get(1).getTipoEventoCodigo()).isEqualTo("PERMISO_PERSONAL");
    }

    // ===================== HELPERS =====================

    private TipoEvento tipo(Long id, String codigo,
            String afectaDias, String generaSubsidio,
            String requiereAdjunto, String permiteSolape) {
        TipoEvento t = new TipoEvento();
        t.setId(id);
        t.setCodigo(codigo);
        t.setNombre(codigo);
        t.setAfectaDiasLaborados(afectaDias);
        t.setAfectaBaseAfp("S");
        t.setAfectaBaseEssalud("S");
        t.setGeneraSubsidio(generaSubsidio);
        t.setRequiereAdjunto(requiereAdjunto);
        t.setPermiteSolape(permiteSolape);
        t.setActivo(1);
        return t;
    }

    private EventoPeriodoDto baseDto(Long tipoId,
            LocalDate inicio, LocalDate fin) {
        EventoPeriodoDto dto = new EventoPeriodoDto();
        dto.setEmpleadoId(EMP_ID);
        dto.setTipoEventoId(tipoId);
        dto.setFechaInicio(inicio);
        dto.setFechaFin(fin);
        return dto;
    }

    private EventoPeriodoDto dtoMaternidadCompleto(
            LocalDate inicio,
            LocalDate fin,
            int duracion,
            String motivoExtension) {
        EventoPeriodoDto dto = baseDto(TIPO_MATERNIDAD, inicio, fin);
        dto.setDuracionLegal(duracion);
        dto.setMotivoExtension(motivoExtension);
        dto.setFechaProbableParto(inicio);
        dto.setDifierePrenatalPostnatal("NO");
        dto.setTipoDocumento("CITT");
        dto.setNroCitt("CITT-TEST-001");
        dto.setFechaEmisionDoc(LocalDate.of(2026, 4, 15));
        dto.setDistribucionMensual(DistribucionMensualCalculator.calcular(inicio, fin));
        return dto;
    }
}
