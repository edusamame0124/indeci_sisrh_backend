package com.indeci.rrhh.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.indeci.audit.annotation.Auditable;
import com.indeci.audit.context.AuditoriaContext;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.AbonoBancoDto;
import com.indeci.rrhh.dto.AbonoBancoResponseDto;
import com.indeci.rrhh.dto.ResumenBancoDto;
import com.indeci.rrhh.dto.TicketMcppDto;
import com.indeci.rrhh.dto.TicketMcppMasivoDto;
import com.indeci.rrhh.entity.AbonoBanco;
import com.indeci.rrhh.entity.EmpleadoBanco;
import com.indeci.rrhh.entity.MovimientoPlanilla;
import com.indeci.rrhh.repository.AbonoBancoRepository;
import com.indeci.rrhh.repository.BankRepository;
import com.indeci.rrhh.repository.EmpleadoBancoRepository;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.MovimientoPlanillaRepository;

import lombok.RequiredArgsConstructor;

/**
 * Spec 010 / M14 — Generación de abonos bancarios + ticket MCPP (SPEC §10.3).
 *
 * Estados: PENDIENTE → {PROCESADO | RECHAZADO}.
 * Tesorería registra el NRO_TICKET_MCPP; eso pasa el abono a PROCESADO
 * (SPEC §12.2 PANTALLA-07).
 */
@Service
@RequiredArgsConstructor
public class AbonoBancoService {

    private static final String EST_PENDIENTE = "PENDIENTE";
    private static final String EST_PROCESADO = "PROCESADO";

    private final AbonoBancoRepository repository;
    private final MovimientoPlanillaRepository movimientoRepository;
    private final EmpleadoBancoRepository empleadoBancoRepository;
    private final EmpleadoPlanillaRepository planillaRepository;
    private final BankRepository bankRepository;
    private final AuditoriaContext auditoriaContext;

    // ============================ REGISTRAR ============================
    @Auditable(accion = "REGISTRAR_ABONO_BANCO")
    public void registrar(AbonoBancoDto dto) {
        if (dto.getMovimientoPlanillaId() == null || dto.getEmpleadoId() == null) {
            throw new NegocioException(
                    "El abono requiere movimientoPlanillaId y empleadoId");
        }
        if (dto.getBanco() == null || dto.getBanco().isBlank()) {
            throw new NegocioException("El abono requiere el banco");
        }
        if (dto.getMontoNeto() == null || dto.getMontoNeto() < 0) {
            throw new NegocioException("El monto neto del abono no puede ser negativo");
        }
        // Un abono por (movimiento, empleado) — coincide con la UK de BD.
        repository.findByMovimientoPlanillaIdAndEmpleadoId(
                        dto.getMovimientoPlanillaId(), dto.getEmpleadoId())
                .ifPresent(a -> {
                    throw new NegocioException(
                            "Ya existe un abono para ese empleado y movimiento");
                });

        AbonoBanco entity = new AbonoBanco();
        entity.setMovimientoPlanillaId(dto.getMovimientoPlanillaId());
        entity.setEmpleadoId(dto.getEmpleadoId());
        entity.setBanco(dto.getBanco());
        entity.setNroCuenta(dto.getNroCuenta());
        entity.setCci(dto.getCci());
        entity.setMeta(dto.getMeta());
        entity.setMontoNeto(dto.getMontoNeto());
        entity.setEstado(EST_PENDIENTE);
        entity.setCreatedAt(LocalDateTime.now());

        repository.save(entity);
        auditoriaContext.setDetalle(
                "Abono bancario registrado — empleado " + dto.getEmpleadoId()
                        + ", banco " + dto.getBanco());
    }

    // ==================== GENERAR ABONOS DEL PERÍODO ====================

