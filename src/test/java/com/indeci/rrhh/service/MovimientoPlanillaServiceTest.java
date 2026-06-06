package com.indeci.rrhh.service;

import com.indeci.audit.context.AuditoriaContext;
import com.indeci.rrhh.dto.MovimientoPlanillaResponseDto;
import com.indeci.rrhh.dto.ResumenMetaDto;
import com.indeci.rrhh.entity.ConceptoPlanilla;
import com.indeci.rrhh.entity.Empleado;
import com.indeci.rrhh.entity.EmpleadoPlanilla;
import com.indeci.rrhh.entity.MovimientoPlanilla;
import com.indeci.rrhh.entity.MovimientoPlanillaDetalle;
import com.indeci.rrhh.entity.Persona;
import com.indeci.rrhh.entity.RegimenLaboral;
import com.indeci.rrhh.repository.ConceptoPlanillaRepository;
import com.indeci.rrhh.repository.EmpleadoRepository;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.MovimientoPlanillaDetalleRepository;
import com.indeci.rrhh.repository.MovimientoPlanillaRepository;
import com.indeci.rrhh.repository.PersonaRepository;
import com.indeci.rrhh.repository.RegimenLaboralRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * Spec 010 / PANTALLA-05 — Tests del resumen por meta presupuestal.
 *   - agrupa por meta, cuenta PEA y suma totales
 *   - clasifica ESSALUD (06001/06002) y aportes (05001-05004) desde el detalle
 *   - empleado sin META se agrupa bajo "SIN META" (caso borde)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MovimientoPlanillaServiceTest {

    @Mock private MovimientoPlanillaRepository repository;
    @Mock private MovimientoPlanillaDetalleRepository detalleRepository;
    @Mock private EmpleadoPlanillaRepository planillaRepository;
    @Mock private EmpleadoRepository empleadoRepository;
    @Mock private PersonaRepository personaRepository;
    @Mock private RegimenLaboralRepository regimenLaboralRepository;
    @Mock private ConceptoPlanillaRepository conceptoRepository;
    @Mock private AuditoriaContext auditoriaContext;

    @InjectMocks private MovimientoPlanillaService service;

    private static final String PERIODO = "2026-05";

    @BeforeEach
    void setUp() {
        // Catálogo de conceptos: ESSALUD (06001/06002), aportes (05001/05003),
        // remunerativo (00301 — no clasifica ni en ESSALUD ni en aportes).
        when(conceptoRepository.findAll()).thenReturn(List.of(
                concepto(1L, "00301"),
                concepto(2L, "06001"),
                concepto(3L, "06002"),
                concepto(4L, "05001"),
                concepto(5L, "05003")));
    }

    @Test
    void resumenPorMeta_agrupa_por_meta_y_suma_pea_y_totales() {
        when(repository.findByPeriodoAndActivo(PERIODO, 1)).thenReturn(List.of(
                mov(100L, 1L, 3000.0),
                mov(101L, 2L, 4000.0),
                mov(102L, 3L, 2000.0)));

        when(planillaRepository.findFirstByEmpleadoIdAndActivo(1L, 1))
                .thenReturn(Optional.of(empPlanilla(1L, "0001", "CC-A")));
        when(planillaRepository.findFirstByEmpleadoIdAndActivo(2L, 1))
                .thenReturn(Optional.of(empPlanilla(2L, "0001", "CC-A")));
        when(planillaRepository.findFirstByEmpleadoIdAndActivo(3L, 1))
                .thenReturn(Optional.of(empPlanilla(3L, "0002", "CC-B")));

        // emp 1 → ESSALUD 270, ONP 390 ; emp 2 → ESSALUD 360, ONP 400 ; emp 3 → sin detalles
        when(detalleRepository.findByMovimientoPlanillaId(100L)).thenReturn(List.of(
                detalle(2L, 270.0), detalle(4L, 390.0)));
        when(detalleRepository.findByMovimientoPlanillaId(101L)).thenReturn(List.of(
                detalle(2L, 360.0), detalle(4L, 400.0)));
        when(detalleRepository.findByMovimientoPlanillaId(102L)).thenReturn(List.of());

        List<ResumenMetaDto> resumen = service.resumenPorMeta(PERIODO);

        assertThat(resumen).hasSize(2);

        ResumenMetaDto meta1 = resumen.get(0); // ordenado por meta → "0001"
        assertThat(meta1.getMeta()).isEqualTo("0001");
        assertThat(meta1.getCentroCosto()).isEqualTo("CC-A");
        assertThat(meta1.getPea()).isEqualTo(2);
        assertThat(meta1.getIngresos()).isEqualTo(7000.0);
        assertThat(meta1.getEssalud()).isEqualTo(630.0);
        assertThat(meta1.getAportes()).isEqualTo(790.0);
        assertThat(meta1.getTotal()).isEqualTo(7630.0); // 7000 + 630
        assertThat(meta1.getEmpleados()).hasSize(2);

        ResumenMetaDto meta2 = resumen.get(1);
        assertThat(meta2.getMeta()).isEqualTo("0002");
        assertThat(meta2.getPea()).isEqualTo(1);
        assertThat(meta2.getTotal()).isEqualTo(2000.0);
    }

    @Test
    void resumenPorMeta_clasifica_essalud_y_aportes_desde_el_detalle() {
        when(repository.findByPeriodoAndActivo(PERIODO, 1)).thenReturn(List.of(
                mov(100L, 1L, 3000.0)));
        when(planillaRepository.findFirstByEmpleadoIdAndActivo(1L, 1))
                .thenReturn(Optional.of(empPlanilla(1L, "0001", "CC-A")));

        // ESSALUD 06001=270 + 06002=100 ; aportes 05001=390 + 05003=50 ;
        // 00301=3000 remunerativo → NO clasifica.
        when(detalleRepository.findByMovimientoPlanillaId(100L)).thenReturn(List.of(
                detalle(2L, 270.0),
                detalle(3L, 100.0),
                detalle(4L, 390.0),
                detalle(5L, 50.0),
                detalle(1L, 3000.0)));

        ResumenMetaDto meta = service.resumenPorMeta(PERIODO).get(0);

        assertThat(meta.getEssalud()).isEqualTo(370.0);  // 270 + 100
        assertThat(meta.getAportes()).isEqualTo(440.0);  // 390 + 50
        assertThat(meta.getIngresos()).isEqualTo(3000.0);
        assertThat(meta.getTotal()).isEqualTo(3370.0);   // 3000 + 370
    }

    @Test
    void resumenPorMeta_empleado_sin_meta_va_a_grupo_SIN_META() {
        when(repository.findByPeriodoAndActivo(PERIODO, 1)).thenReturn(List.of(
                mov(100L, 1L, 3000.0)));
        when(planillaRepository.findFirstByEmpleadoIdAndActivo(1L, 1))
                .thenReturn(Optional.of(empPlanilla(1L, null, null)));
        when(detalleRepository.findByMovimientoPlanillaId(anyLong()))
                .thenReturn(List.of());

        List<ResumenMetaDto> resumen = service.resumenPorMeta(PERIODO);

        assertThat(resumen).hasSize(1);
        assertThat(resumen.get(0).getMeta()).isEqualTo("SIN META");
        assertThat(resumen.get(0).getPea()).isEqualTo(1);
    }

    @Test
    void listarPeriodo_enriquece_identificacion_empleado_y_regimen() {
        when(repository.findByPeriodoAndActivo(PERIODO, 1)).thenReturn(List.of(
                mov(100L, 1L, 3000.0)));
        when(empleadoRepository.findAllById(List.of(1L))).thenReturn(List.of(
                empleado(1L, 10L)));
        when(personaRepository.findAllById(List.of(10L))).thenReturn(List.of(
                persona(10L, "Ana Isabel Salas Ramos", "44552584")));
        when(planillaRepository.findByEmpleadoIdInAndActivo(List.of(1L), 1)).thenReturn(List.of(
                empPlanillaConRegimen(1L, 7L)));
        when(regimenLaboralRepository.findAllById(List.of(7L))).thenReturn(List.of(
                regimen(7L, "1057", "CAS")));

        MovimientoPlanillaResponseDto dto = service.listarPeriodo(PERIODO).get(0);

        assertThat(dto.getEmpleadoNombre()).isEqualTo("Ana Isabel Salas Ramos");
        assertThat(dto.getEmpleadoDni()).isEqualTo("44552584");
        assertThat(dto.getRegimenLaboralCodigo()).isEqualTo("1057");
        assertThat(dto.getRegimenLaboralNombre()).isEqualTo("CAS");
    }

    // ================= HELPERS =================

    private MovimientoPlanilla mov(Long id, Long empleadoId, Double ingresos) {
        MovimientoPlanilla m = new MovimientoPlanilla();
        m.setId(id);
        m.setEmpleadoId(empleadoId);
        m.setPeriodo(PERIODO);
        m.setTotalIngresos(ingresos);
        m.setActivo(1);
        return m;
    }

    private EmpleadoPlanilla empPlanilla(Long empleadoId, String meta, String centroCosto) {
        EmpleadoPlanilla e = new EmpleadoPlanilla();
        e.setEmpleadoId(empleadoId);
        e.setMeta(meta);
        e.setCentroCosto(centroCosto);
        e.setActivo(1);
        return e;
    }

    private EmpleadoPlanilla empPlanillaConRegimen(Long empleadoId, Long regimenLaboralId) {
        EmpleadoPlanilla e = empPlanilla(empleadoId, "0001", "CC-A");
        e.setId(50L);
        e.setRegimenLaboralId(regimenLaboralId);
        return e;
    }

    private Empleado empleado(Long id, Long personaId) {
        Empleado e = new Empleado();
        e.setId(id);
        e.setPersonaId(personaId);
        return e;
    }

    private Persona persona(Long id, String nombreCompleto, String dni) {
        Persona p = new Persona();
        p.setId(id);
        p.setNombreCompleto(nombreCompleto);
        p.setDni(dni);
        return p;
    }

    private RegimenLaboral regimen(Long id, String codigo, String nombre) {
        RegimenLaboral r = new RegimenLaboral();
        r.setId(id);
        r.setCodigo(codigo);
        r.setNombre(nombre);
        return r;
    }

    private MovimientoPlanillaDetalle detalle(Long conceptoId, Double monto) {
        MovimientoPlanillaDetalle d = new MovimientoPlanillaDetalle();
        d.setConceptoPlanillaId(conceptoId);
        d.setMonto(monto);
        return d;
    }

    private ConceptoPlanilla concepto(Long id, String codigoMef) {
        ConceptoPlanilla c = new ConceptoPlanilla();
        c.setId(id);
        c.setCodigoMef(codigoMef);
        return c;
    }
}
