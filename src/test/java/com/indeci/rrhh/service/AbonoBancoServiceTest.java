package com.indeci.rrhh.service;

import com.indeci.audit.context.AuditoriaContext;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.AbonoBancoDto;
import com.indeci.rrhh.dto.ResumenBancoDto;
import com.indeci.rrhh.dto.TicketMcppDto;
import com.indeci.rrhh.dto.TicketMcppMasivoDto;
import com.indeci.rrhh.entity.AbonoBanco;
import com.indeci.rrhh.entity.Bank;
import com.indeci.rrhh.entity.EmpleadoBanco;
import com.indeci.rrhh.entity.EmpleadoPlanilla;
import com.indeci.rrhh.entity.MovimientoPlanilla;
import com.indeci.rrhh.repository.AbonoBancoRepository;
import com.indeci.rrhh.repository.BankRepository;
import com.indeci.rrhh.repository.EmpleadoBancoRepository;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.MovimientoPlanillaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Spec 010 / M14 — Tests del servicio de abonos bancarios.
 *   - registrar feliz → estado PENDIENTE
 *   - registrar duplicado → NegocioException
 *   - registrar monto negativo → NegocioException
 *   - registrar ticket MCPP → estado PROCESADO
 *   - registrar ticket sobre abono ya procesado → NegocioException
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AbonoBancoServiceTest {

    @Mock private AbonoBancoRepository repository;
    @Mock private MovimientoPlanillaRepository movimientoRepository;
    @Mock private EmpleadoBancoRepository empleadoBancoRepository;
    @Mock private EmpleadoPlanillaRepository planillaRepository;
    @Mock private BankRepository bankRepository;
    @Mock private AuditoriaContext auditoriaContext;

    @InjectMocks private AbonoBancoService service;

    private static final Long MOVIMIENTO_ID = 100L;
    private static final Long EMPLEADO_ID   = 41L;
    private static final String PERIODO     = "2026-05";

    @Test
    void registrar_caso_feliz_crea_abono_pendiente() {
        when(repository.findByMovimientoPlanillaIdAndEmpleadoId(MOVIMIENTO_ID, EMPLEADO_ID))
                .thenReturn(Optional.empty());

        service.registrar(dto(2610.00));

        ArgumentCaptor<AbonoBanco> capt = ArgumentCaptor.forClass(AbonoBanco.class);
        verify(repository).save(capt.capture());
        assertThat(capt.getValue().getEstado()).isEqualTo("PENDIENTE");
        assertThat(capt.getValue().getBanco()).isEqualTo("BCP");
        assertThat(capt.getValue().getMontoNeto()).isEqualTo(2610.00);
    }

    @Test
    void registrar_duplicado_lanza_negocio() {
        when(repository.findByMovimientoPlanillaIdAndEmpleadoId(MOVIMIENTO_ID, EMPLEADO_ID))
                .thenReturn(Optional.of(new AbonoBanco()));

        assertThatThrownBy(() -> service.registrar(dto(2610.00)))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("Ya existe");
    }

    @Test
    void registrar_monto_negativo_lanza_negocio() {
        // La validación de monto ocurre antes de tocar el repositorio.
        assertThatThrownBy(() -> service.registrar(dto(-1.00)))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("negativo");
    }

    @Test
    void registrar_ticket_mcpp_pasa_abono_a_procesado() {
        AbonoBanco abono = new AbonoBanco();
        abono.setId(1L);
        abono.setEstado("PENDIENTE");
        when(repository.findById(1L)).thenReturn(Optional.of(abono));

        TicketMcppDto ticket = new TicketMcppDto();
        ticket.setNroTicketMcpp("MCPP-2026-000123");

        service.registrarTicketMcpp(1L, ticket);

        ArgumentCaptor<AbonoBanco> capt = ArgumentCaptor.forClass(AbonoBanco.class);
        verify(repository).save(capt.capture());
        assertThat(capt.getValue().getEstado()).isEqualTo("PROCESADO");
        assertThat(capt.getValue().getNroTicketMcpp()).isEqualTo("MCPP-2026-000123");
        assertThat(capt.getValue().getFechaProcesado()).isNotNull();
    }

    @Test
    void registrar_ticket_sobre_abono_ya_procesado_lanza_negocio() {
        AbonoBanco abono = new AbonoBanco();
        abono.setId(1L);
        abono.setEstado("PROCESADO");
        when(repository.findById(1L)).thenReturn(Optional.of(abono));

        TicketMcppDto ticket = new TicketMcppDto();
        ticket.setNroTicketMcpp("MCPP-2026-000999");

        assertThatThrownBy(() -> service.registrarTicketMcpp(1L, ticket))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("procesado");
    }

    // ============== GENERAR ABONOS DEL PERÍODO (PANTALLA-07) ==============

    @Test
    void generarAbonos_caso_feliz_crea_abono_con_banco_y_cci_de_la_cuenta() {
        when(movimientoRepository.findByPeriodoAndActivo(PERIODO, 1))
                .thenReturn(List.of(movimiento(100L, EMPLEADO_ID, 2610.00)));
        when(repository.findByMovimientoPlanillaIdAndEmpleadoId(100L, EMPLEADO_ID))
                .thenReturn(Optional.empty());
        when(empleadoBancoRepository.findByEmpleadoIdAndEsCuentaPlanillaAndActivo(
                EMPLEADO_ID, 1, 1))
                .thenReturn(Optional.of(cuentaPlanilla(5L, "191-123", "00219100123")));
        when(bankRepository.findById(5L)).thenReturn(Optional.of(bank("BCP")));
        when(planillaRepository.findFirstByEmpleadoIdAndActivo(EMPLEADO_ID, 1))
                .thenReturn(Optional.of(empleadoPlanilla("0056")));

        int afectados = service.generarAbonosPeriodo(PERIODO);

        assertThat(afectados).isEqualTo(1);
        ArgumentCaptor<AbonoBanco> capt = ArgumentCaptor.forClass(AbonoBanco.class);
        verify(repository).save(capt.capture());
        AbonoBanco abono = capt.getValue();
        assertThat(abono.getBanco()).isEqualTo("BCP");
        assertThat(abono.getCci()).isEqualTo("00219100123");
        assertThat(abono.getMontoNeto()).isEqualTo(2610.00);
        assertThat(abono.getEstado()).isEqualTo("PENDIENTE");
        assertThat(abono.getMeta()).isEqualTo("0056");
    }

    @Test
    void generarAbonos_empleado_sin_cuenta_queda_SIN_CUENTA() {
        when(movimientoRepository.findByPeriodoAndActivo(PERIODO, 1))
                .thenReturn(List.of(movimiento(100L, EMPLEADO_ID, 2610.00)));
        when(repository.findByMovimientoPlanillaIdAndEmpleadoId(100L, EMPLEADO_ID))
                .thenReturn(Optional.empty());
        when(empleadoBancoRepository.findByEmpleadoIdAndEsCuentaPlanillaAndActivo(
                EMPLEADO_ID, 1, 1))
                .thenReturn(Optional.empty());

        service.generarAbonosPeriodo(PERIODO);

        ArgumentCaptor<AbonoBanco> capt = ArgumentCaptor.forClass(AbonoBanco.class);
        verify(repository).save(capt.capture());
        assertThat(capt.getValue().getBanco()).isEqualTo("SIN CUENTA");
    }

    @Test
    void generarAbonos_no_reescribe_abono_ya_procesado() {
        AbonoBanco yaPagado = new AbonoBanco();
        yaPagado.setEstado("PROCESADO");
        when(movimientoRepository.findByPeriodoAndActivo(PERIODO, 1))
                .thenReturn(List.of(movimiento(100L, EMPLEADO_ID, 2610.00)));
        when(repository.findByMovimientoPlanillaIdAndEmpleadoId(100L, EMPLEADO_ID))
                .thenReturn(Optional.of(yaPagado));

        int afectados = service.generarAbonosPeriodo(PERIODO);

        assertThat(afectados).isZero();
        verify(repository, never()).save(yaPagado);
    }

    // ==================== RESUMEN POR BANCO ====================

    @Test
    void resumenPorBanco_agrupa_y_suma_por_banco() {
        when(movimientoRepository.findByPeriodoAndActivo(PERIODO, 1))
                .thenReturn(List.of(
                        movimiento(100L, 1L, 0.0),
                        movimiento(101L, 2L, 0.0)));
        when(repository.findByMovimientoPlanillaId(100L))
                .thenReturn(List.of(abono("BCP", 2610.00)));
        when(repository.findByMovimientoPlanillaId(101L))
                .thenReturn(List.of(abono("BCP", 1390.00)));

        List<ResumenBancoDto> resumen = service.resumenPorBanco(PERIODO);

        assertThat(resumen).hasSize(1);
        assertThat(resumen.get(0).getBanco()).isEqualTo("BCP");
        assertThat(resumen.get(0).getCantidad()).isEqualTo(2);
        assertThat(resumen.get(0).getTotalNeto()).isEqualTo(4000.00);
    }

    // ==================== TICKET MCPP MASIVO ====================

    @Test
    void ticketMcppMasivo_procesa_solo_los_pendientes() {
        AbonoBanco a1 = new AbonoBanco();
        a1.setId(1L);
        a1.setEstado("PENDIENTE");
        AbonoBanco a2 = new AbonoBanco();
        a2.setId(2L);
        a2.setEstado("PROCESADO"); // ya pagado — se omite
        when(repository.findById(1L)).thenReturn(Optional.of(a1));
        when(repository.findById(2L)).thenReturn(Optional.of(a2));

        TicketMcppMasivoDto dto = new TicketMcppMasivoDto();
        dto.setAbonoIds(List.of(1L, 2L));
        dto.setNroTicketMcpp("MCPP-2026-000500");

        int procesados = service.registrarTicketMcppMasivo(dto);

        assertThat(procesados).isEqualTo(1);
        assertThat(a1.getEstado()).isEqualTo("PROCESADO");
        assertThat(a1.getNroTicketMcpp()).isEqualTo("MCPP-2026-000500");
    }

    // ============================ HELPER ============================
    private MovimientoPlanilla movimiento(Long id, Long empleadoId, Double neto) {
        MovimientoPlanilla m = new MovimientoPlanilla();
        m.setId(id);
        m.setEmpleadoId(empleadoId);
        m.setPeriodo(PERIODO);
        m.setNetoPagar(neto);
        m.setActivo(1);
        return m;
    }

    private EmpleadoBanco cuentaPlanilla(Long bankId, String nroCuenta, String cci) {
        EmpleadoBanco e = new EmpleadoBanco();
        e.setBankId(bankId);
        e.setNumeroCuenta(nroCuenta);
        e.setCci(cci);
        e.setEsCuentaPlanilla(1);
        e.setActivo(1);
        return e;
    }

    private Bank bank(String name) {
        Bank b = new Bank();
        b.setName(name);
        b.setActivo(1);
        return b;
    }

    private EmpleadoPlanilla empleadoPlanilla(String meta) {
        EmpleadoPlanilla p = new EmpleadoPlanilla();
        p.setMeta(meta);
        p.setActivo(1);
        return p;
    }

    private AbonoBanco abono(String banco, Double monto) {
        AbonoBanco a = new AbonoBanco();
        a.setBanco(banco);
        a.setMontoNeto(monto);
        a.setEstado("PENDIENTE");
        return a;
    }

    private AbonoBancoDto dto(double montoNeto) {
        AbonoBancoDto d = new AbonoBancoDto();
        d.setMovimientoPlanillaId(MOVIMIENTO_ID);
        d.setEmpleadoId(EMPLEADO_ID);
        d.setBanco("BCP");
        d.setNroCuenta("191-1234567-0-12");
        d.setCci("00219100123456701234");
        d.setMeta("0056");
        d.setMontoNeto(montoNeto);
        return d;
    }
}
