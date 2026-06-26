package com.indeci.rrhh.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.indeci.audit.annotation.Auditable;
import com.indeci.audit.context.AuditoriaContext;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.Suspension4taVigenteDto;
import com.indeci.rrhh.dto.ir4ta.Ir4taAcumuladoDetalleDto;
import com.indeci.rrhh.dto.ir4ta.Ir4taControlAnualDto;
import com.indeci.rrhh.dto.ir4ta.Ir4taReinicioInputDto;
import com.indeci.rrhh.entity.CalculoSnapshot;
import com.indeci.rrhh.entity.Ir4taConfigAnual;
import com.indeci.rrhh.entity.Ir4taControlAnual;
import com.indeci.rrhh.repository.CalculoSnapshotRepository;
import com.indeci.rrhh.repository.Ir4taControlAnualRepository;
import java.util.List;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;

/**
 * Control anual del tope de suspensión IR4ta por trabajador (pendiente B2).
 *
 * <p>Recalcula el <b>acumulado conocido por INDECI</b> (suma de base afecta 4ta
 * de los snapshots {@code IR4TA_CAS} del año), detecta el exceso del tope y
 * gobierna la confirmación de reinicio por RR.HH. <b>No modifica el motor ni las
 * planillas históricas</b>: el motor lo consulta vía {@link #evaluarEnMotor}.</p>
 *
 * <p>Comparación de exceso ESTRICTA: {@code acumuladoProyectado > topeAnual}
 * (nunca {@code >=}).</p>
 */
@Service
@RequiredArgsConstructor
public class Ir4taControlAnualService {

    private static final Logger log = LoggerFactory.getLogger(Ir4taControlAnualService.class);

    public static final String TIPO_GENERAL  = "GENERAL_CAS";
    public static final String TIPO_DIRECTOR = "DIRECTOR_SIMILAR";

    public static final String EST_VIGENTE   = "VIGENTE";
    public static final String EST_ALERTA_80 = "ALERTA_80_PORCIENTO";
    public static final String EST_ALERTA_90 = "ALERTA_90_PORCIENTO";
    public static final String EST_CERCA     = "CERCA_DEL_TOPE";
    public static final String EST_EXCEDE    = "EXCEDE_TOPE_REQUIERE_VALIDACION";
    public static final String EST_REINICIO  = "REINICIO_CONFIRMADO";
    public static final String EST_RET_ACTIVA = "RETENCION_ACTIVA";

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private final Ir4taControlAnualRepository controlRepo;
    private final CalculoSnapshotRepository snapshotRepo;
    private final Ir4taConfigService configService;
    private final Suspension4taService suspension4taService;
    private final AuditoriaContext auditoriaContext;
    private final ObjectMapper objectMapper;

    // ======================================================================
    // DECISIÓN DEL MOTOR (efecto lateral: monitorea y persiste; defensivo)
    // ======================================================================

    /** Resultado para el motor: si retener pese a la constancia, y el estado. */
    public record MotorDecision(boolean aplicarRetencionPeseASuspension, String estadoControl) {
        static MotorDecision sinCambio() { return new MotorDecision(false, null); }
    }

