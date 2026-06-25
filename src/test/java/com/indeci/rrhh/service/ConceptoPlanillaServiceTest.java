package com.indeci.rrhh.service;

import com.indeci.audit.context.AuditoriaContext;
import com.indeci.audit.repository.AuditoriaRepository;
import com.indeci.exception.ConceptoEnPlanillaCerradaException;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.ConceptoPlanillaDto;
import com.indeci.rrhh.dto.ConceptoPlanillaResponseDto;
import com.indeci.rrhh.entity.ConceptoPlanilla;
import com.indeci.rrhh.entity.ConceptoTipoInterno;
import com.indeci.rrhh.entity.ConceptoPlanillaTipo;
import com.indeci.rrhh.repository.ConceptoPlanillaRepository;
import com.indeci.rrhh.repository.ConceptoPlanillaTipoRepository;
import com.indeci.rrhh.repository.ConceptoRtpsRepository;
import com.indeci.rrhh.repository.ConceptoTipoInternoRepository;
import com.indeci.rrhh.repository.MovimientoPlanillaDetalleRepository;
import com.indeci.rrhh.repository.PlanillaTipoRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SPEC_CONCEPTOS_PLANILLA P1 (§10.4) — ciclo de vida del concepto y guard de
 * planilla cerrada.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConceptoPlanillaServiceTest {

    @Mock private ConceptoPlanillaRepository repository;
    @Mock private ConceptoRtpsRepository rtpsRepository;
    @Mock private ConceptoTipoInternoRepository tipoInternoRepository;
    @Mock private MovimientoPlanillaDetalleRepository movimientoDetalleRepository;
    @Mock private ConceptoPlanillaTipoRepository conceptoPlanillaTipoRepository;
    @Mock private PlanillaTipoRepository planillaTipoRepository;
    @Mock private AuditoriaContext auditoriaContext;
    @Mock private AuditoriaRepository auditoriaRepository;

    @InjectMocks private ConceptoPlanillaService service;

    private static final Long ID = 7L;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        // §15 / Fase A — por defecto todos los códigos de tipo de planilla existen
        // y repository.save devuelve la entidad con un id (necesario para asociar M:N).
        when(planillaTipoRepository.existsById(any())).thenReturn(true);
        when(conceptoPlanillaTipoRepository.findByConceptoPlanillaId(any()))
                .thenReturn(List.of());
        when(repository.save(any(ConceptoPlanilla.class)))
                .thenAnswer(inv -> {
                    ConceptoPlanilla c = inv.getArgument(0);
                    if (c.getId() == null) {
                        c.setId(ID);
                    }
                    return c;
                });
    }

    /** §15 / Fase A — DTO mínimo válido (con ≥1 planilla asociada). */
    private static List<String> unTipoPlanilla() {
        return List.of("CAS");
    }

    // ---- §10.4.2 — estado por defecto BORRADOR al crear ----
    @Test
    void guardar_fuerza_estado_BORRADOR_y_activo_cero() {
        ConceptoPlanillaDto dto = new ConceptoPlanillaDto();
        dto.setCodigo("0703X");
        dto.setNombre("DESC. AUTORIZADO");
        dto.setTipoConcepto("DESCUENTO");
        dto.setRtpsCodigo("0703");
        dto.setPlanillaTipos(unTipoPlanilla());

        service.guardar(dto);

        ArgumentCaptor<ConceptoPlanilla> captor = ArgumentCaptor.forClass(ConceptoPlanilla.class);
        verify(repository).save(captor.capture());
        ConceptoPlanilla saved = captor.getValue();
        assertThat(saved.getEstado()).isEqualTo("BORRADOR");
        assertThat(saved.getActivo()).isEqualTo(0);
        // Persiste TODOS los campos (antes solo 4).
        assertThat(saved.getTipoConcepto()).isEqualTo("DESCUENTO");
        assertThat(saved.getRtpsCodigo()).isEqualTo("0703");
    }

    // ================================================================
    // SPEC_CONCEPTOS_PLANILLA §13 — código auto + derivación TIPO_CONCEPTO
    // ================================================================

    // ---- guardar sin código → genera CONC-#### no nulo ----
    @Test
    void guardar_sin_codigo_genera_correlativo_CONC() {
        when(repository.nextCodigoCorrelativo()).thenReturn(7L);

        ConceptoPlanillaDto dto = new ConceptoPlanillaDto();
        dto.setNombre("CONCEPTO NUEVO");
        dto.setPlanillaTipos(unTipoPlanilla());
        // sin código

        service.guardar(dto);

        ArgumentCaptor<ConceptoPlanilla> captor = ArgumentCaptor.forClass(ConceptoPlanilla.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getCodigo()).isEqualTo("CONC-0007");
    }

    @Test
    void guardar_con_codigo_explicito_lo_respeta() {
        ConceptoPlanillaDto dto = new ConceptoPlanillaDto();
        dto.setCodigo("IR4TA_CAS");
        dto.setNombre("CONCEPTO TÉCNICO");
        dto.setPlanillaTipos(unTipoPlanilla());

        service.guardar(dto);

        ArgumentCaptor<ConceptoPlanilla> captor = ArgumentCaptor.forClass(ConceptoPlanilla.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getCodigo()).isEqualTo("IR4TA_CAS");
        verify(repository, never()).nextCodigoCorrelativo();
    }

    // ---- derivación: INCENTIVOS → NO_REMUNERATIVO ----
    @Test
    void guardar_deriva_tipoConcepto_NO_REMUNERATIVO_para_INCENTIVOS() {
        when(repository.nextCodigoCorrelativo()).thenReturn(1L);
        when(tipoInternoRepository.findById("INCENTIVOS"))
                .thenReturn(Optional.of(tipoInterno("INCENTIVOS", "NO_REMUNERATIVO")));

        ConceptoPlanillaDto dto = new ConceptoPlanillaDto();
        dto.setNombre("BONO INCENTIVO");
        dto.setTipoConceptoInterno("INCENTIVOS");
        dto.setPlanillaTipos(unTipoPlanilla());
        // tipoConcepto NO viene; se deriva.

        service.guardar(dto);

        ArgumentCaptor<ConceptoPlanilla> captor = ArgumentCaptor.forClass(ConceptoPlanilla.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getTipoConceptoInterno()).isEqualTo("INCENTIVOS");
        assertThat(captor.getValue().getTipoConcepto()).isEqualTo("NO_REMUNERATIVO");
    }

    // ---- derivación: DESC_FIJO → DESCUENTO (motor); tipo legacy se mantiene ----
    @Test
    void guardar_deriva_tipoConcepto_DESCUENTO_para_DESC_FIJO() {
        when(repository.nextCodigoCorrelativo()).thenReturn(2L);
        when(tipoInternoRepository.findById("DESC_FIJO"))
                .thenReturn(Optional.of(tipoInterno("DESC_FIJO", "DESCUENTO")));

        ConceptoPlanillaDto dto = new ConceptoPlanillaDto();
        dto.setNombre("DESCUENTO FIJO");
        dto.setTipoConceptoInterno("DESC_FIJO");
        dto.setTipo("DESCUENTO"); // legacy free-form (compat Spec 009)
        dto.setPlanillaTipos(unTipoPlanilla());

        service.guardar(dto);

        ArgumentCaptor<ConceptoPlanilla> captor = ArgumentCaptor.forClass(ConceptoPlanilla.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getTipoConcepto()).isEqualTo("DESCUENTO");
        assertThat(captor.getValue().getTipo()).isEqualTo("DESCUENTO");
    }

    // ================================================================
    // SPEC_CONCEPTOS_PLANILLA §14 / P4 — modo de cálculo (metadata)
    // ================================================================

    // ---- guardar sin modoCalculo → persiste 'RESULTADO_MOTOR' (default) ----
    @Test
    void guardar_sin_modoCalculo_persiste_RESULTADO_MOTOR() {
        when(repository.nextCodigoCorrelativo()).thenReturn(5L);

        ConceptoPlanillaDto dto = new ConceptoPlanillaDto();
        dto.setNombre("CONCEPTO SIN MODO");
        dto.setPlanillaTipos(unTipoPlanilla());
        // sin modoCalculo

        service.guardar(dto);

        ArgumentCaptor<ConceptoPlanilla> captor = ArgumentCaptor.forClass(ConceptoPlanilla.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getModoCalculo()).isEqualTo("RESULTADO_MOTOR");
    }

    // ---- guardar con modoCalculo='PORCENTAJE' → se persiste y aparece en el response ----
    @Test
    void guardar_con_modoCalculo_PORCENTAJE_lo_persiste_y_expone_en_response() {
        when(repository.nextCodigoCorrelativo()).thenReturn(6L);
        when(repository.save(any(ConceptoPlanilla.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ConceptoPlanillaDto dto = new ConceptoPlanillaDto();
        dto.setNombre("BONO PORCENTUAL");
        dto.setModoCalculo("PORCENTAJE");
        dto.setPlanillaTipos(unTipoPlanilla());

        service.guardar(dto);

        ArgumentCaptor<ConceptoPlanilla> captor = ArgumentCaptor.forClass(ConceptoPlanilla.class);
        verify(repository).save(captor.capture());
        ConceptoPlanilla saved = captor.getValue();
        assertThat(saved.getModoCalculo()).isEqualTo("PORCENTAJE");

        // El response refleja el valor persistido (toResponse via listar()).
        when(repository.findByActivo(1)).thenReturn(List.of(saved));
        List<ConceptoPlanillaResponseDto> listado = service.listar();
        assertThat(listado).hasSize(1);
        assertThat(listado.get(0).getModoCalculo()).isEqualTo("PORCENTAJE");
    }

    private ConceptoTipoInterno tipoInterno(String codigo, String clasificacionMotor) {
        ConceptoTipoInterno t = new ConceptoTipoInterno();
        t.setCodigo(codigo);
        t.setClasificacionMotor(clasificacionMotor);
        t.setActivo(1);
        return t;
    }

    // ---- §10.4.3 — transición activar valida el estado origen ----
    @Test
    void activar_desde_EN_REVISION_pasa_a_ACTIVO_y_sincroniza_activo() {
        ConceptoPlanilla e = concepto("EN_REVISION");
        when(repository.findById(ID)).thenReturn(Optional.of(e));
        when(movimientoDetalleRepository.countEnPlanillaCerrada(ID)).thenReturn(0L);

        service.activar(ID);

        assertThat(e.getEstado()).isEqualTo("ACTIVO");
        assertThat(e.getActivo()).isEqualTo(1); // el motor (que lee ACTIVO) ya lo verá
        verify(repository).save(e);
    }

    @Test
    void activar_desde_BORRADOR_es_transicion_invalida() {
        ConceptoPlanilla e = concepto("BORRADOR");
        when(repository.findById(ID)).thenReturn(Optional.of(e));
        when(movimientoDetalleRepository.countEnPlanillaCerrada(ID)).thenReturn(0L);

        assertThatThrownBy(() -> service.activar(ID))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("Transición no permitida");
        verify(repository, never()).save(any());
    }

    @Test
    void cerrar_desde_ACTIVO_pone_activo_cero() {
        ConceptoPlanilla e = concepto("ACTIVO");
        when(repository.findById(ID)).thenReturn(Optional.of(e));
        when(movimientoDetalleRepository.countEnPlanillaCerrada(ID)).thenReturn(0L);

        service.cerrar(ID);

        assertThat(e.getEstado()).isEqualTo("CERRADO");
        assertThat(e.getActivo()).isEqualTo(0);
    }

    // ---- §10.4.4 — actualizar bloqueado (409) si usado en planilla cerrada ----
    @Test
    void actualizar_bloqueado_si_concepto_usado_en_planilla_cerrada() {
        ConceptoPlanilla e = concepto("ACTIVO");
        when(repository.findById(ID)).thenReturn(Optional.of(e));
        when(movimientoDetalleRepository.countEnPlanillaCerrada(ID)).thenReturn(3L);

        ConceptoPlanillaDto dto = new ConceptoPlanillaDto();
        dto.setNombre("INTENTO EDICION");

        assertThatThrownBy(() -> service.actualizar(ID, dto))
                .isInstanceOf(ConceptoEnPlanillaCerradaException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void actualizar_permitido_si_no_usado_en_planilla_cerrada() {
        ConceptoPlanilla e = concepto("ACTIVO");
        when(repository.findById(ID)).thenReturn(Optional.of(e));
        when(movimientoDetalleRepository.countEnPlanillaCerrada(ID)).thenReturn(0L);

        ConceptoPlanillaDto dto = new ConceptoPlanillaDto();
        dto.setNombre("NUEVO NOMBRE");
        dto.setCodigoMef("00501");
        dto.setPlanillaTipos(unTipoPlanilla());

        service.actualizar(ID, dto);

        assertThat(e.getNombre()).isEqualTo("NUEVO NOMBRE");
        assertThat(e.getCodigoMef()).isEqualTo("00501");
        verify(repository).save(e);
    }

    // ================================================================
    // SPEC_CONCEPTOS_PLANILLA §12 / P3 — versionado + supersede + catálogo
    // ================================================================

    // ---- crearNuevaVersion: nueva fila VERSION+1, predecesor con FECHA_VIG_FIN ----
    @Test
    void crearNuevaVersion_clona_con_version_mas_uno_y_cierra_predecesor() {
        ConceptoPlanilla v1 = versionada("0703X", 1, "ACTIVO",
                LocalDate.of(2026, 1, 1), null);
        v1.setNombre("DESC. ORIGINAL");
        v1.setTipoConcepto("DESCUENTO");

        when(repository.findById(ID)).thenReturn(Optional.of(v1));
        when(repository.findByCodigoOrderByVersionDesc("0703X"))
                .thenReturn(List.of(v1));
        // Devuelve la entidad nueva con un id asignado al guardar.
        when(repository.save(any(ConceptoPlanilla.class)))
                .thenAnswer(inv -> {
                    ConceptoPlanilla c = inv.getArgument(0);
                    if (c.getId() == null) {
                        c.setId(99L);
                    }
                    return c;
                });

        LocalDate nuevaVig = LocalDate.of(2026, 7, 1);
        Long nuevoId = service.crearNuevaVersion(ID, nuevaVig);

        assertThat(nuevoId).isEqualTo(99L);
        // Predecesor cerrado el día anterior.
        assertThat(v1.getFechaVigFin()).isEqualTo(LocalDate.of(2026, 6, 30));

        ArgumentCaptor<ConceptoPlanilla> captor = ArgumentCaptor.forClass(ConceptoPlanilla.class);
        verify(repository, org.mockito.Mockito.atLeast(2)).save(captor.capture());
        ConceptoPlanilla nueva = captor.getAllValues().stream()
                .filter(c -> nuevaVig.equals(c.getFechaVigIni()))
                .findFirst().orElseThrow();
        assertThat(nueva.getVersion()).isEqualTo(2);
        assertThat(nueva.getEstado()).isEqualTo("BORRADOR");
        assertThat(nueva.getActivo()).isEqualTo(0);
        assertThat(nueva.getFechaVigFin()).isNull();
        // Clona la configuración.
        assertThat(nueva.getNombre()).isEqualTo("DESC. ORIGINAL");
        assertThat(nueva.getTipoConcepto()).isEqualTo("DESCUENTO");
        assertThat(nueva.getCodigo()).isEqualTo("0703X");
    }

    // ---- crearNuevaVersion: rechaza solapamiento de vigencias ----
    @Test
    void crearNuevaVersion_rechaza_solapamiento() {
        // La nueva vigencia no puede iniciar en/antes del inicio de v1 (2026-01-01):
        // insertar "antes o sobre" una versión existente es solapamiento.
        ConceptoPlanilla v1 = versionada("0703X", 1, "ACTIVO",
                LocalDate.of(2026, 1, 1), null);
        when(repository.findById(ID)).thenReturn(Optional.of(v1));
        when(repository.findByCodigoOrderByVersionDesc("0703X"))
                .thenReturn(List.of(v1));

        assertThatThrownBy(() ->
                service.crearNuevaVersion(ID, LocalDate.of(2026, 1, 1)))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("Solapamiento");
        verify(repository, never()).save(any());
    }

    // ---- activar supersede: solo 1 ACTIVO por código ----
    @Test
    void activar_supersede_cierra_la_version_activa_previa() {
        ConceptoPlanilla v1 = versionada("0703X", 1, "ACTIVO",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30));
        v1.setId(10L);
        ConceptoPlanilla v2 = versionada("0703X", 2, "EN_REVISION",
                LocalDate.of(2026, 7, 1), null);
        v2.setId(11L);

        when(repository.findById(11L)).thenReturn(Optional.of(v2));
        when(repository.findByCodigoOrderByVersionDesc("0703X"))
                .thenReturn(List.of(v2, v1));
        when(movimientoDetalleRepository.countEnPlanillaCerrada(11L)).thenReturn(0L);

        service.activar(11L);

        // Predecesor superseded.
        assertThat(v1.getEstado()).isEqualTo("CERRADO");
        assertThat(v1.getActivo()).isEqualTo(0);
        // Nueva versión activa.
        assertThat(v2.getEstado()).isEqualTo("ACTIVO");
        assertThat(v2.getActivo()).isEqualTo(1);
    }

    // ---- listarCatalogo: solo la versión vigente por código ----
    @Test
    void listarCatalogo_devuelve_solo_la_version_vigente_por_codigo() {
        ConceptoPlanilla v1 = versionada("0703X", 1, "CERRADO",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30));
        v1.setId(10L);
        ConceptoPlanilla v2 = versionada("0703X", 2, "ACTIVO",
                LocalDate.of(2026, 7, 1), null);
        v2.setId(11L);
        ConceptoPlanilla otro = versionada("0104", 1, "ACTIVO",
                LocalDate.of(2026, 1, 1), null);
        otro.setId(20L);

        when(repository.findByEstadoNot("ANULADO"))
                .thenReturn(List.of(v1, v2, otro));

        List<ConceptoPlanillaResponseDto> result = service.listarCatalogo();

        // 0703X colapsa a su versión vigente (v2); 0104 aparece una vez.
        assertThat(result).hasSize(2);
        assertThat(result).extracting(ConceptoPlanillaResponseDto::getId)
                .containsExactlyInAnyOrder(11L, 20L);
        ConceptoPlanillaResponseDto vigente = result.stream()
                .filter(r -> "0703X".equals(r.getCodigo()))
                .findFirst().orElseThrow();
        assertThat(vigente.getVersion()).isEqualTo(2);
        assertThat(vigente.getEstado()).isEqualTo("ACTIVO");
    }

    // ================================================================
    // SPEC_CONCEPTOS_PLANILLA §15 / Fase A — asociación M:N a tipos de planilla
    // ================================================================

    // ---- guardar con 2 planillas → persiste 2 asociaciones; el response las devuelve ----
    @Test
    void guardar_con_dos_planillaTipos_persiste_dos_asociaciones() {
        when(repository.nextCodigoCorrelativo()).thenReturn(8L);

        ConceptoPlanillaDto dto = new ConceptoPlanillaDto();
        dto.setNombre("CONCEPTO MULTI-PLANILLA");
        dto.setPlanillaTipos(List.of("CAS", "CAS_ADIC"));

        service.guardar(dto);

        // Reemplaza (borra previas) e inserta una fila por código.
        verify(conceptoPlanillaTipoRepository).deleteByConceptoPlanillaId(ID);
        ArgumentCaptor<ConceptoPlanillaTipo> captor =
                ArgumentCaptor.forClass(ConceptoPlanillaTipo.class);
        verify(conceptoPlanillaTipoRepository, org.mockito.Mockito.times(2))
                .save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(ConceptoPlanillaTipo::getPlanillaTipoCodigo)
                .containsExactlyInAnyOrder("CAS", "CAS_ADIC");
        assertThat(captor.getAllValues())
                .allMatch(a -> ID.equals(a.getConceptoPlanillaId()));

        // El response del concepto devuelve los códigos asociados.
        when(conceptoPlanillaTipoRepository.findByConceptoPlanillaId(ID))
                .thenReturn(List.of(asociacion("CAS"), asociacion("CAS_ADIC")));
        ConceptoPlanilla saved = concepto("BORRADOR");
        when(repository.findByActivo(1)).thenReturn(List.of(saved));
        List<ConceptoPlanillaResponseDto> listado = service.listar();
        assertThat(listado.get(0).getPlanillaTipos())
                .containsExactlyInAnyOrder("CAS", "CAS_ADIC");
    }

    // ---- guardar con planillaTipos vacío → NegocioException, no persiste ----
    @Test
    void guardar_sin_planillaTipos_lanza_NegocioException() {
        ConceptoPlanillaDto dto = new ConceptoPlanillaDto();
        dto.setNombre("CONCEPTO SIN PLANILLA");
        dto.setPlanillaTipos(List.of());

        assertThatThrownBy(() -> service.guardar(dto))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("al menos una planilla");
        verify(repository, never()).save(any());
        verify(conceptoPlanillaTipoRepository, never()).save(any());
    }

    // ---- crearNuevaVersion copia las asociaciones de la versión origen ----
    @Test
    void crearNuevaVersion_copia_asociaciones_de_planillaTipos() {
        ConceptoPlanilla v1 = versionada("0703X", 1, "ACTIVO",
                LocalDate.of(2026, 1, 1), null);
        when(repository.findById(ID)).thenReturn(Optional.of(v1));
        when(repository.findByCodigoOrderByVersionDesc("0703X")).thenReturn(List.of(v1));
        when(repository.save(any(ConceptoPlanilla.class)))
                .thenAnswer(inv -> {
                    ConceptoPlanilla c = inv.getArgument(0);
                    if (c.getId() == null) {
                        c.setId(99L);
                    }
                    return c;
                });
        // La versión origen tiene 2 tipos de planilla asociados.
        when(conceptoPlanillaTipoRepository.findByConceptoPlanillaId(ID))
                .thenReturn(List.of(asociacion("CAS"), asociacion("CAS_TEMP")));

        service.crearNuevaVersion(ID, LocalDate.of(2026, 7, 1));

        // Se insertan las mismas asociaciones bajo la nueva versión (id 99L).
        ArgumentCaptor<ConceptoPlanillaTipo> captor =
                ArgumentCaptor.forClass(ConceptoPlanillaTipo.class);
        verify(conceptoPlanillaTipoRepository, org.mockito.Mockito.times(2))
                .save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(ConceptoPlanillaTipo::getPlanillaTipoCodigo)
                .containsExactlyInAnyOrder("CAS", "CAS_TEMP");
        assertThat(captor.getAllValues())
                .allMatch(a -> Long.valueOf(99L).equals(a.getConceptoPlanillaId()));
    }

    private ConceptoPlanillaTipo asociacion(String codigo) {
        ConceptoPlanillaTipo a = new ConceptoPlanillaTipo();
        a.setConceptoPlanillaId(ID);
        a.setPlanillaTipoCodigo(codigo);
        return a;
    }

    private ConceptoPlanilla concepto(String estado) {
        ConceptoPlanilla e = new ConceptoPlanilla();
        e.setId(ID);
        e.setEstado(estado);
        e.setActivo("ACTIVO".equals(estado) ? 1 : 0);
        return e;
    }

    private ConceptoPlanilla versionada(String codigo, int version, String estado,
                                        LocalDate vigIni, LocalDate vigFin) {
        ConceptoPlanilla e = new ConceptoPlanilla();
        e.setId(ID);
        e.setCodigo(codigo);
        e.setVersion(version);
        e.setEstado(estado);
        e.setActivo("ACTIVO".equals(estado) ? 1 : 0);
        e.setFechaVigIni(vigIni);
        e.setFechaVigFin(vigFin);
        return e;
    }
}
