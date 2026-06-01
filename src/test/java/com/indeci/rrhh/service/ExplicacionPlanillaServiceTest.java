package com.indeci.rrhh.service;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.ExplicacionLineaDto;
import com.indeci.rrhh.dto.ExplicacionPlanillaDto;
import com.indeci.rrhh.entity.*;
import com.indeci.rrhh.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * F3.1a — Tests del service que arma la respuesta para Ficha 360.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExplicacionPlanillaServiceTest {

    @Mock private MovimientoPlanillaRepository movimientoRepository;
    @Mock private MovimientoPlanillaDetalleRepository detalleRepository;
    @Mock private ConceptoPlanillaRepository conceptoRepository;
    @Mock private EmpleadoRepository empleadoRepository;
    @Mock private PersonaRepository personaRepository;
    @Mock private EmpleadoPlanillaRepository planillaRepository;
    @Mock private EmpleadoBancoRepository bancoRepository;
    @Mock private BankRepository bankRepository;
    @Mock private RegimenLaboralRepository regimenRepository;
    @Mock private ConciliacionAirhspRepository conciliacionRepository;

    @InjectMocks private ExplicacionPlanillaService service;

    private static final Long EMP_ID = 42L;
    private static final String PERIODO = "2026-05";

    // =================== Validaciones de entrada ===================

    @Test
    void explicar_sin_empleadoId_lanza() {
        assertThatThrownBy(() -> service.explicar(null, PERIODO))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("empleadoId");
    }

    @Test
    void explicar_sin_periodo_lanza() {
        assertThatThrownBy(() -> service.explicar(EMP_ID, null))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("periodo");
        assertThatThrownBy(() -> service.explicar(EMP_ID, ""))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("periodo");
    }

    // =================== Sin movimiento → aplica=false ===================

    @Test
    void sin_movimiento_devuelve_noAplica_y_lineas_vacias() {
        when(movimientoRepository.findByEmpleadoIdAndPeriodoAndActivo(EMP_ID, PERIODO, 1))
                .thenReturn(Optional.empty());

        ExplicacionPlanillaDto r = service.explicar(EMP_ID, PERIODO);

        assertThat(r.aplica()).isFalse();
        assertThat(r.empleadoId()).isEqualTo(EMP_ID);
        assertThat(r.periodo()).isEqualTo(PERIODO);
        assertThat(r.cabecera()).isNull();
        assertThat(r.totales()).isNull();
        assertThat(r.lineas()).isEmpty();
    }

    // =================== Caso feliz ===================

    @Test
    void explica_movimiento_con_todos_los_datos_de_cabecera() {
        stubMovimientoFeliz();

        // Persona
        Empleado emp = new Empleado();
        emp.setPersonaId(7L);
        when(empleadoRepository.findById(EMP_ID)).thenReturn(Optional.of(emp));
        Persona p = new Persona();
        p.setNombreCompleto("Juan Pérez García");
        p.setDni("87654321");
        when(personaRepository.findById(7L)).thenReturn(Optional.of(p));

        // EmpleadoPlanilla + régimen
        EmpleadoPlanilla pl = new EmpleadoPlanilla();
        pl.setRegimenLaboralId(2L);
        pl.setMeta("0074");
        when(planillaRepository.findFirstByEmpleadoIdAndActivo(EMP_ID, 1))
                .thenReturn(Optional.of(pl));
        RegimenLaboral reg = new RegimenLaboral();
        reg.setCodigo("CAS");
        reg.setNombre("CAS - D.Leg. 1057");
        when(regimenRepository.findById(2L)).thenReturn(Optional.of(reg));

        // Banco
        EmpleadoBanco eb = new EmpleadoBanco();
        eb.setBankId(11L);
        eb.setNumeroCuenta("191-1234567");
        eb.setCci("00219100123456789012");
        when(bancoRepository.findByEmpleadoIdAndEsCuentaPlanillaAndActivo(EMP_ID, 1, 1))
                .thenReturn(Optional.of(eb));
        Bank bk = new Bank();
        bk.setName("BCP");
        when(bankRepository.findById(11L)).thenReturn(Optional.of(bk));

        ExplicacionPlanillaDto r = service.explicar(EMP_ID, PERIODO);

        assertThat(r.aplica()).isTrue();
        assertThat(r.cabecera().nombreCompleto()).isEqualTo("Juan Pérez García");
        assertThat(r.cabecera().dni()).isEqualTo("87654321");
        assertThat(r.cabecera().regimenLaboralCodigo()).isEqualTo("CAS");
        assertThat(r.cabecera().regimenLaboralNombre()).isEqualTo("CAS - D.Leg. 1057");
        assertThat(r.cabecera().meta()).isEqualTo("0074");
        assertThat(r.cabecera().banco()).isEqualTo("BCP");
        assertThat(r.cabecera().numeroCuenta()).isEqualTo("191-1234567");
        assertThat(r.cabecera().cci()).isEqualTo("00219100123456789012");
    }

    @Test
    void totales_separa_aporte_trabajador_y_aporte_empleador() {
        MovimientoPlanilla mov = movimiento(100L, 5500.0, 2435.0, 3065.0, "BIEN");
        when(movimientoRepository.findByEmpleadoIdAndPeriodoAndActivo(EMP_ID, PERIODO, 1))
                .thenReturn(Optional.of(mov));

        // Detalle: 1 ingreso, 1 aporte trabajador, 1 aporte empleador.
        MovimientoPlanillaDetalle dIngreso  = detalle(1L, 100L, 5500.0, 901L); // sueldo
        MovimientoPlanillaDetalle dApTrab   = detalle(2L, 100L,  550.0, 902L); // aporte AFP
        MovimientoPlanillaDetalle dApEmp    = detalle(3L, 100L,  495.0, 903L); // EsSalud

        when(detalleRepository.findByMovimientoPlanillaId(100L))
                .thenReturn(List.of(dIngreso, dApTrab, dApEmp));

        when(conceptoRepository.findById(901L)).thenReturn(Optional.of(
                concepto("00301", "Sueldo CAS", "REMUNERATIVO")));
        when(conceptoRepository.findById(902L)).thenReturn(Optional.of(
                concepto("05002", "Aporte AFP 10%", "APORTE_TRABAJADOR")));
        when(conceptoRepository.findById(903L)).thenReturn(Optional.of(
                concepto("06001", "ESSALUD 9%", "APORTE_EMPLEADOR")));

        ExplicacionPlanillaDto r = service.explicar(EMP_ID, PERIODO);

        assertThat(r.totales().aporteTrabajador()).isEqualByComparingTo("550.00");
        assertThat(r.totales().aporteEmpleador()).isEqualByComparingTo("495.00");
        assertThat(r.totales().totalIngresos()).isEqualByComparingTo("5500.00");
        assertThat(r.totales().totalDescuentos()).isEqualByComparingTo("2435.00");
        assertThat(r.totales().netoPagar()).isEqualByComparingTo("3065.00");
        assertThat(r.totales().estadoNeto()).isEqualTo("BIEN");
    }

    @Test
    void totales_incluye_conciliacion_AIRHSP_cuando_existe() {
        MovimientoPlanilla mov = movimiento(100L, 5500.0, 0.0, 5500.0, "BIEN");
        when(movimientoRepository.findByEmpleadoIdAndPeriodoAndActivo(EMP_ID, PERIODO, 1))
                .thenReturn(Optional.of(mov));
        when(detalleRepository.findByMovimientoPlanillaId(100L)).thenReturn(List.of());

        ConciliacionAirhsp con = new ConciliacionAirhsp();
        con.setMontoSistema(5500.0);
        con.setMontoAirhsp(5500.0);
        // Diferencia es columna VIRTUAL en BD pero podemos mockearla via setter Java.
        // En real, Hibernate la calcula al leer. Aquí simulamos el resultado.
        con.setEstado("CONCILIADO");
        when(conciliacionRepository.findByMovimientoPlanillaIdAndEmpleadoId(100L, EMP_ID))
                .thenReturn(Optional.of(con));

        ExplicacionPlanillaDto r = service.explicar(EMP_ID, PERIODO);

        assertThat(r.totales().montoSistemaAirhsp()).isEqualByComparingTo("5500.00");
        assertThat(r.totales().montoAirhsp()).isEqualByComparingTo("5500.00");
        assertThat(r.totales().estadoAirhsp()).isEqualTo("CONCILIADO");
    }

    @Test
    void totales_sin_conciliacion_devuelve_montos_null() {
        MovimientoPlanilla mov = movimiento(100L, 5500.0, 0.0, 5500.0, "BIEN");
        when(movimientoRepository.findByEmpleadoIdAndPeriodoAndActivo(EMP_ID, PERIODO, 1))
                .thenReturn(Optional.of(mov));
        when(detalleRepository.findByMovimientoPlanillaId(100L)).thenReturn(List.of());
        when(conciliacionRepository.findByMovimientoPlanillaIdAndEmpleadoId(100L, EMP_ID))
                .thenReturn(Optional.empty());

        ExplicacionPlanillaDto r = service.explicar(EMP_ID, PERIODO);

        assertThat(r.totales().estadoAirhsp()).isNull();
        assertThat(r.totales().montoSistemaAirhsp()).isNull();
    }

    // =================== Líneas ===================

    @Test
    void lineas_clasifica_grupos_por_tipoConcepto() {
        MovimientoPlanilla mov = movimiento(100L, 5500.0, 550.0, 4950.0, "BIEN");
        when(movimientoRepository.findByEmpleadoIdAndPeriodoAndActivo(EMP_ID, PERIODO, 1))
                .thenReturn(Optional.of(mov));

        when(detalleRepository.findByMovimientoPlanillaId(100L))
                .thenReturn(List.of(
                        detalle(1L, 100L, 5500.0, 901L),
                        detalle(2L, 100L,  550.0, 902L),
                        detalle(3L, 100L,  495.0, 903L),
                        detalle(4L, 100L,   42.0, 904L)));

        when(conceptoRepository.findById(901L)).thenReturn(Optional.of(
                concepto("00301", "Sueldo CAS", "REMUNERATIVO")));
        when(conceptoRepository.findById(902L)).thenReturn(Optional.of(
                concepto("05002", "Aporte AFP 10%", "APORTE_TRABAJADOR")));
        when(conceptoRepository.findById(903L)).thenReturn(Optional.of(
                concepto("06001", "ESSALUD 9%", "APORTE_EMPLEADOR")));
        when(conceptoRepository.findById(904L)).thenReturn(Optional.of(
                concepto("05401", "Tardanza", "DESCUENTO")));

        ExplicacionPlanillaDto r = service.explicar(EMP_ID, PERIODO);

        assertThat(r.lineas()).hasSize(4);
        var byCodigo = r.lineas().stream()
                .collect(java.util.stream.Collectors.toMap(
                        ExplicacionLineaDto::codigoMef, l -> l));
        assertThat(byCodigo.get("00301").grupo()).isEqualTo("INGRESO");
        assertThat(byCodigo.get("05002").grupo()).isEqualTo("APORTE_TRABAJADOR");
        assertThat(byCodigo.get("06001").grupo()).isEqualTo("APORTE_EMPLEADOR");
        assertThat(byCodigo.get("05401").grupo()).isEqualTo("DESCUENTO");
    }

    @Test
    void linea_marca_fuente_CONCEPTO_AUTO_si_codigo_mef_es_autocalculado() {
        MovimientoPlanilla mov = movimiento(100L, 5500.0, 0.0, 5500.0, "BIEN");
        when(movimientoRepository.findByEmpleadoIdAndPeriodoAndActivo(EMP_ID, PERIODO, 1))
                .thenReturn(Optional.of(mov));

        MovimientoPlanillaDetalle dEssalud = detalle(1L, 100L, 495.0, 903L);
        MovimientoPlanillaDetalle dManual  = detalle(2L, 100L, 100.0, 904L);
        when(detalleRepository.findByMovimientoPlanillaId(100L))
                .thenReturn(List.of(dEssalud, dManual));

        when(conceptoRepository.findById(903L)).thenReturn(Optional.of(
                concepto("06001", "ESSALUD 9%", "APORTE_EMPLEADOR"))); // autocalculado
        when(conceptoRepository.findById(904L)).thenReturn(Optional.of(
                concepto("11051", "Incremento DS 265", "REMUNERATIVO"))); // manual

        ExplicacionPlanillaDto r = service.explicar(EMP_ID, PERIODO);
        var byCodigo = r.lineas().stream()
                .collect(java.util.stream.Collectors.toMap(
                        ExplicacionLineaDto::codigoMef, l -> l));
        assertThat(byCodigo.get("06001").fuenteTipo()).isEqualTo("CONCEPTO_AUTO");
        assertThat(byCodigo.get("11051").fuenteTipo()).isEqualTo("EMPLEADO_CONCEPTO");
    }

    @Test
    void linea_con_diasReintegro_muestra_detalle_humano() {
        MovimientoPlanilla mov = movimiento(100L, 5500.0, 0.0, 5500.0, "BIEN");
        when(movimientoRepository.findByEmpleadoIdAndPeriodoAndActivo(EMP_ID, PERIODO, 1))
                .thenReturn(Optional.of(mov));

        MovimientoPlanillaDetalle d = detalle(1L, 100L, 916.66, 901L);
        d.setDiasReintegro(5);
        when(detalleRepository.findByMovimientoPlanillaId(100L)).thenReturn(List.of(d));
        when(conceptoRepository.findById(901L)).thenReturn(Optional.of(
                concepto("00301", "Sueldo CAS", "REMUNERATIVO")));

        ExplicacionPlanillaDto r = service.explicar(EMP_ID, PERIODO);
        assertThat(r.lineas().get(0).detalle()).contains("Reintegro 5 días");
    }

    // =================== Defensivos ===================

    @Test
    void empleado_sin_persona_no_explota_devuelve_cabecera_con_nulls() {
        stubMovimientoFeliz();
        when(empleadoRepository.findById(EMP_ID)).thenReturn(Optional.empty());

        ExplicacionPlanillaDto r = service.explicar(EMP_ID, PERIODO);

        assertThat(r.aplica()).isTrue();
        assertThat(r.cabecera().nombreCompleto()).isNull();
        assertThat(r.cabecera().dni()).isNull();
    }

    @Test
    void empleado_sin_banco_planilla_devuelve_cabecera_sin_banco() {
        stubMovimientoFeliz();
        when(bancoRepository.findByEmpleadoIdAndEsCuentaPlanillaAndActivo(EMP_ID, 1, 1))
                .thenReturn(Optional.empty());

        ExplicacionPlanillaDto r = service.explicar(EMP_ID, PERIODO);
        assertThat(r.cabecera().banco()).isNull();
        assertThat(r.cabecera().numeroCuenta()).isNull();
        assertThat(r.cabecera().cci()).isNull();
    }

    // =================== HELPERS ===================

    /** Mockea solo el movimiento y deja repos vacíos por default. */
    private MovimientoPlanilla stubMovimientoFeliz() {
        MovimientoPlanilla mov = movimiento(100L, 5500.0, 2435.0, 3065.0, "BIEN");
        when(movimientoRepository.findByEmpleadoIdAndPeriodoAndActivo(EMP_ID, PERIODO, 1))
                .thenReturn(Optional.of(mov));
        when(detalleRepository.findByMovimientoPlanillaId(100L)).thenReturn(List.of());
        return mov;
    }

    private MovimientoPlanilla movimiento(Long id, Double ingresos, Double descuentos,
            Double neto, String estadoNeto) {
        MovimientoPlanilla m = new MovimientoPlanilla();
        m.setId(id);
        m.setEmpleadoId(EMP_ID);
        m.setPeriodo(PERIODO);
        m.setTotalIngresos(ingresos);
        m.setTotalDescuentos(descuentos);
        m.setNetoPagar(neto);
        m.setEstadoNeto(estadoNeto);
        m.setActivo(1);
        return m;
    }

    private MovimientoPlanillaDetalle detalle(Long id, Long movId,
            Double monto, Long conceptoId) {
        MovimientoPlanillaDetalle d = new MovimientoPlanillaDetalle();
        d.setId(id);
        d.setMovimientoPlanillaId(movId);
        d.setConceptoPlanillaId(conceptoId);
        d.setMonto(monto);
        return d;
    }

    private ConceptoPlanilla concepto(String codigoMef, String nombre, String tipoConcepto) {
        ConceptoPlanilla c = new ConceptoPlanilla();
        c.setCodigoMef(codigoMef);
        c.setNombre(nombre);
        c.setTipoConcepto(tipoConcepto);
        return c;
    }
}