    /**
     * Evalúa el tope anual para el período en curso. Solo tiene efecto cuando hay
     * constancia vigente (suspensión). Actualiza el monitoreo (acumulado, estado,
     * detección de exceso) y decide si el motor debe retener pese a la suspensión
     * (solo si RR.HH. confirmó el reinicio y el período ya alcanzó el de inicio,
     * o si la regla de retención automática está activada).
     *
     * <p>Defensivo: cualquier error deja la decisión en "sin cambio" (el motor
     * mantiene su comportamiento actual) y no rompe la generación.</p>
     */
    @Transactional
    public MotorDecision evaluarEnMotor(
            Long empleadoId, int anioFiscal, String periodo,
            BigDecimal baseAfectaPeriodo, boolean suspensionVigente, Long suspension4taId) {
        try {
            if (!suspensionVigente || empleadoId == null || periodo == null) {
                return MotorDecision.sinCambio();
            }
            Optional<Ir4taConfigAnual> cfgOpt =
                    configService.resolverPorAnio(anioFiscal, LocalDate.of(anioFiscal, 6, 15));
            if (cfgOpt.isEmpty()) {
                return MotorDecision.sinCambio();
            }
            Ir4taConfigAnual cfg = cfgOpt.get();
            if (!flag(cfg.getFlgCalcAcumulado(), true)) {
                return MotorDecision.sinCambio();
            }

            Ir4taControlAnual ctrl = controlRepo
                    .findByEmpleadoIdAndAnioFiscal(empleadoId, anioFiscal)
                    .orElseGet(() -> nuevoControl(empleadoId, anioFiscal));

            String tipoTope = ctrl.getTipoTope() != null ? ctrl.getTipoTope() : TIPO_GENERAL;
            BigDecimal tope = topeParaTipo(cfg, tipoTope);
            if (tope == null || tope.signum() <= 0) {
                return MotorDecision.sinCambio();
            }

            BigDecimal base = baseAfectaPeriodo == null ? BigDecimal.ZERO : baseAfectaPeriodo;
            BigDecimal acumPrevio = snapshotRepo.sumarBaseIr4taPorAnio(
                    empleadoId, anioPrefijo(anioFiscal), periodo);
            if (acumPrevio == null) acumPrevio = BigDecimal.ZERO;
            BigDecimal proyectado = acumPrevio.add(base);

            boolean aplicar = false;
            String estado;

            // Reinicio ya confirmado por RR.HH. y período en curso lo alcanza → retiene.
            if (EST_RET_ACTIVA.equals(ctrl.getEstadoControl())
                    && ctrl.getPeriodoReinicio() != null
                    && comparaPeriodo(periodo, ctrl.getPeriodoReinicio()) >= 0) {
                aplicar = true;
                estado = EST_RET_ACTIVA;
            } else {
                estado = calcularEstado(acumPrevio, proyectado, tope, cfg);
                if (EST_EXCEDE.equals(estado)) {
                    // Marca detección de exceso (una sola vez) si la regla lo pide.
                    if (flag(cfg.getFlgMarcarValidacion(), true) && ctrl.getPeriodoExceso() == null) {
                        ctrl.setPeriodoExceso(periodo);
                        ctrl.setFechaDeteccionExceso(LocalDateTime.now());
                    }
                    // Retención automática SOLO si se habilitó explícitamente (off por defecto).
                    if (flag(cfg.getFlgRetencionAuto(), false)) {
                        aplicar = true;
                        estado = EST_RET_ACTIVA;
                    }
                }
            }

            ctrl.setSuspension4taId(suspension4taId);
            ctrl.setTopeAnualAplicado(tope);
            ctrl.setAcumuladoIndeci(acumPrevio);
            ctrl.setPctConsumido(pct(acumPrevio, tope));
            ctrl.setUltimoPeriodoCalc(periodo);
            // No degradar un estado terminal (RETENCION_ACTIVA) ya alcanzado.
            if (!EST_RET_ACTIVA.equals(ctrl.getEstadoControl()) || aplicar) {
                ctrl.setEstadoControl(estado);
            }
            ctrl.setModificadoPor("MOTOR");
            ctrl.setModificadoEn(LocalDateTime.now());
            controlRepo.save(ctrl);

            return new MotorDecision(aplicar, ctrl.getEstadoControl());
        } catch (RuntimeException e) {
            log.warn("Control anual IR4ta no evaluado (empleado {}, periodo {}): {}",
                    empleadoId, periodo, e.getMessage());
            return MotorDecision.sinCambio();
        }
    }

    // ======================================================================
    // LECTURA (Wireframe B — pestaña Control anual)
    // ======================================================================