    /**
     * Genera (UPSERT) un abono por cada movimiento del período (SPEC §12.2
     * PANTALLA-07). Toma banco/cuenta/CCI de la cuenta de planilla activa del
     * empleado y el neto del movimiento.
     *
     * <p>Idempotente: si el abono ya existe y sigue PENDIENTE, se actualiza con
     * los datos vigentes; si ya está PROCESADO (ticket MCPP cargado) NO se toca.
     * El empleado sin cuenta de planilla queda con banco "SIN CUENTA".
     *
     * @return número de abonos creados o actualizados.
     */
    @Auditable(accion = "GENERAR_ABONOS_PERIODO")
    @Transactional
    public int generarAbonosPeriodo(String periodo) {

        List<MovimientoPlanilla> movimientos =
                movimientoRepository.findByPeriodoAndActivo(periodo, 1);

        int afectados = 0;
        for (MovimientoPlanilla mov : movimientos) {

            AbonoBanco existente = repository
                    .findByMovimientoPlanillaIdAndEmpleadoId(
                            mov.getId(), mov.getEmpleadoId())
                    .orElse(null);
            if (existente != null && EST_PROCESADO.equals(existente.getEstado())) {
                continue; // ya pagado — no se reescribe
            }

            EmpleadoBanco cuenta = empleadoBancoRepository
                    .findByEmpleadoIdAndEsCuentaPlanillaAndActivo(
                            mov.getEmpleadoId(), 1, 1)
                    .orElse(null);

            String banco = "SIN CUENTA";
            String nroCuenta = null;
            String cci = null;
            if (cuenta != null) {
                nroCuenta = cuenta.getNumeroCuenta();
                cci = cuenta.getCci();
                if (cuenta.getBankId() != null) {
                    banco = bankRepository.findById(cuenta.getBankId())
                            .map(b -> b.getName())
                            .filter(n -> n != null && !n.isBlank())
                            .orElse("SIN BANCO");
                }
            }

            String meta = planillaRepository
                    .findFirstByEmpleadoIdAndActivo(mov.getEmpleadoId(), 1)
                    .map(p -> p.getMeta())
                    .orElse(null);

            AbonoBanco abono = existente != null ? existente : new AbonoBanco();
            abono.setMovimientoPlanillaId(mov.getId());
            abono.setEmpleadoId(mov.getEmpleadoId());
            abono.setBanco(banco);
            abono.setNroCuenta(nroCuenta);
            abono.setCci(cci);
            abono.setMeta(meta);
            abono.setMontoNeto(mov.getNetoPagar() != null ? mov.getNetoPagar() : 0d);
            abono.setEstado(EST_PENDIENTE);
            if (abono.getCreatedAt() == null) {
                abono.setCreatedAt(LocalDateTime.now());
            }
            repository.save(abono);
            afectados++;
        }

        auditoriaContext.setDetalle(
                "Abonos generados período " + periodo + ": " + afectados);
        return afectados;
    }

    // ==================== RESUMEN POR BANCO ====================

    /**
     * Agrupa los abonos del período por banco (SPEC §12.2 PANTALLA-07).
     * Los abonos se localizan a través de los movimientos del período.
     */
    public List<ResumenBancoDto> resumenPorBanco(String periodo) {

        Map<String, ResumenBancoDto> grupos = new LinkedHashMap<>();

        for (MovimientoPlanilla mov :
                movimientoRepository.findByPeriodoAndActivo(periodo, 1)) {

            for (AbonoBanco abono :
                    repository.findByMovimientoPlanillaId(mov.getId())) {

                ResumenBancoDto grupo = grupos.computeIfAbsent(
                        abono.getBanco(), banco -> {
                            ResumenBancoDto g = new ResumenBancoDto();
                            g.setBanco(banco);
                            g.setCantidad(0);
                            g.setTotalNeto(0d);
                            g.setAbonos(new ArrayList<>());
                            return g;
                        });

                double monto = abono.getMontoNeto() != null
                        ? abono.getMontoNeto() : 0d;
                grupo.setCantidad(grupo.getCantidad() + 1);
                grupo.setTotalNeto(grupo.getTotalNeto() + monto);
                grupo.getAbonos().add(toResponse(abono));
            }
        }

        List<ResumenBancoDto> resultado = new ArrayList<>(grupos.values());
        for (ResumenBancoDto g : resultado) {
            g.setTotalNeto(redondear(g.getTotalNeto()));
        }
        resultado.sort(Comparator.comparing(ResumenBancoDto::getBanco));
        return resultado;
    }