    @Transactional(readOnly = true)
    public Ir4taControlAnualDto obtenerControl(Long empleadoId, int anioFiscal) {
        Ir4taControlAnualDto dto = new Ir4taControlAnualDto();
        dto.setEmpleadoId(empleadoId);
        dto.setAnioFiscal(anioFiscal);

        Suspension4taVigenteDto susp = suspension4taService.consultarVigente(
                empleadoId, LocalDate.of(anioFiscal, 6, 15));
        dto.setExisteConstanciaVigente(susp.vigente());
        dto.setNroConstancia(susp.nroConstancia());
        dto.setEstadoConstancia(susp.vigente() ? "VIGENTE"
                : (susp.existeVencida() ? "VENCIDA" : "-"));

        Optional<Ir4taControlAnual> ctrlOpt =
                controlRepo.findByEmpleadoIdAndAnioFiscal(empleadoId, anioFiscal);
        String tipoTope = ctrlOpt.map(Ir4taControlAnual::getTipoTope).orElse(TIPO_GENERAL);
        dto.setTipoTope(tipoTope);

        Optional<Ir4taConfigAnual> cfgOpt =
                configService.resolverPorAnio(anioFiscal, LocalDate.of(anioFiscal, 6, 15));
        BigDecimal tope = cfgOpt.map(c -> topeParaTipo(c, tipoTope)).orElse(null);
        dto.setTopeAnual(tope);

        BigDecimal acumulado = snapshotRepo.sumarBaseIr4taPorAnio(
                empleadoId, anioPrefijo(anioFiscal), null);
        if (acumulado == null) acumulado = BigDecimal.ZERO;
        dto.setAcumuladoIndeci(acumulado);

        boolean aplica = tope != null && tope.signum() > 0;
        dto.setAplicaControl(aplica && susp.vigente());
        if (aplica) {
            dto.setSaldoDisponible(tope.subtract(acumulado).max(BigDecimal.ZERO));
            dto.setPctConsumido(pct(acumulado, tope));
        }

        ctrlOpt.ifPresent(c -> {
            dto.setUltimoPeriodoCalc(c.getUltimoPeriodoCalc());
            dto.setEstadoControl(c.getEstadoControl());
            dto.setPeriodoExceso(c.getPeriodoExceso());
            dto.setFechaDeteccionExceso(c.getFechaDeteccionExceso());
            dto.setPeriodoReinicio(c.getPeriodoReinicio());
            dto.setSustentoReinicio(c.getSustentoReinicio());
            dto.setConfirmadoPor(c.getConfirmadoPor());
            dto.setConfirmadoEn(c.getConfirmadoEn());
        });
        if (dto.getEstadoControl() == null) {
            dto.setEstadoControl(aplica ? EST_VIGENTE : "-");
        }
        return dto;
    }

    @Transactional(readOnly = true)
    public List<Ir4taAcumuladoDetalleDto> obtenerDetalleAcumulado(Long empleadoId, int anioFiscal) {
        List<CalculoSnapshot> snapshots = snapshotRepo.findIr4taCasVigentesPorAnio(empleadoId, anioPrefijo(anioFiscal));
        return snapshots.stream().map(s -> {
            Ir4taAcumuladoDetalleDto dto = new Ir4taAcumuladoDetalleDto();
            dto.setPeriodo(s.getPeriodo());
            
            BigDecimal baseAfecta = s.getBaseCalculo() != null ? s.getBaseCalculo() : BigDecimal.ZERO;
            dto.setBaseAfecta(baseAfecta);
            
            BigDecimal ingresosBrutos = baseAfecta;
            if (s.getParametrosJson() != null && !s.getParametrosJson().isEmpty()) {
                try {
                    JsonNode root = objectMapper.readTree(s.getParametrosJson());
                    if (root.has("baseImponible") && !root.get("baseImponible").isNull()) {
                        ingresosBrutos = new BigDecimal(root.get("baseImponible").asText());
                    }
                } catch (Exception e) {
                    log.warn("No se pudo parsear parametrosJson para snapshot {}", s.getId());
                }
            }
            dto.setIngresosBrutos(ingresosBrutos);
            dto.setDeducciones(ingresosBrutos.subtract(baseAfecta));
            
            return dto;
        }).collect(Collectors.toList());
    }

    // ======================================================================
    // FLAG MANUAL DE TIPO DE TOPE (RR.HH.)
    // ======================================================================

    @Auditable(accion = "DEFINIR_TIPO_TOPE_IR4TA")
    @Transactional
    public Ir4taControlAnualDto definirTipoTope(
            Long empleadoId, int anioFiscal, String tipoTope, String usuario) {
        if (!TIPO_GENERAL.equals(tipoTope) && !TIPO_DIRECTOR.equals(tipoTope)) {
            throw new NegocioException("Tipo de tope inválido: " + tipoTope);
        }
        Ir4taControlAnual ctrl = controlRepo
                .findByEmpleadoIdAndAnioFiscal(empleadoId, anioFiscal)
                .orElseGet(() -> nuevoControl(empleadoId, anioFiscal));
        ctrl.setTipoTope(tipoTope);
        ctrl.setModificadoPor(usuario);
        ctrl.setModificadoEn(LocalDateTime.now());
        if (ctrl.getCreadoPor() == null) ctrl.setCreadoPor(usuario);
        controlRepo.save(ctrl);
        auditoriaContext.setDetalle("Tipo de tope IR4ta '" + tipoTope
                + "' — empleado " + empleadoId + ", año " + anioFiscal);
        return obtenerControl(empleadoId, anioFiscal);
    }

    // ======================================================================
    // CONFIRMACIÓN DE REINICIO (RR.HH. — rol autorizado)
    // ======================================================================

    @Auditable(accion = "CONFIRMAR_REINICIO_IR4TA")
    @Transactional
    public Ir4taControlAnualDto confirmarReinicio(
            Long empleadoId, Ir4taReinicioInputDto input, String usuario) {
        Ir4taControlAnual ctrl = controlRepo
                .findByEmpleadoIdAndAnioFiscal(empleadoId, input.getAnioFiscal())
                .orElseThrow(() -> new NegocioException(
                        "No existe control anual para el empleado " + empleadoId
                                + " en " + input.getAnioFiscal() + "."));

        if (!EST_EXCEDE.equals(ctrl.getEstadoControl())) {
            throw new NegocioException(
                    "Solo se puede confirmar el reinicio cuando el control está en "
                            + "EXCEDE_TOPE_REQUIERE_VALIDACION (estado actual: "
                            + ctrl.getEstadoControl() + ").");
        }

        ctrl.setEstadoControl(EST_RET_ACTIVA);
        ctrl.setPeriodoReinicio(input.getPeriodoReinicio());
        ctrl.setSustentoReinicio(input.getSustento());
        if (input.getObservacion() != null) ctrl.setObservacion(input.getObservacion());
        ctrl.setConfirmadoPor(usuario);
        ctrl.setConfirmadoEn(LocalDateTime.now());
        ctrl.setModificadoPor(usuario);
        ctrl.setModificadoEn(LocalDateTime.now());
        controlRepo.save(ctrl);

        auditoriaContext.setDetalle("Reinicio IR4ta confirmado — empleado " + empleadoId
                + ", año " + input.getAnioFiscal() + ", desde " + input.getPeriodoReinicio());
        return obtenerControl(empleadoId, input.getAnioFiscal());
    }

    // ======================================================================
    // HELPERS
    // ======================================================================

    /** Estado de control derivado del consumo (comparación de exceso estricta). */
    String calcularEstado(
            BigDecimal acumulado, BigDecimal proyectado, BigDecimal tope, Ir4taConfigAnual cfg) {
        if (proyectado.compareTo(tope) > 0) {
            return EST_EXCEDE;
        }
        BigDecimal pctConsumido = pct(acumulado, tope);
        BigDecimal pctPrev = cfg.getPctAlertaPrev() != null ? cfg.getPctAlertaPrev() : BigDecimal.valueOf(80);
        BigDecimal pctCrit = cfg.getPctAlertaCrit() != null ? cfg.getPctAlertaCrit() : BigDecimal.valueOf(90);
        if (pctConsumido.compareTo(CIEN) >= 0) {
            return EST_CERCA;
        }
        if (pctConsumido.compareTo(pctCrit) >= 0) {
            return EST_ALERTA_90;
        }
        if (pctConsumido.compareTo(pctPrev) >= 0) {
            return EST_ALERTA_80;
        }
        return EST_VIGENTE;
    }

    private BigDecimal topeParaTipo(Ir4taConfigAnual cfg, String tipoTope) {
        return TIPO_DIRECTOR.equals(tipoTope)
                ? cfg.getTopeAnualDirector()
                : cfg.getTopeAnualGeneral();
    }

    private BigDecimal pct(BigDecimal monto, BigDecimal tope) {
        if (tope == null || tope.signum() <= 0) return BigDecimal.ZERO;
        return monto.multiply(CIEN).divide(tope, 2, RoundingMode.HALF_UP);
    }

    private Ir4taControlAnual nuevoControl(Long empleadoId, int anioFiscal) {
        Ir4taControlAnual c = new Ir4taControlAnual();
        c.setEmpleadoId(empleadoId);
        c.setAnioFiscal(anioFiscal);
        c.setTipoTope(TIPO_GENERAL);
        c.setAcumuladoIndeci(BigDecimal.ZERO);
        c.setEstadoControl(EST_VIGENTE);
        c.setCreadoPor("MOTOR");
        c.setCreadoEn(LocalDateTime.now());
        return c;
    }

    private static boolean flag(Integer v, boolean def) {
        return v == null ? def : v == 1;
    }

    private static String anioPrefijo(int anio) {
        return anio + "%";
    }

    /** Compara dos períodos ignorando el separador (YYYY-MM vs YYYYMM). */
    private static int comparaPeriodo(String a, String b) {
        return normaliza(a).compareTo(normaliza(b));
    }

    private static String normaliza(String periodo) {
        return periodo == null ? "" : periodo.replace("-", "");
    }
}