    // ============================ LISTAR ============================
    public List<AbonoBancoResponseDto> listarPorMovimiento(Long movimientoPlanillaId) {
        return repository.findByMovimientoPlanillaId(movimientoPlanillaId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // ============================ REGISTRAR TICKET MCPP ============================
    @Auditable(accion = "REGISTRAR_TICKET_MCPP")
    public void registrarTicketMcpp(Long id, TicketMcppDto dto) {
        if (dto.getNroTicketMcpp() == null || dto.getNroTicketMcpp().isBlank()) {
            throw new NegocioException("El número de ticket MCPP es obligatorio");
        }
        AbonoBanco entity = repository.findById(id)
                .orElseThrow(() -> new NegocioException("Abono bancario no encontrado"));

        if (!EST_PENDIENTE.equals(entity.getEstado())) {
            throw new NegocioException(
                    "El abono ya fue procesado o rechazado (estado " + entity.getEstado() + ")");
        }

        entity.setNroTicketMcpp(dto.getNroTicketMcpp());
        entity.setEstado(EST_PROCESADO);
        entity.setFechaProcesado(LocalDate.now());

        repository.save(entity);
        auditoriaContext.setDetalle(
                "Abono " + id + " procesado con ticket MCPP " + dto.getNroTicketMcpp());
    }

    // ==================== REGISTRAR TICKET MCPP MASIVO ====================

    /**
     * Aplica un mismo ticket MCPP a varios abonos (SPEC §12.2 PANTALLA-07 —
     * ingreso masivo). Los abonos que ya no estén PENDIENTE se omiten en
     * silencio: el masivo es tolerante.
     *
     * @return número de abonos efectivamente procesados.
     */
    @Auditable(accion = "REGISTRAR_TICKET_MCPP_MASIVO")
    @Transactional
    public int registrarTicketMcppMasivo(TicketMcppMasivoDto dto) {
        if (dto.getNroTicketMcpp() == null || dto.getNroTicketMcpp().isBlank()) {
            throw new NegocioException("El número de ticket MCPP es obligatorio");
        }
        if (dto.getAbonoIds() == null || dto.getAbonoIds().isEmpty()) {
            throw new NegocioException("No se indicaron abonos para procesar");
        }

        int procesados = 0;
        for (Long id : dto.getAbonoIds()) {
            AbonoBanco abono = repository.findById(id).orElse(null);
            if (abono == null || !EST_PENDIENTE.equals(abono.getEstado())) {
                continue;
            }
            abono.setNroTicketMcpp(dto.getNroTicketMcpp());
            abono.setEstado(EST_PROCESADO);
            abono.setFechaProcesado(LocalDate.now());
            repository.save(abono);
            procesados++;
        }

        auditoriaContext.setDetalle(
                "Ticket MCPP " + dto.getNroTicketMcpp()
                        + " aplicado a " + procesados + " abono(s)");
        return procesados;
    }

    // ============================ HELPERS ============================
    private AbonoBancoResponseDto toResponse(AbonoBanco e) {
        AbonoBancoResponseDto dto = new AbonoBancoResponseDto();
        dto.setId(e.getId());
        dto.setMovimientoPlanillaId(e.getMovimientoPlanillaId());
        dto.setEmpleadoId(e.getEmpleadoId());
        dto.setBanco(e.getBanco());
        dto.setNroCuenta(e.getNroCuenta());
        dto.setCci(e.getCci());
        dto.setMeta(e.getMeta());
        dto.setMontoNeto(e.getMontoNeto());
        dto.setEstado(e.getEstado());
        dto.setNroTicketMcpp(e.getNroTicketMcpp());
        dto.setFechaProcesado(e.getFechaProcesado());
        return dto;
    }

    private static double redondear(double valor) {
        return BigDecimal.valueOf(valor)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
