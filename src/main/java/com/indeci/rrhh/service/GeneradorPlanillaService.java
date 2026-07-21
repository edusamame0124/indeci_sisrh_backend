package com.indeci.rrhh.service;

import com.indeci.exception.ConceptoRegimenNoAplicableException;
import com.indeci.exception.ConceptoSinCodigoMefException;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.GeneracionFallidaDto;
import com.indeci.rrhh.dto.GeneracionMasivaResultDto;
import com.indeci.rrhh.dto.GenerarPlanillaCabeceraDto;
import com.indeci.rrhh.dto.ResumenPlanillaDto;
import com.indeci.rrhh.dto.SubsidioCalculadoDto;
import com.indeci.rrhh.dto.Suspension4taVigenteDto;
import com.indeci.rrhh.entity.*;
import com.indeci.rrhh.repository.*;
import com.indeci.rrhh.service.support.RegimenAplicableHelper;
import com.indeci.rrhh.entity.Ir4taConfigAnual;

import java.time.LocalDate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Spec 010 — Motor de generación de planilla.
 *
 * Refactor a partir del flujo del SPEC §5.2 con validaciones LEY-01/02/07:
 *   1. validarPeriodoAbierto
 *   2. obtenerConfigPlanilla
 *   3. obtenerPension (opcional)
 *   4. borrarMovimientoAnterior + crearCabecera
 *   5. calcularRemunerativos     (sueldo básico + asig. familiar 728/CAS)
 *   6. calcularNoRemunerativos   (placeholder Etapa 1 — solo manuales)
 *   7. aplicarConceptosManuales  (EmpleadoConcepto activos del empleado,
 *                                 valida CODIGO_MEF — LEY-01)
 *  7b. calcularDescuentoAsistencia (PASO 7 — tardanzas + faltas de la
 *                                 asistencia VALIDADA del período; D.Leg. 276 Art. 24)
 *   8. calcularAportePensionario (ONP 13% | AFP 10% — LEY-02 NO ESSALUD aquí)
 *   9. calcularEssaludEmpleador  (§5.5 — mínimo regulatorio + split EPS 6.75/2.25)
 *  10. calcularTotalesYCUC
 *  16. crearConciliacionAirhsp  (PASO 16 — conciliación monto sistema vs AIRHSP)
 *
 * Validaciones críticas:
 *   - LEY-01: Concepto sin CODIGO_MEF → {@link ConceptoSinCodigoMefException}
 *   - LEY-02: ESSALUD nunca aparece como descuento al trabajador
 *   - LEY-07: ESSALUD se graba como APORTE_EMPLEADOR y NO suma al neto
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GeneradorPlanillaService {

    // Códigos MEF que el motor calcula automáticamente. Si faltan en BD, se
    // pide ejecutar el seed V010_04.
    private static final String MEF_ASIG_FAMILIAR_728 = "00302";
    private static final String MEF_ASIG_FAMILIAR_CAS = "00502";
    private static final String MEF_APORTE_ONP        = "05001";
    private static final String MEF_APORTE_AFP        = "05002";
    private static final String MEF_COMISION_AFP      = "05003";
    private static final String MEF_SEGURO_AFP        = "05004";
    private static final String MEF_ESSALUD           = "06001";  // ESSALUD 9% sin EPS
    private static final String MEF_ESSALUD_EPS       = "06002";  // ESSALUD 6.75% con EPS
    private static final String MEF_COPAGO_EPS        = "05309";  // Copago EPS 2.25% trabajador
    private static final String MEF_RETENCION_5TA     = "05101";  // Retención IR 5ta categoría
    private static final String MEF_DESC_TARDANZA     = "05401";  // Descuento por tardanza (PASO 7)
    private static final String MEF_DESC_FALTA        = "05402";  // Descuento por falta (PASO 7)
    private static final String CODIGO_INTERNO_DESC_JUDICIAL = "DESCUENTO_JUDICIAL";
    private static final String TIPO_CONCEPTO_DESC_JUDICIAL  = "DESCUENTO_JUDICIAL";
    private static final String SISPER_DESC_JUDICIAL         = "716"; // Código operativo SISPER

    /**
     * CODIGO interno del concepto de retención IR 4ta CAS (V010_49). Se resuelve
     * por CODIGO (no por CODIGO_MEF) porque es una retención tributaria SUNAT
     * (3042), NO un concepto de ingreso MEF/AIRHSP — su CODIGO_MEF es 'NO_APLICA'
     * y NUNCA debe bloquear la planilla.
     */
    private static final String CODIGO_INTERNO_IR4TA_CAS = "IR4TA_CAS";

    /**
     * FASE 4 — CODIGO interno de los conceptos de subsidio (V010_58). Se
     * resuelven por CODIGO (no por CODIGO_MEF): son conceptos PLAME/SUNAT
     * (0915/0916), NO MEF/AIRHSP. Son NO_REMUNERATIVO (no afectan IR/EsSalud/AFP).
     */
    private static final String CODIGO_SUBSIDIO_MATERNIDAD = "SUBSIDIO_MATERNIDAD";
    private static final String CODIGO_SUBSIDIO_ENFERMEDAD = "SUBSIDIO_ENFERMEDAD";
    private static final String TIPO_SUBSIDIO_MATERNIDAD   = "MATERNIDAD";

    /**
     * Mejora 2026-06-03 — Remuneración base por régimen (CODIGO_MEF). La base
     * se toma de {@code EmpleadoPlanilla.sueldoBasico} (Configuración de planilla),
     * NO de un concepto asignado a mano. Mapeo confirmado por RRHH:
     * CAS=00501, 728=00301, 276=00102(MUC); SERVIR por subgrupo L001–L004.
     */
    private static final String MEF_BASE_276 = "00102";  // MUC
    private static final String MEF_BASE_728 = "00301";  // Sueldo Básico
    private static final String MEF_BASE_CAS = "00501";  // Remuneración CAS

    /**
     * SERVIR (Ley 30057 / RD0111-2021-EF) — base por grupo del servidor civil.
     * Las claves son los valores que persiste el frontend en
     * EMPLEADO_PLANILLA.GRUPO_SERVIDOR_CIVIL (ver GRUPOS_SERVIDOR_CIVIL en la UI).
     */
    private static final Map<String, String> SERVIR_SUBGRUPO_BASE_MEF = Map.of(
            "FUNCIONARIO", "L001",                  // Compensación del Funcionario Público
            "DIRECTIVO", "L002",                    // Compensación del Directivo Público
            "CARRERA", "L003",                      // Compensación del Servidor Civil de Carrera
            "ACTIVIDADES_COMPLEMENTARIAS", "L004"); // Compensación de Actividades Complementarias
    /** Fallback operativo si el SERVIR no tiene grupo informado (no bloquea — decisión RR.HH.). */
    private static final String MEF_BASE_SERVIR_FALLBACK = "L003";

    /**
     * Conceptos de "remuneración base" que el motor calcula desde sueldoBasico y,
     * por tanto, NO se asignan a mano ni se suman desde EmpleadoConcepto (evita
     * doble conteo). Incluye 00101 legacy de 276 además del 00102 activo.
     */
    private static final Set<String> MEF_BASE_REMUNERATIVA = Set.of(
            "00101", "00102", "00301", "00501",
            "L001", "L002", "L003", "L004");

    /**
     * CODIGO_MEF que el motor calcula automáticamente. Si un EmpleadoConcepto
     * manual apunta a uno de estos, se IGNORA en aplicarConceptosManuales para
     * no duplicar (el motor ya los calcula): asignación familiar, aportes
     * pensionarios, retención 5ta, ESSALUD y copago EPS.
     */
    private static final Set<String> MEF_AUTOCALCULADOS = Set.of(
            "00302", "00502",                   // asignación familiar 728 / CAS
            "05001", "05002", "05003", "05004", // aporte ONP / AFP + comisión + prima
            "05101",                            // retención 5ta categoría
            "06001", "06002", "05309",          // ESSALUD sin/con EPS + copago EPS
            "05401", "05402");                  // descuento tardanza / falta (PASO 7 — asistencia)

    private static final String REG_LABORAL_728    = "728";
    private static final String REG_LABORAL_CAS    = "CAS";    // INDECI_REGIMEN_LABORAL.CODIGO
    private static final String REG_LABORAL_CAS_1057 = "1057"; // Alias legacy usado en catálogos MEF/SUNAT
    private static final String REG_LABORAL_SERVIR = "SERVIR"; // Alias textual (catálogos MEF/SUNAT)
    private static final String REG_LABORAL_SERVIR_30057 = "30057"; // INDECI_REGIMEN_LABORAL.CODIGO (Ley 30057)

    // Coincide con INDECI_REGIMEN_PENSIONARIO.TIPO (no CODIGO — ese trae la AFP).
    private static final String REG_PENS_ONP = "ONP";
    private static final String REG_PENS_AFP = "AFP";
    private static final Set<String> REG_PENS_SIN_APORTE = Set.of(
            "PENSIONISTA",
            "RETIRO",
            "AFP_RETIRO",
            "SIN_REGIMEN",
            "SIN_REGIMEN_PENSIONARIO",
            "SIN_APORTE");

    private static final BigDecimal CIEN    = new BigDecimal("100");
    private static final BigDecimal DOCE    = new BigDecimal("12");
    private static final BigDecimal SIETE   = new BigDecimal("7");
    private static final BigDecimal MEDIO   = new BigDecimal("0.5");
    // Techos de plausibilidad para tasas AFP por-empleado (fracción). Un valor por
    // encima de estos topes se considera dato mal digitado y se descarta a favor de
    // la tasa oficial de la vigencia AFP (REGLA-02). Aporte ~10%, comisión flujo <2%,
    // prima ~1.37%.
    // TODO(auditoria-tasas-afp): el techo NO detecta un valor sucio que caiga dentro
    // del rango plausible (ej. entre la tasa real y MAX). Hoy no hay ese caso (solo
    // existen 0.78 —descartado— y 1.55 —legítimo—). Al crecer el volumen de empleados
    // o AFPs, agregar validación/alerta que compare la tasa por-empleado contra la
    // oficial y marque desviaciones significativas.
    private static final BigDecimal MAX_TASA_APORTE_AFP   = new BigDecimal("0.15");
    private static final BigDecimal MAX_TASA_COMISION_AFP = new BigDecimal("0.03");
    private static final BigDecimal MAX_TASA_PRIMA_AFP    = new BigDecimal("0.05");

    /** CODIGO en INDECI_TIPO_COMISION_AFP para el esquema comisión sobre saldo. */
    private static final String TIPO_COMISION_MIXTA = "MIXTA";
    /** F1.3c — Divisor fijo del prorrateo mensual (días calendar SUNAT/MEF). */
    private static final BigDecimal TREINTA = new BigDecimal("30");

    /**
     * F1.3b — Días estándar por defecto cuando {@link EmpleadoPlanilla} no
     * tiene aún cargado el campo {@code DIAS_LABORADOS_DEFAULT} (V010_36).
     * El motor lee este valor hasta que F1.5 conecte el campo real.
     */
    private static final int DIAS_LAB_DEFAULT = 30;
    /** V012_03 — Rango válido de días laborados a persistir (guard defensivo). */
    private static final int DIAS_LAB_MIN = 0;
    private static final int DIAS_LAB_MAX = 31;

    /**
     * F1.3b — Estado de Asistencia M04 que habilita lectura de días falta.
     * Coincide con el comportamiento del PASO 7b actual.
     */
    private static final String ASISTENCIA_VALIDADA = "VALIDADA";

    private final EmpleadoPlanillaRepository planillaRepository;
    private final ConceptoPlanillaRepository conceptoRepository;
    private final MovimientoPlanillaRepository movimientoRepository;
    private final MovimientoPlanillaDetalleRepository detalleRepository;
    private final PeriodoPlanillaRepository periodoRepository;
    private final EmpleadoConceptoRepository empleadoConceptoRepository;
    private final AsistenciaCabeceraRepository asistenciaCabeceraRepository;
    // Regla de cierre "Omisión de marcación": al generar planilla se penaliza como FALTA toda
    // OMISION_MARCACION sin papeleta 004 aprobada (re-verifica al cierre, respeta el periodo de gracia).
    private final com.indeci.rrhh.repository.AsistenciaDetalleRepository asistenciaDetalleRepository;
    private final com.indeci.rrhh.service.asistencia.PapeletaJustificacionResolver papeletaJustificacionResolver;
    private final EmpleadoPensionRepository empleadoPensionRepository;
    private final EmpleadoRepository empleadoRepository;
    private final RegimenPensionarioRepository regimenPensionarioRepository;
    private final RegimenLaboralRepository regimenLaboralRepository;
    private final ParametroRemunerativoService parametroService;
    private final ConciliacionAirhspRepository conciliacionRepository;
    private final AbonoBancoRepository abonoBancoRepository;
    /** F1.4 — Reintegro por días (motor PASO 5b, infraestructura). */
    private final EmpleadoReintegroRepository empleadoReintegroRepository;
    /** F2.3 — Eventos del período (motor PASO 3, afecta días laborados). */
    private final EmpleadoEventoRepository empleadoEventoRepository;
    /** P0 maternidad — tramos mensuales del descanso subsidiado. */
    private final EventoDistribucionMesRepository eventoDistribucionMesRepository;

    /** FASE 1 — Vigencia de suspensión de retención de 4ta (motor PASO 8c). */
    private final Suspension4taService suspension4taService;

    /** V010_76 — Configuración anual IR4ta: UIT, tasa y base inafecta por año fiscal. */
    private final Ir4taConfigService ir4taConfigService;

    /** V010_93/94 — Control anual del tope de suspensión IR4ta (pendiente B2). */
    private final Ir4taControlAnualService ir4taControlAnualService;
    private final QuintaCategoriaService quintaCategoriaService;

    /** FASE 2 — Snapshot de trazabilidad (solo añadido; no afecta el cálculo). */
    private final CalculoSnapshotService calculoSnapshotService;
    private final SubsidioCalculadorService subsidioCalculadorService;
    private final com.indeci.rrhh.service.subsidio.SubsidioPlanillaIntegracionService subsidioPlanillaIntegracionService;

    /** B2 — Vigencias previsionales para trazabilidad en INDECI_MOVIMIENTO_PLANILLA. */
    private final AfpParametroVigenciaRepository afpVigenciaRepository;
    private final OnpParametroVigenciaRepository onpVigenciaRepository;
    private final TipoComisionAfpRepository tipoComisionAfpRepository;
    private final EmpleadoRemuneracionHistRepository remuneracionHistRepository;
    
    /** HU-02 - Maestro de Lotes para vincular automáticamente planillas Ordinarias. */
    private final PlanillaLoteRepository planillaLoteRepository;

    /** HU-03 - Refactor de Motor V3: Enrutador de Estrategias. */
    private final com.indeci.rrhh.service.strategy.CalculadorConceptoFactory calculadorConceptoFactory;

    /** Fase 4 - Inmutabilidad (Boleta Snapshot). */
    private final com.indeci.rrhh.service.support.MovimientoPlanillaSnapshotFactory snapshotFactory;

    /**
     * Self-reference para que {@link #generarTodoPeriodo(String)} invoque
     * {@link #generar(Long, String)} a través del proxy de Spring y cada
     * empleado ejecute con su propia transacción aislada.
     */
    @Autowired
    @Lazy
    private GeneradorPlanillaService self;

    /**
     * F1.5a — Feature flag del prorrateo Motor v3. Cuando {@code false} (default
     * en application.properties), el motor calcula exactamente como antes:
     * el reintegro de {@link #calcularReintegro} se computa pero NO se suma.
     * Cuando {@code true}, suma el reintegro al total remunerativo del empleado.
     * Configurable por entorno (typical: ON en dev, OFF en prod hasta smoke).
     */
    @Value("${motor.v3.prorrateo.enabled:false}")
    private boolean motorV3ProrrateoEnabled;
    public boolean isMotorV3ProrrateoEnabled() { return motorV3ProrrateoEnabled; }

    /**
     * FASE 4 — Calcula + registra los subsidios del período y devuelve el
     * INGRESO no remunerativo que debe sumarse al neto del trabajador.
     *
     * <p>Criterio normativo (confirmado RRHH 2026-06-06): el sueldo ya se
     * prorratea hacia abajo por los días de descanso del evento (cuando el
     * TipoEvento tiene {@code AFECTA_DIAS_LABORADOS='S'}). Por eso el
     * {@code subsidio_total_100} entra como ingreso NO remunerativo que rellena
     * ese hueco → el neto del trabajador queda ≈ mes completo, sin doble pago.</p>
     *
     * <pre>
     *  GENERA_SUBSIDIO='S' + AFECTA_DIAS_LABORADOS='S'
     *      → línea SUBSIDIO_* + suma subsidio_total_100 al neto.
     *  GENERA_SUBSIDIO='S' + AFECTA_DIAS_LABORADOS='N'
     *      → NO suma al neto (el sueldo no se redujo): solo trazabilidad.
     *        Preflight V18 alerta la posible mala configuración.
     * </pre>
     *
     * <p>El diferencial entidad (PLAME 2073) <b>nunca</b> suma al neto del
     * trabajador: es costo del empleador, ya está en la trazabilidad/snapshot.</p>
     *
     * @return ingreso de subsidio a sumar a {@code totalIngresos} (NO a las
     *         bases imponibles: es no remunerativo, no afecta IR/EsSalud/AFP).
     */
    /**
     * P0-F0: flujo por eventos retirado. Los subsidios se imputan vía liquidaciones
     * del módulo dedicado ({@code INDECI_SUBSIDIO_LIQUIDACION}, F2).
     */
    public BigDecimal registrarSubsidiosEventos(
            Long empleadoId,
            String periodo,
            MovimientoPlanilla movimiento,
            EmpleadoPlanilla planilla) {
        return registrarSubsidiosDesdeLiquidacion(empleadoId, periodo, movimiento, planilla);
    }

    /**
     * Stub F2 — consultará liquidaciones vigentes por tramo/período.
     * Legacy {@code INDECI_SUBSIDIO_EVENTO_CALCULO} queda read-only.
     */
    private BigDecimal registrarSubsidiosDesdeLiquidacion(
            Long empleadoId,
            String periodo,
            MovimientoPlanilla movimiento,
            EmpleadoPlanilla planilla) {
        // F1.10 — El motor es la fuente única de las líneas de subsidio: las graba
        // en el movimiento (ya regenerado, detalles previos borrados) y devuelve el
        // total, garantizando línea == total en la boleta de forma determinista.
        return subsidioPlanillaIntegracionService.grabarSubsidioEnMovimientoMotor(
                empleadoId, periodo, movimiento.getId());
    }

    /** Resuelve el concepto de subsidio por CODIGO interno (PLAME, no MEF). */
    private ConceptoPlanilla conceptoSubsidio(String tipoSubsidio) {
        String codigo = TIPO_SUBSIDIO_MATERNIDAD.equalsIgnoreCase(tipoSubsidio)
                ? CODIGO_SUBSIDIO_MATERNIDAD
                : CODIGO_SUBSIDIO_ENFERMEDAD;
        return conceptoRepository.findByCodigoAndActivo(codigo, 1)
                .orElseThrow(() -> new NegocioException(
                        "Concepto de subsidio '" + codigo + "' no configurado o inactivo. "
                                + "Ejecutar seed V010_58__conceptos_subsidio_plame.sql."));
    }

    // ======================================================================
    // ENTRY POINT
    // ======================================================================

    @Transactional
    public void generar(Long empleadoId, String periodo) {
        generarConOverride(empleadoId, periodo, null, null, "ORDINARIA", null, null, null, null);
    }

    @Transactional
    public void generarConOverride(Long empleadoId, String periodo, BigDecimal overrideSueldoBasico) {
        generarConOverride(empleadoId, periodo, overrideSueldoBasico, null, "ORDINARIA", null, null, null, null);
    }

    /**
     * F2 (BLOQUE 3) — Pago de un reintegro/devengado (Modelo B) en la planilla
     * adicional. Crea un movimiento con la línea del reintegro bajo el concepto
     * {@code CODIGO_MEF=00507} y aplica la matriz de afectación al 100%: ONP/AFP,
     * IR (4ta CAS / 5ta 728·SERVIR) y EsSalud 9%. La transición del reintegro a
     * PAGADO la realiza {@code PlanillaLoteService} en la misma transacción.
     */
    @Transactional
    public void generarReintegroAdicional(Long empleadoId, String periodo,
            BigDecimal montoReintegro, ConceptoPlanilla conceptoReintegro,
            Long loteId, String tipoPlanilla) {
        if (montoReintegro == null || montoReintegro.signum() <= 0) {
            return;
        }
        PeriodoPlanilla periodoPlanilla = periodoRepository.findByPeriodoAndActivo(periodo, 1)
                .orElseThrow(() -> new NegocioException("Periodo no existe"));
        if ("CERRADO".equalsIgnoreCase(periodoPlanilla.getEstado())) {
            throw new NegocioException("El periodo " + periodo + " está cerrado");
        }
        LocalDate inicioPeriodo = ParametroRemunerativoService.periodoToFechaInicio(periodo);
        LocalDate finPeriodo = inicioPeriodo.withDayOfMonth(inicioPeriodo.lengthOfMonth());
        EmpleadoPlanilla planilla = planillaRepository
                .findVinculosVigentesEnPeriodo(empleadoId, inicioPeriodo, finPeriodo)
                .stream().findFirst()
                .orElseThrow(() -> new NegocioException(
                        "El empleado no tiene un vínculo vigente en el período " + periodo));
        Optional<EmpleadoPension> pensionOpt =
                empleadoPensionRepository.findFirstByEmpleadoIdAndActivo(empleadoId, 1);
        Empleado empleado = empleadoRepository.findById(empleadoId).orElse(null);
        int anioFiscal = anioDePeriodo(periodo);
        String regimen = resolverRegimenLaboralCodigo(planilla.getRegimenLaboralId());

        borrarMovimientoAnterior(empleadoId, periodo, tipoPlanilla, null);
        MovimientoPlanilla mov = crearCabecera(
                empleadoId, periodo, tipoPlanilla, null, inicioPeriodo, finPeriodo, loteId);

        // Ingreso: reintegro 100% afecto, trazado bajo CODIGO_MEF 00507.
        grabarDetalle(mov.getId(), conceptoReintegro, montoReintegro,
                "Reintegro/devengado (afecto) — MEF " + conceptoReintegro.getCodigoMef());

        // Matriz de afectación al 100% sobre el monto del reintegro.
        BigDecimal base = montoReintegro;
        BigDecimal aportePension = BigDecimal.ZERO;
        if (pensionOpt.isPresent()) {
            aportePension = calcularAportePensionario(mov, pensionOpt.get(), base, anioFiscal);
        }
        BigDecimal ir = esRegimenCas(regimen)
                ? calcular4taCategoriaCAS(base, regimen, anioFiscal, false)
                : calcular5taCategoria(mov, planilla, base, anioFiscal);
        calcularEssaludEmpleador(mov, empleado, base, anioFiscal, regimen, empleadoId, periodo);

        BigDecimal totalDescuentos = aportePension.add(ir);
        calcularTotalesYCUC(mov, montoReintegro, totalDescuentos, montoReintegro,
                ir, aportePension, BigDecimal.ZERO);
    }

    @Transactional
    public void generarConOverride(Long empleadoId, String periodo, BigDecimal overrideSueldoBasico,
                                   Long empleadoPuestoId, String tipoPlanilla,
                                   LocalDate fechaInicioPago, LocalDate fechaFinPago, Long loteId,
                                   List<String> conceptosAdicionales) {

        // 1. Validar periodo
        PeriodoPlanilla periodoPlanilla = periodoRepository
                .findByPeriodoAndActivo(periodo, 1)
                .orElseThrow(() -> new NegocioException("Periodo no existe"));
        if ("CERRADO".equalsIgnoreCase(periodoPlanilla.getEstado())) {
            throw new NegocioException("El periodo " + periodo + " está cerrado");
        }

        // 2. Vínculo que corresponde al período (traslape), no "el más reciente".
        //    Soporta vínculos secuenciales (rotación CAS): junio→CAS cesado, julio→CAS nuevo.
        LocalDate inicioPeriodo = ParametroRemunerativoService.periodoToFechaInicio(periodo);
        LocalDate finPeriodo = inicioPeriodo.withDayOfMonth(inicioPeriodo.lengthOfMonth());
        EmpleadoPlanilla planilla = planillaRepository
                .findVinculosVigentesEnPeriodo(empleadoId, inicioPeriodo, finPeriodo)
                .stream().findFirst()
                .orElseThrow(() -> new NegocioException(
                        "El empleado no tiene un vínculo vigente en el período " + periodo));

        // 3. Pensión vigente (opcional — sin pensión, no se calcula aporte)
        Optional<EmpleadoPension> pensionOpt =
                empleadoPensionRepository.findFirstByEmpleadoIdAndActivo(empleadoId, 1);

        // 3b. Empleado (para HAS_EPS — split ESSALUD §5.5). Opcional.
        Empleado empleado = empleadoRepository.findById(empleadoId).orElse(null);

        int anioFiscal = anioDePeriodo(periodo);
        String actualTipoPlanilla = tipoPlanilla != null ? tipoPlanilla : "ORDINARIA";
        
        // ====================================================================
        // HU-02: Auto-resolver Lote si viene nulo (ej. Planilla Ordinaria)
        // ====================================================================
        Long resolvedLoteId = loteId;
        if (resolvedLoteId == null && "ORDINARIA".equals(actualTipoPlanilla)) {
            String regimen = planilla.getRegimenLaboral() != null ? planilla.getRegimenLaboral().getCodigo() : "DESC";
            PlanillaLote lote = planillaLoteRepository
                    .findByPeriodoAndRegimenLaboralCodigoAndTipoPlanillaAndCorrelativo(periodo, regimen, "ORDINARIA", 1)
                    .orElseGet(() -> {
                        PlanillaLote nuevoLote = new PlanillaLote();
                        nuevoLote.setPeriodo(periodo);
                        nuevoLote.setRegimenLaboralCodigo(regimen);
                        nuevoLote.setTipoPlanilla("ORDINARIA");
                        nuevoLote.setCorrelativo(1);
                        nuevoLote.setEstado("GENERADO");
                        return planillaLoteRepository.save(nuevoLote);
                    });
            resolvedLoteId = lote.getId();
        }

        // 4) Generación de Movimiento (Cabecera)
        borrarMovimientoAnterior(empleadoId, periodo, actualTipoPlanilla, empleadoPuestoId);
        MovimientoPlanilla movimiento = crearCabecera(empleadoId, periodo, actualTipoPlanilla, empleadoPuestoId, fechaInicioPago, fechaFinPago, resolvedLoteId);

        // 4b. FASE 2 — Trazabilidad: descartar el snapshot anterior del par
        //     empleado/período para que quede un único conjunto vigente
        //     (reproducible) tras esta regeneración.
        calculoSnapshotService.desactivarPrevios(empleadoId, periodo);

        String conceptoLote = "PLA_HABERES"; 
        if (resolvedLoteId != null) {
            PlanillaLote loteDB = planillaLoteRepository.findById(resolvedLoteId).orElse(null);
            if (loteDB != null && loteDB.getConceptoPlanilla() != null) {
                conceptoLote = loteDB.getConceptoPlanilla();
            }
        }

        com.indeci.rrhh.service.strategy.ContextoCalculoPlanilla contexto = com.indeci.rrhh.service.strategy.ContextoCalculoPlanilla.builder()
                .empleadoId(empleadoId)
                .periodo(periodo)
                .regimenLaboralCodigo(planilla.getRegimenLaboral() != null ? planilla.getRegimenLaboral().getCodigo() : "DESC")
                .anioFiscal(anioFiscal)
                .planilla(planilla)
                .empleado(empleado)
                .movimiento(movimiento)
                .periodoPlanilla(periodoPlanilla)
                .pensionOpt(pensionOpt)
                .overrideSueldoBasico(overrideSueldoBasico)
                .conceptosAdicionales(conceptosAdicionales)
                .motorLegacy(this)
                .build();

        com.indeci.rrhh.service.strategy.CalculadorConceptoStrategy estrategia = 
                calculadorConceptoFactory.obtenerEstrategia(contexto.getRegimenLaboralCodigo(), conceptoLote);
        
        estrategia.ejecutarCalculo(contexto);
    }

    // ======================================================================
    // FASE 2 — Snapshot de trazabilidad (efecto lateral, no altera cálculo)
    // ======================================================================

    /**
     * Snapshot de contexto del cálculo. Captura la base reconocida (sueldo
     * básico de la configuración de planilla), el régimen, la UIT del año y los
     * totales, de modo que la pantalla de explicación tenga el marco general.
     */
    public void registrarSnapshotGeneral(
            Long empleadoId, String periodo, Long movimientoId, int anioFiscal,
            EmpleadoPlanilla planilla, String regimenLaboralCodigo,
            BigDecimal totalIngresos, BigDecimal totalDescuentos) {

        BigDecimal baseReconocida = planilla.getSueldoBasico() == null
                ? null : BigDecimal.valueOf(planilla.getSueldoBasico());
        BigDecimal uit = parametroService
                .obtenerValorOpcional("UIT", anioFiscal, null)
                .orElse(null);
        BigDecimal neto = totalIngresos.subtract(totalDescuentos);

        calculoSnapshotService.registrar(
                CalculoSnapshotService.registro(
                                empleadoId, periodo, CalculoSnapshotService.REGLA_GENERAL)
                        .movimiento(movimientoId)
                        .base(baseReconocida)
                        .resultado(neto)
                        .version(String.valueOf(anioFiscal))
                        .formula("neto = ingresos " + escala(totalIngresos)
                                + " - descuentos " + escala(totalDescuentos))
                        .param("regimen", regimenLaboralCodigo)
                        .param("baseReconocida", baseReconocida)
                        .param("uit", uit)
                        .param("anioFiscal", anioFiscal)
                        .param("totalIngresos", totalIngresos)
                        .param("totalDescuentos", totalDescuentos));
    }

    /**
     * Snapshot de la retención IR 4ta CAS (tributo SUNAT 3042). Deja explícito
     * por qué se retuvo o no: base afecta, inafecto, tasa y estado de la
     * constancia SUNAT de suspensión.
     */
    public void registrarSnapshotIr4ta(
            Long empleadoId, String periodo, Long movimientoId, int anioFiscal,
            BigDecimal baseImponible, BigDecimal baseAfecta, BigDecimal resultado,
            Suspension4taVigenteDto suspension) {

        BigDecimal baseInafecta = parametroService
                .obtenerValorOpcional("BASE_INAFECTA_IR4TA", anioFiscal, null)
                .orElse(null);
        BigDecimal tasa = parametroService
                .obtenerValorOpcional("TASA_IR4TA", anioFiscal, null)
                .orElse(null);
        boolean suspendido = suspension != null && suspension.vigente();

        String formula;
        if (suspendido) {
            formula = "Suspensión SUNAT vigente → retención 0";
        } else if (baseInafecta != null && baseAfecta.compareTo(baseInafecta) <= 0) {
            formula = "base " + escala(baseAfecta) + " ≤ inafecto "
                    + escala(baseInafecta) + " → inafecto, retención 0";
        } else {
            formula = "(" + escala(baseAfecta) + ") × "
                    + (tasa == null ? "8%" : tasa) + " = " + escala(resultado);
        }

        calculoSnapshotService.registrar(
                CalculoSnapshotService.registro(
                                empleadoId, periodo, CalculoSnapshotService.REGLA_IR4TA_CAS)
                        .movimiento(movimientoId)
                        .base(baseAfecta)
                        .resultado(resultado)
                        .version(String.valueOf(anioFiscal))
                        .formula(formula)
                        .param("tributoSunat", "3042")
                        .param("baseImponible", baseImponible)
                        .param("baseAfecta", baseAfecta)
                        .param("baseInafecta", baseInafecta)
                        .param("tasa", tasa)
                        .param("suspensionVigente", suspendido)
                        .param("nroConstancia",
                                suspension == null ? null : suspension.nroConstancia()));
    }

    private static String escala(BigDecimal v) {
        return v == null ? "0.00" : v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** Motivo del prorrateo del haber para la línea trazable (subsidio / LSG / cese-alta). */
    private static String motivoProrrateo(int diasSubsidio, int diasLicencia) {
        StringBuilder sb = new StringBuilder(" (");
        boolean any = false;
        if (diasSubsidio > 0) {
            sb.append("subsidio ").append(diasSubsidio).append(" d");
            any = true;
        }
        if (diasLicencia > 0) {
            if (any) sb.append(", ");
            sb.append("licencia sin goce ").append(diasLicencia).append(" d");
            any = true;
        }
        if (!any) {
            sb.append("cese/alta");
        }
        return sb.append(")").toString();
    }

    // ======================================================================
    // PASO 5 — REMUNERATIVOS automáticos (asignación familiar)
    // ======================================================================

    public RemunerativosResult calcularRemunerativos(
            MovimientoPlanilla movimiento,
            EmpleadoPlanilla planilla,
            Empleado empleado,
            int anioFiscal,
            BigDecimal overrideSueldoBasico) {

        RemunerativosResult result = new RemunerativosResult();

        String regimenCodigo = planilla.getRegimenLaboralId() != null
                ? resolverRegimenLaboralCodigo(planilla.getRegimenLaboralId())
                : null;

        // Remuneración BASE desde Configuración de planilla (mejora 2026-06-03):
        // la base es EmpleadoPlanilla.sueldoBasico, NO un concepto manual. Se graba
        // con el concepto base del régimen (CAS/728/276/SERVIR) → línea trazable.
        BigDecimal sueldoBasico = overrideSueldoBasico != null
                ? overrideSueldoBasico
                : resolverBaseRemunerativa(planilla, movimiento.getPeriodo());

        // Fase 3 / F1.8 — prorrateo del haber del cargo por días EFECTIVOS =
        // días de vínculo (cese/alta intra-mes) − días de subsidio. Los días bajo
        // subsidio los cubre EsSalud; el empleador NO paga remuneración ordinaria
        // por ellos (regla Contraloría, divisor 1/30). La reducción por subsidio
        // es OBLIGATORIA e INCONDICIONAL (sin feature flag); el módulo de subsidios
        // la provee vía contrato diasSubsidioMotor. La asignación familiar
        // (beneficio fijo, D.S. 035-90-TR) se paga completa, no se prorratea.
        // Cero regresión: sin subsidio ni licencia diasHaber=diasVinc.
        // SPEC_VACACIONES F9.1 — los días de licencia SIN GOCE (y suspensión no
        // subsidiada) tampoco se remuneran: reducen el haber igual que el subsidio.
        int diasVinc = diasVinculoEnPeriodo(planilla, movimiento.getPeriodo());
        int diasSubsidio = subsidioPlanillaIntegracionService
                .diasSubsidioMotor(planilla.getEmpleadoId(), movimiento.getPeriodo());
        int diasLicencia = sumarDiasEventosNoSubsidiados(
                planilla.getEmpleadoId(), movimiento.getPeriodo());
        int diasHaber = Math.max(0, diasVinc - diasSubsidio - diasLicencia);
        boolean prorrateado = diasHaber < DIAS_LAB_DEFAULT;
        sueldoBasico = prorratear(sueldoBasico, diasHaber);

        if (sueldoBasico.signum() > 0 && regimenCodigo != null) {
            String baseMef = baseMefDeRegimen(regimenCodigo, planilla.getGrupoServidorCivil());
            if (baseMef != null) {
                ConceptoPlanilla conceptoBase = conceptoPorMef(baseMef);
                String desc = "Remuneración base (" + regimenCodigo + ")"
                        + (prorrateado
                            ? " — prorrateada " + diasHaber + "/30 días"
                                + motivoProrrateo(diasSubsidio, diasLicencia)
                            : "");
                grabarDetalle(movimiento.getId(), conceptoBase, sueldoBasico, desc);
                acumular(result, conceptoBase, sueldoBasico);
            }
        }

        // Asignación familiar (régimen 728 o CAS)
        if (planilla.getTieneAsignacionFamiliar() != null
                && planilla.getTieneAsignacionFamiliar() == 1
                && regimenCodigo != null) {

            String mefAsig = null;
            if (REG_LABORAL_728.equals(regimenCodigo)) mefAsig = MEF_ASIG_FAMILIAR_728;
            if (esRegimenCas(regimenCodigo)) mefAsig = MEF_ASIG_FAMILIAR_CAS;

            if (mefAsig != null) {
                BigDecimal monto = parametroService.obtenerValor(
                        "ASIG_FAMILIAR", anioFiscal, null);
                ConceptoPlanilla concepto = conceptoPorMef(mefAsig);
                grabarDetalle(movimiento.getId(), concepto, monto,
                        "Asignación familiar (" + regimenCodigo + ")");
                acumular(result, concepto, monto);
            }
        }

        // Otros ingresos remunerativos (incrementos DS, bonificaciones, etc.) y
        // descuentos vienen de EmpleadoConcepto en aplicarConceptosManuales, que
        // ahora IGNORA los conceptos base (MEF_BASE_REMUNERATIVA) para no duplicar.
        return result;
    }

    /**
     * CODIGO_MEF de la remuneración base del régimen. SERVIR resuelve por subgrupo
     * (INDECI_TIPO_PERSONAL.CODIGO del empleado) con fallback L003. {@code null}
     * si el régimen no tiene concepto base definido.
     */
    private String baseMefDeRegimen(String regimenCodigo, String grupoServidorCivil) {
        if (esRegimenCas(regimenCodigo)) return MEF_BASE_CAS;
        if (REG_LABORAL_728.equals(regimenCodigo)) return MEF_BASE_728;
        if ("276".equals(regimenCodigo)) return MEF_BASE_276;
        if (esRegimenServir(regimenCodigo)) {
            // F2 — el grupo del servidor civil vive en EMPLEADO_PLANILLA.GRUPO_SERVIDOR_CIVIL
            // (dato que registra RR.HH.), NO en persona.tipo_persona_id. Se normaliza para
            // tolerar espacios/caso. Sin grupo o grupo desconocido → fallback L003 (decisión
            // RR.HH.). Guard adicional: SERVIR_SUBGRUPO_BASE_MEF es Map.of (rechaza null).
            String grupo = grupoServidorCivil != null ? grupoServidorCivil.trim().toUpperCase() : null;
            return grupo != null
                    ? SERVIR_SUBGRUPO_BASE_MEF.getOrDefault(grupo, MEF_BASE_SERVIR_FALLBACK)
                    : MEF_BASE_SERVIR_FALLBACK;
        }
        return null;
    }

    private void acumular(RemunerativosResult result, ConceptoPlanilla concepto, BigDecimal monto) {
        result.totalRemunerativo = result.totalRemunerativo.add(monto);
        if ("S".equalsIgnoreCase(concepto.getAfectoAportePens())) {
            result.baseAportePension = result.baseAportePension.add(monto);
        }
        if ("S".equalsIgnoreCase(concepto.getAfectoEssalud())) {
            result.baseEssalud = result.baseEssalud.add(monto);
        }
    }

    // ======================================================================
    // PASO 7 — CONCEPTOS MANUALES (con validación LEY-01)
    // ======================================================================

    public ManualesResult aplicarConceptosManuales(
            MovimientoPlanilla movimiento,
            EmpleadoPlanilla planilla,
            String regimenLaboralCodigo,
            String periodo) {

        ManualesResult result = new ManualesResult();
        BigDecimal sueldoBasico = resolverBaseRemunerativa(planilla, periodo);

        // F1.5b — días laborados se calculan UNA VEZ por empleado/período;
        // se reusan en cada EmpleadoConcepto prorrateable. Solo se calcula
        // si el flag está ON (sino no se usa).
        int diasLab = motorV3ProrrateoEnabled
                ? calcularDiasLaborados(planilla.getEmpleadoId(), periodo)
                : DIAS_LAB_DEFAULT;

        // F1.8 — días de subsidio para la reducción INCONDICIONAL de conceptos
        // prorrateables cuando el flag legacy motorV3 está OFF (con el flag ON ya
        // vienen incluidos dentro de diasLab vía calcularDiasLaborados → DRY).
        int diasSubsidioManual = motorV3ProrrateoEnabled
                ? 0
                : subsidioPlanillaIntegracionService.diasSubsidioMotor(
                        planilla.getEmpleadoId(), periodo);

        List<EmpleadoConcepto> conceptos =
                empleadoConceptoRepository.findByEmpleadoIdAndActivo(
                        planilla.getEmpleadoId(), 1);

        for (EmpleadoConcepto ec : conceptos) {
            ConceptoPlanilla concepto = conceptoRepository
                    .findById(ec.getConceptoPlanillaId())
                    .orElseThrow(() -> new NegocioException(
                            "Concepto no existe: id=" + ec.getConceptoPlanillaId()));

            // LEY-01 (Ley 32448) — todo concepto pagable necesita CODIGO_MEF
            if (concepto.getCodigoMef() == null || concepto.getCodigoMef().isBlank()) {
                throw new ConceptoSinCodigoMefException(concepto.getId(), concepto.getNombre());
            }

            // El motor calcula estos conceptos automáticamente (aportes, ESSALUD,
            // 5ta, asig. familiar). Un EmpleadoConcepto manual con ese CODIGO_MEF
            // es dato legacy del sistema anterior — se ignora para no duplicar.
            if (MEF_AUTOCALCULADOS.contains(concepto.getCodigoMef())) {
                continue;
            }

            // Mejora 2026-06-03 — los conceptos de remuneración base los calcula
            // el motor desde sueldoBasico (PASO 5); un EmpleadoConcepto manual con
            // ese MEF es dato legacy → se ignora para no duplicar la base.
            if (MEF_BASE_REMUNERATIVA.contains(concepto.getCodigoMef())) {
                continue;
            }

            // F1.5b — Guard normativo: si el flag está ON, verificar que el
            // concepto aplique al régimen del empleado. Ej: pagar DS 327
            // (REGIMEN_APLICABLE='728,1057') a un empleado del régimen 276 es
            // un pago indebido — lanza excepción que bloquea generar().
            if (motorV3ProrrateoEnabled
                    && !regimenAplicaConcepto(concepto.getRegimenAplicable(), regimenLaboralCodigo)) {
                throw new ConceptoRegimenNoAplicableException(
                        concepto.getCodigoMef(),
                        concepto.getNombre(),
                        regimenLaboralCodigo,
                        concepto.getRegimenAplicable());
            }

            BigDecimal monto = calcularMontoEmpleadoConcepto(ec, sueldoBasico);

            // F1.5b / F1.8 — Prorrateo de conceptos manuales prorrateables: el
            // prorrateo por faltas/eventos sigue tras el flag motorV3, pero la
            // reducción por SUBSIDIO es OBLIGATORIA e incondicional (Contraloría).
            if ("S".equalsIgnoreCase(concepto.getEsProrrateable())) {
                int diasProrrateo = motorV3ProrrateoEnabled
                        ? diasLab
                        : Math.max(0, DIAS_LAB_DEFAULT - diasSubsidioManual);
                if (diasProrrateo < DIAS_LAB_DEFAULT) {
                    monto = prorratear(monto, diasProrrateo);
                }
            }

            grabarDetalle(movimiento.getId(), concepto, monto, concepto.getNombre());

            String tipo = resolverTipoConcepto(concepto);
            switch (tipo) {
                case "REMUNERATIVO" -> {
                    result.ingresos = result.ingresos.add(monto);
                    result.ingresosMensualesPermanentes =
                            result.ingresosMensualesPermanentes.add(monto);
                    if ("S".equalsIgnoreCase(concepto.getAfectoAportePens())) {
                        result.baseAportePension = result.baseAportePension.add(monto);
                    }
                    if ("S".equalsIgnoreCase(concepto.getAfectoEssalud())) {
                        result.baseEssalud = result.baseEssalud.add(monto);
                    }
                }
                case "NO_REMUNERATIVO" -> {
                    result.ingresos = result.ingresos.add(monto);
                    if ("S".equalsIgnoreCase(concepto.getAfectoAportePens())) {
                        result.baseAportePension = result.baseAportePension.add(monto);
                    }
                    if ("S".equalsIgnoreCase(concepto.getAfectoEssalud())) {
                        result.baseEssalud = result.baseEssalud.add(monto);
                    }
                }
                case "DESCUENTO", "APORTE_TRABAJADOR", "DESCUENTO_JUDICIAL" -> {
                    result.descuentos = result.descuentos.add(monto);
                    if (esDescuentoJudicial(concepto)) {
                        result.descuentoJudicial = result.descuentoJudicial.add(monto);
                    }
                }
                case "APORTE_EMPLEADOR" -> {
                    // LEY-02: aporte empleador nunca descuenta al trabajador.
                    // Si llega aquí vía EmpleadoConcepto manual, lo respetamos
                    // como informativo (ya está grabado en el detalle).
                }
                default -> {
                    // Tipo desconocido: tratar como ingreso por seguridad
                    result.ingresos = result.ingresos.add(monto);
                }
            }
        }

        return result;
    }

    private BigDecimal calcularMontoEmpleadoConcepto(EmpleadoConcepto ec, BigDecimal sueldoBasico) {
        if (ec.getMonto() != null) {
            return BigDecimal.valueOf(ec.getMonto());
        }
        if (ec.getPorcentaje() != null && sueldoBasico.signum() > 0) {
            return sueldoBasico
                    .multiply(BigDecimal.valueOf(ec.getPorcentaje()))
                    .divide(CIEN, 6, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    /**
     * Resuelve el tipo del concepto. Prioriza el nuevo TIPO_CONCEPTO; si está
     * vacío hace fallback al TIPO legacy (INGRESO/DESCUENTO).
     */
    private String resolverTipoConcepto(ConceptoPlanilla concepto) {
        if (concepto.getTipoConcepto() != null && !concepto.getTipoConcepto().isBlank()) {
            return concepto.getTipoConcepto().toUpperCase();
        }
        String legacy = concepto.getTipo();
        if (legacy == null) return "REMUNERATIVO";
        return switch (legacy.toUpperCase()) {
            case "INGRESO"   -> "REMUNERATIVO";
            case "DESCUENTO" -> "DESCUENTO";
            case "APORTE"    -> "APORTE_TRABAJADOR";
            default          -> "REMUNERATIVO";
        };
    }

    private boolean esDescuentoJudicial(ConceptoPlanilla concepto) {
        return CODIGO_INTERNO_DESC_JUDICIAL.equalsIgnoreCase(concepto.getCodigo())
                || TIPO_CONCEPTO_DESC_JUDICIAL.equalsIgnoreCase(concepto.getTipoConcepto())
                || SISPER_DESC_JUDICIAL.equals(concepto.getCodigoSisper());
    }

    // ======================================================================
    // PASO 7b — DESCUENTO DE ASISTENCIA (tardanzas + faltas — D.Leg. 276 Art. 24)
    // ======================================================================

    /**
     * Aplica el descuento por tardanzas y faltas tomado de
     * {@code INDECI_ASISTENCIA_CABECERA} (M04 / SPEC §12.2 PANTALLA-02).
     *
     * <p>SOLO se consume la asistencia en estado {@code VALIDADA}: la
     * {@code BORRADOR} es trabajo en curso del operador y se ignora. Si no hay
     * asistencia para el empleado/período, el descuento es {@code ZERO}.
     *
     * <p>Los montos ({@code descuentoTardanza} / {@code descuentoFalta}) ya
     * vienen calculados por {@code AsistenciaService} con la fórmula REGLA
     * 276-02; el motor solo los graba como detalle y los suma a los descuentos.
     * No reducen la base imponible de aportes ni el umbral del neto 50%.
     *
     * @return suma de los descuentos de asistencia (descuento al trabajador).
     */
    public BigDecimal calcularDescuentoAsistencia(
            MovimientoPlanilla movimiento, Long empleadoId, String periodo) {

        Optional<AsistenciaCabecera> asistOpt = asistenciaCabeceraRepository
                .findByEmpleadoIdAndPeriodoAndActivo(empleadoId, periodo, 1);
        if (asistOpt.isEmpty()) {
            return BigDecimal.ZERO;
        }

        AsistenciaCabecera asist = asistOpt.get();
        if (!"VALIDADA".equalsIgnoreCase(asist.getEstado())) {
            return BigDecimal.ZERO; // asistencia en BORRADOR — no alimenta el motor
        }

        BigDecimal total = BigDecimal.ZERO;

        BigDecimal descTardanza = toBigDecimal(asist.getDescuentoTardanza());
        if (descTardanza.signum() > 0) {
            grabarDetalle(movimiento.getId(), conceptoPorMef(MEF_DESC_TARDANZA), descTardanza,
                    "Descuento por tardanza ("
                            + (asist.getTotalMinTardanza() != null ? asist.getTotalMinTardanza() : 0)
                            + " min — D.Leg. 276 Art. 24)");
            total = total.add(descTardanza);
        }

        BigDecimal descFalta = toBigDecimal(asist.getDescuentoFalta());
        if (descFalta.signum() > 0) {
            grabarDetalle(movimiento.getId(), conceptoPorMef(MEF_DESC_FALTA), descFalta,
                    "Descuento por faltas ("
                            + (asist.getDiasFalta() != null ? asist.getDiasFalta() : 0)
                            + " día(s) — D.Leg. 276 Art. 24)");
            total = total.add(descFalta);
        }

        // Cierre de la "Omisión de marcación": recién aquí (generación de planilla) toda
        // OMISION_MARCACION sin papeleta 004 aprobada se penaliza como FALTA (base/30 por día).
        // Se re-verifica la papeleta al cierre, respetando el periodo de gracia. Aditivo: si no
        // hay días de omisión (dato preexistente), no cambia nada.
        BigDecimal descOmision = calcularDescuentoOmisionCierre(asist, empleadoId, periodo);
        if (descOmision.signum() > 0) {
            grabarDetalle(movimiento.getId(), conceptoPorMef(MEF_DESC_FALTA), descOmision,
                    "Falta por omisión de marcación no justificada al cierre (sin papeleta 004)");
            total = total.add(descOmision);
        }

        return total;
    }

    /**
     * Regla de cierre: cuenta los días {@code OMISION_MARCACION} de la asistencia activa que, al
     * momento de generar la planilla, NO están cubiertos por una papeleta 004 aprobada, y devuelve
     * su descuento como falta (remuneración/30 por día). Si hay papeleta, el día ya fue convertido
     * a ASISTENCIA_JUSTIFICADA en la carga y no llega aquí como omisión.
     */
    private BigDecimal calcularDescuentoOmisionCierre(
            AsistenciaCabecera asist, Long empleadoId, String periodo) {
        if (asist.getId() == null) {
            return BigDecimal.ZERO;
        }
        List<AsistenciaDetalle> omisiones = asistenciaDetalleRepository
                .findByCabeceraIdOrderByDia(asist.getId()).stream()
                .filter(d -> "OMISION_MARCACION".equals(d.getTipoDia()))
                .toList();
        if (omisiones.isEmpty()) {
            return BigDecimal.ZERO;
        }
        LocalDate finPeriodo = periodoRepository.findByPeriodoAndActivo(periodo, 1)
                .map(PeriodoPlanilla::getFechaFin)
                .orElse(null);
        List<com.indeci.rrhh.entity.SolicitudRrhh> justificantes =
                papeletaJustificacionResolver.cargarJustificantes(empleadoId, finPeriodo);
        long noJustificadas = omisiones.stream()
                .filter(d -> !papeletaJustificacionResolver.omisionJustificada(d.getDia(), justificantes))
                .count();
        if (noJustificadas == 0) {
            return BigDecimal.ZERO;
        }
        double base = asist.getRemuneracionBase() != null ? asist.getRemuneracionBase() : 0.0;
        return toBigDecimal(com.indeci.rrhh.service.asistencia.AsistenciaResumenCalculator
                .calcularDescuentoFalta(base, (int) noJustificadas));
    }

    /**
     * INTEGRACIÓN FINAL: Busca la planilla AGUINALDO del empleado en el periodo
     * y copia sus detalles de ingreso al movimiento actual. Retorna la suma de los
     * ingresos consolidados para sumarlos al totalIngresos.
     */
    public BigDecimal consolidarAguinaldo(Long empleadoId, String periodo, Long movimientoId) {
        BigDecimal ingresosAguinaldo = BigDecimal.ZERO;
        
        MovimientoPlanilla movAguinaldo = movimientoRepository
                .findAllByEmpleadoIdAndPeriodoAndActivo(empleadoId, periodo, 1)
                .stream()
                .filter(m -> "AGUINALDO".equals(m.getTipoPlanilla()))
                .findFirst()
                .orElse(null);

        if (movAguinaldo != null) {
            List<MovimientoPlanillaDetalle> detalles = detalleRepository.findByMovimientoPlanillaId(movAguinaldo.getId());
            for (MovimientoPlanillaDetalle det : detalles) {
                if (det.getMonto() != null && det.getMonto() > 0 && det.getConceptoPlanillaId() != null) {
                    ConceptoPlanilla concepto = conceptoRepository.findById(det.getConceptoPlanillaId()).orElse(null);
                    if (concepto != null && !"DESCUENTO".equals(concepto.getTipo())) {
                        grabarDetalle(movimientoId, concepto, 
                                BigDecimal.valueOf(det.getMonto()), det.getObservacion() + " (Consolidado)");
                        ingresosAguinaldo = ingresosAguinaldo.add(BigDecimal.valueOf(det.getMonto()));
                    }
                }
            }
        }
        
        return ingresosAguinaldo;
    }

    // ======================================================================
    // PASO 8 — APORTE PENSIONARIO (ONP / AFP)
    // ======================================================================

    public BigDecimal calcularAportePensionario(
            MovimientoPlanilla movimiento,
            EmpleadoPension pension,
            BigDecimal baseImponible,
            int anioFiscal) {

        if (baseImponible.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        if (pension.getRegimenPensionarioId() == null) {
            return BigDecimal.ZERO;
        }

        String tipoRegimen = resolverRegimenPensionarioTipo(
                pension.getRegimenPensionarioId());

        if (esRegimenPensionarioSinAporte(tipoRegimen)) {
            return BigDecimal.ZERO;
        }

        BigDecimal totalAporte = BigDecimal.ZERO;

        if (REG_PENS_ONP.equalsIgnoreCase(tipoRegimen)) {
            // B2 — registrar la vigencia ONP usada para trazabilidad
            onpVigenciaRepository.findVigenteByPeriodo(movimiento.getPeriodo())
                    .ifPresent(v -> movimiento.setOnpParamVigenciaId(v.getId()));

            BigDecimal tasa = primeraTasaNoNula(
                    pension.getPorcentajeAporte(),
                    () -> parametroService.obtenerValor("TASA_ONP", anioFiscal, null));
            BigDecimal monto = baseImponible.multiply(tasa).setScale(2, RoundingMode.HALF_UP);
            grabarDetalle(movimiento.getId(), conceptoPorMef(MEF_APORTE_ONP), monto,
                    "Aporte ONP " + porcentajeTexto(tasa));
            totalAporte = totalAporte.add(monto);

        } else if (REG_PENS_AFP.equalsIgnoreCase(tipoRegimen)) {
            // Condición especial: RETIRO_955 / PENSIONISTA_SPP → el trabajador
            // ya retiró su fondo o es pensionista SPP; no corresponde descuento AFP.
            String condicionAfp = pension.getCondicionEspecialAfp();
            if ("RETIRO_955".equals(condicionAfp) || "PENSIONISTA_SPP".equals(condicionAfp)) {
                return BigDecimal.ZERO;
            }

            // REGLA-02 — Fuente oficial de tasas: la vigencia AFP del período
            // (INDECI_AFP_PARAMETRO_VIGENCIA, cargada por SBS). El campo por-empleado
            // solo se respeta como override si es plausible (>0 y ≤ techo); un valor
            // fuera de rango (ej. comisión mal digitada 78%) se descarta y se usa la
            // tasa oficial, evitando descuentos descomunales y netos negativos.
            AfpParametroVigencia vig = afpVigenciaRepository.findVigenteByAfpAndPeriodo(
                    pension.getRegimenPensionarioId(), movimiento.getPeriodo())
                    .orElse(null);
            if (vig != null) {
                movimiento.setAfpParamVigenciaId(vig.getId());
            }

            // Aporte obligatorio al fondo (≈10%).
            BigDecimal tasaAporte = tasaAfpConOverride(
                    pension.getPorcentajeAporte(),
                    vig != null ? vig.getAporteObligatorioPct() : null,
                    () -> parametroService.obtenerValor("TASA_AFP_APORTE", anioFiscal, null),
                    MAX_TASA_APORTE_AFP);
            BigDecimal aporte = baseImponible.multiply(tasaAporte).setScale(2, RoundingMode.HALF_UP);
            grabarDetalle(movimiento.getId(), conceptoPorMef(MEF_APORTE_AFP), aporte,
                    "Aporte AFP " + porcentajeTexto(tasaAporte));
            totalAporte = totalAporte.add(aporte);

            // Advertencia de dato sucio: comisión por-empleado presente pero fuera de
            // rango (ej. 0.78 = 78% mal digitado). No rompe el cálculo —se descarta a
            // favor de la tasa oficial— pero se registra para auditoría/limpieza.
            BigDecimal comisionEmpleado = normalizarTasaEmpleado(pension.getPorcentajeComision());
            if (comisionEmpleado != null && comisionEmpleado.signum() > 0
                    && comisionEmpleado.compareTo(MAX_TASA_COMISION_AFP) > 0) {
                log.warn("Comisión AFP por-empleado fuera de rango ({}) para EMPLEADO_ID={} "
                        + "PERIODO={}. Se descarta y se usa la tasa oficial de la vigencia.",
                        comisionEmpleado, pension.getEmpleadoId(), movimiento.getPeriodo());
            }

            // Comisión sobre flujo: varía por AFP (§16.3). Oficial desde la vigencia.
            // En esquema MIXTA (comisión sobre saldo) el flujo mensual es 0%.
            boolean esMixta = esComisionMixta(pension.getTipoComisionAfpId());
            BigDecimal tasaComision = tasaComisionFlujo(
                    esMixta,
                    pension.getPorcentajeComision(),
                    vig != null ? vig.getComisionFlujoPct() : null,
                    () -> BigDecimal.ZERO,
                    MAX_TASA_COMISION_AFP);
            if (tasaComision.signum() > 0) {
                BigDecimal monto = baseImponible.multiply(tasaComision)
                        .setScale(2, RoundingMode.HALF_UP);
                grabarDetalle(movimiento.getId(), conceptoPorMef(MEF_COMISION_AFP), monto,
                        "Comisión AFP " + porcentajeTexto(tasaComision));
                totalAporte = totalAporte.add(monto);
            }

            // Prima de seguro AFP CON TOPE (SPEC §5.6):
            //   base  = MIN(baseImponible, tope) — tope = remuneración máxima
            //           asegurable de la vigencia (o TOPE_SEGURO_AFP como respaldo).
            //   prima = tasa oficial de la vigencia (o PRIMA_AFP global de respaldo).
            BigDecimal tasaPrima = tasaAfpConOverride(
                    pension.getPorcentajeSeguro(),
                    vig != null ? vig.getPrimaSeguroPct() : null,
                    () -> parametroService.obtenerValor("PRIMA_AFP", anioFiscal, null),
                    MAX_TASA_PRIMA_AFP);

            // Validacion automatica de edad 65 años
            boolean esMayor65 = false;
            Empleado empleado = empleadoRepository.findById(movimiento.getEmpleadoId()).orElse(null);
            if (empleado != null && empleado.getPersona() != null) {
                java.util.Date fechaNac = empleado.getPersona().getFechaNacimiento();
                if (fechaNac != null) {
                    java.time.LocalDate fn = java.time.Instant.ofEpochMilli(fechaNac.getTime())
                            .atZone(java.time.ZoneId.systemDefault()).toLocalDate();
                    int mes = mesDePeriodo(movimiento.getPeriodo());
                    java.time.LocalDate finMes = java.time.LocalDate.of(anioFiscal, mes, 1).plusMonths(1).minusDays(1);
                    int edad = java.time.Period.between(fn, finMes).getYears();
                    if (edad >= 65) {
                        esMayor65 = true;
                        log.info("Exonerando Prima Seguro AFP automáticamente por edad >= 65. Empleado ID={}", empleado.getId());
                    }
                }
            }

            if (tasaPrima.signum() > 0 && !esMayor65) {
                BigDecimal tope = (vig != null && vig.getRemuneracionMaximaAseg() != null)
                        ? vig.getRemuneracionMaximaAseg()
                        : parametroService.obtenerValor("TOPE_SEGURO_AFP", anioFiscal, null);
                BigDecimal baseSeguro = baseImponible.min(tope);
                BigDecimal montoSeguro = baseSeguro.multiply(tasaPrima)
                        .setScale(2, RoundingMode.HALF_UP);
                boolean conTope = baseImponible.compareTo(tope) > 0;
                grabarDetalle(movimiento.getId(), conceptoPorMef(MEF_SEGURO_AFP), montoSeguro,
                        "Prima seguro AFP " + porcentajeTexto(tasaPrima)
                                + (conTope ? " (base topada)" : ""));
                totalAporte = totalAporte.add(montoSeguro);
            }
        }

        return totalAporte;
    }

    private boolean esRegimenPensionarioSinAporte(String tipoRegimen) {
        return REG_PENS_SIN_APORTE.contains(normalizarTipoPensionario(tipoRegimen));
    }

    // ======================================================================
    // PASO 8b — RETENCIÓN 5TA CATEGORÍA (BW — SPEC §5.7 / LEY-03)
    // ======================================================================

    /**
     * FASE 3 — Retención IR 5ta categoría según el método del Art. 40 del
     * Reglamento de la LIR (D.S. 122-94-EF). LEY-03: aplica SOLO a 728 y SERVIR
     * (276 y CAS → ZERO siempre).
     *
     * <p>Pasos (validado vs Excel caso AGUIRRE = 1 589.57):</p>
     * <ol>
     *   <li>Renta bruta anual = Σ(remuneraciones ya percibidas en el año) +
     *       remun_mensual × (12 − mes + 1) + aguinaldos futuros + otras 5ta.</li>
     *   <li>Renta neta = MAX(0, bruta − 7×UIT) → escala progresiva del TUO LIR.</li>
     *   <li>Saldo por retener = impuesto_anual − Σ(retenciones 5ta ya efectuadas).</li>
     *   <li>Retención del mes = MAX(0, ROUND(saldo / divisor_del_mes, 2)).</li>
     * </ol>
     *
     * <p>El histórico (percibido + retenido) se acumula de los
     * {@link MovimientoPlanilla} previos del MISMO año fiscal. Los aguinaldos
     * (FP si mes≤7, Navidad si mes≤12) se proyectan SOLO si
     * {@code IR5TA_INCLUYE_AGUINALDO} está activo para el régimen; lo ya
     * percibido entra por el histórico (no se duplica). Montos por parámetro.</p>
     *
     * @return la retención del mes (descuento al trabajador); ZERO si no aplica.
     */
    public BigDecimal calcular5taCategoria(
            MovimientoPlanilla movimiento,
            EmpleadoPlanilla planilla,
            BigDecimal baseRemuneracion,
            int anioFiscal) {

        if (baseRemuneracion.signum() <= 0 || planilla.getRegimenLaboralId() == null) {
            return BigDecimal.ZERO;
        }

        // LEY-03 — 5ta categoría aplica únicamente a 728 y SERVIR.
        String regimen = resolverRegimenLaboralCodigo(planilla.getRegimenLaboralId());
        if (!REG_LABORAL_728.equals(regimen) && !esRegimenServir(regimen)) {
            return BigDecimal.ZERO;
        }

        int mes = mesDePeriodo(movimiento.getPeriodo());

        // FASE 2 - Usar el servicio centralizado (QuintaCategoriaService)
        com.indeci.rrhh.dto.CalculoRentaRequestDto req = new com.indeci.rrhh.dto.CalculoRentaRequestDto();
        req.setEmpleadoId(movimiento.getEmpleadoId());
        req.setAnioFiscal(anioFiscal);
        req.setMes(mes);
        req.setIngresosMesActual(baseRemuneracion);

        com.indeci.rrhh.dto.CalculoRentaResponseDto resp = quintaCategoriaService.calcular(req);
        BigDecimal retencionMes = resp.getRetencionCalculada() != null ? resp.getRetencionCalculada() : BigDecimal.ZERO;

        // 4. Línea trazable (solo si retiene) + snapshot IR5TA (siempre que aplica).
        if (retencionMes.signum() > 0) {
            grabarDetalle(movimiento.getId(), conceptoPorMef(MEF_RETENCION_5TA), retencionMes,
                    "Retención IR 5ta | obs: " + resp.getObservacion());
        }
        
        // Snapshot simplificado (para mantener la firma y trazabilidad)
        BigDecimal uit = parametroService.obtenerValor("UIT", anioFiscal, null);
        BigDecimal brutaAnual = baseRemuneracion.multiply(BigDecimal.valueOf(12L));
        Historico5ta hist = new Historico5ta(BigDecimal.ZERO, BigDecimal.ZERO);
        Ir5taResultado r = new Ir5taResultado(BigDecimal.ZERO, BigDecimal.ZERO, retencionMes, BigDecimal.ONE, retencionMes);

        registrarSnapshotIr5ta(movimiento.getEmpleadoId(), movimiento.getPeriodo(),
                movimiento.getId(), anioFiscal, mes, uit, brutaAnual, BigDecimal.ZERO,
                BigDecimal.ZERO, hist, r);

        return retencionMes;
    }

    /**
     * Núcleo del Art. 40 (sin efectos): a partir de la renta bruta anual y el
     * retenido acumulado, calcula neta, impuesto anual, saldo y la retención del
     * mes con el divisor del mes. Package-private para test directo del caso
     * maestro AGUIRRE.
     */
    Ir5taResultado calcularRetencion5taArt40(
            BigDecimal brutaAnual, BigDecimal retenidoAcum, int mes,
            BigDecimal uit, int anioFiscal) {

        BigDecimal retenido = retenidoAcum == null ? BigDecimal.ZERO : retenidoAcum;
        BigDecimal rentaNeta = brutaAnual.subtract(uit.multiply(SIETE));
        if (rentaNeta.signum() <= 0) {
            // Bajo las 7 UIT — exonerado. Divisor informativo del mes.
            return new Ir5taResultado(
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    divisorDelMes(mes, anioFiscal), BigDecimal.ZERO);
        }
        BigDecimal impuestoAnual = aplicarEscalaProgresiva5ta(rentaNeta, uit, anioFiscal);
        BigDecimal saldo = impuestoAnual.subtract(retenido);
        BigDecimal divisor = divisorDelMes(mes, anioFiscal);
        BigDecimal retencionMes = saldo.divide(divisor, 2, RoundingMode.HALF_UP);
        if (retencionMes.signum() <= 0) {
            retencionMes = BigDecimal.ZERO;
        }
        return new Ir5taResultado(rentaNeta, impuestoAnual, saldo, divisor, retencionMes);
    }

    /** Divisor del Art. 40 para el mes; fallback a 12 (comportamiento previo) si falta el seed. */
    private BigDecimal divisorDelMes(int mes, int anioFiscal) {
        BigDecimal divisor = parametroService
                .obtenerValorOpcional(String.format("IR5TA_DIVISOR_MES_%02d", mes), anioFiscal, null)
                .orElse(DOCE);
        return divisor.signum() <= 0 ? DOCE : divisor;
    }

    /**
     * Proyecta los aguinaldos/gratificaciones FUTUROS afectos a 5ta. Gated por
     * {@code IR5TA_INCLUYE_AGUINALDO} (por régimen → global). FP se proyecta si
     * mes ≤ 7 y Navidad si mes ≤ 12; lo anterior ya está en el histórico.
     * Monto = remun × {@code IR5TA_AGUINALDO_FACTOR} por cada aguinaldo.
     */
    private BigDecimal proyectarAguinaldos5ta(
            BigDecimal baseRemuneracion, int mes, int anioFiscal, Long regimenLaboralId) {

        boolean incluir = parametroService
                .obtenerValorOpcional("IR5TA_INCLUYE_AGUINALDO", anioFiscal, regimenLaboralId)
                .map(v -> v.signum() > 0)
                .orElse(false);
        if (!incluir) {
            return BigDecimal.ZERO;
        }
        BigDecimal factor = parametroService
                .obtenerValorOpcional("IR5TA_AGUINALDO_FACTOR", anioFiscal, null)
                .orElse(BigDecimal.ONE);
        int nFuturos = (mes <= 7 ? 1 : 0) + (mes <= 12 ? 1 : 0);
        if (nFuturos == 0) {
            return BigDecimal.ZERO;
        }
        return baseRemuneracion.multiply(factor).multiply(BigDecimal.valueOf(nFuturos));
    }

    /**
     * Acumula del año fiscal: remuneración ya percibida (Σ totalIngresos de los
     * movimientos de meses anteriores) y retención 5ta ya efectuada (Σ líneas
     * MEF 05101). Excluye el movimiento actual.
     */
    private Historico5ta acumularHistorico5ta(
            Long empleadoId, int anioFiscal, int mesActual, Long movimientoActualId) {

        BigDecimal percibido = BigDecimal.ZERO;
        BigDecimal retenido = BigDecimal.ZERO;
        Long conceptoId5ta = conceptoPorMef(MEF_RETENCION_5TA).getId();

        for (MovimientoPlanilla mov : movimientoRepository.findByEmpleadoIdAndActivo(empleadoId, 1)) {
            if (mov.getId() != null && mov.getId().equals(movimientoActualId)) continue;
            if (anioDePeriodo(mov.getPeriodo()) != anioFiscal) continue;
            if (mesDePeriodo(mov.getPeriodo()) >= mesActual) continue; // solo meses previos

            percibido = percibido.add(toBigDecimal(mov.getTotalIngresos()));
            for (MovimientoPlanillaDetalle d : detalleRepository.findByMovimientoPlanillaId(mov.getId())) {
                if (conceptoId5ta.equals(d.getConceptoPlanillaId()) && d.getMonto() != null) {
                    retenido = retenido.add(BigDecimal.valueOf(d.getMonto()));
                }
            }
        }
        return new Historico5ta(percibido, retenido);
    }

    /** Snapshot IR5TA (FASE 2) — deja trazable la proyección Art. 40. */
    private void registrarSnapshotIr5ta(
            Long empleadoId, String periodo, Long movimientoId, int anioFiscal, int mes,
            BigDecimal uit, BigDecimal brutaAnual, BigDecimal mesesRestantes,
            BigDecimal aguinaldos, Historico5ta hist, Ir5taResultado r) {

        calculoSnapshotService.registrar(
                CalculoSnapshotService.registro(
                                empleadoId, periodo, CalculoSnapshotService.REGLA_IR5TA)
                        .movimiento(movimientoId)
                        .base(brutaAnual)
                        .resultado(r.retencionMes)
                        .version(String.valueOf(anioFiscal))
                        .formula(String.format(
                                "(impAnual %s - retenidoPrev %s) / divisor %s = %s",
                                escala(r.impuestoAnual), escala(hist.retenido),
                                r.divisor.toPlainString(), escala(r.retencionMes)))
                        .param("mes", mes)
                        .param("uit", uit)
                        .param("sieteUit", uit.multiply(SIETE))
                        .param("percibidoPrev", hist.percibido)
                        .param("mesesRestantes", mesesRestantes)
                        .param("aguinaldosProyectados", aguinaldos)
                        .param("brutaAnual", brutaAnual)
                        .param("rentaNeta", r.rentaNeta)
                        .param("impuestoAnual", r.impuestoAnual)
                        .param("retenidoAcumulado", hist.retenido)
                        .param("saldoPorRetener", r.saldo)
                        .param("divisorMes", r.divisor));
    }

    private int mesDePeriodo(String periodo) {
        String p = periodo == null ? "" : periodo.replace("-", "").trim();
        if (p.length() < 6) {
            throw new NegocioException("Periodo inválido (esperado YYYYMM/YYYY-MM): " + periodo);
        }
        try {
            return Integer.parseInt(p.substring(4, 6));
        } catch (NumberFormatException ex) {
            throw new NegocioException("Periodo inválido: " + periodo);
        }
    }

    /** Histórico 5ta del año: percibido + retenido acumulado. */
    private static final class Historico5ta {
        final BigDecimal percibido;
        final BigDecimal retenido;
        Historico5ta(BigDecimal percibido, BigDecimal retenido) {
            this.percibido = percibido;
            this.retenido = retenido;
        }
    }

    /** Resultado intermedio del Art. 40 (para línea + snapshot). Package-private para test. */
    static final class Ir5taResultado {
        final BigDecimal rentaNeta;
        final BigDecimal impuestoAnual;
        final BigDecimal saldo;
        final BigDecimal divisor;
        final BigDecimal retencionMes;
        Ir5taResultado(BigDecimal rentaNeta, BigDecimal impuestoAnual, BigDecimal saldo,
                BigDecimal divisor, BigDecimal retencionMes) {
            this.rentaNeta = rentaNeta;
            this.impuestoAnual = impuestoAnual;
            this.saldo = saldo;
            this.divisor = divisor;
            this.retencionMes = retencionMes;
        }
    }

    /**
     * Escala progresiva del IR 5ta categoría (TUO LIR — SPEC §16.2). Tramos y
     * tasas vienen de TBL_PARAMETRO_REMUNERATIVO (REGLA-02). Los límites están
     * en múltiplos de UIT y se convierten a soles con la UIT del año.
     */
    private BigDecimal aplicarEscalaProgresiva5ta(
            BigDecimal rentaNeta, BigDecimal uit, int anioFiscal) {

        BigDecimal lim1 = parametroService.obtenerValor("IR5TA_TRAMO1_LIM_UIT", anioFiscal, null).multiply(uit);
        BigDecimal lim2 = parametroService.obtenerValor("IR5TA_TRAMO2_LIM_UIT", anioFiscal, null).multiply(uit);
        BigDecimal lim3 = parametroService.obtenerValor("IR5TA_TRAMO3_LIM_UIT", anioFiscal, null).multiply(uit);
        BigDecimal lim4 = parametroService.obtenerValor("IR5TA_TRAMO4_LIM_UIT", anioFiscal, null).multiply(uit);
        BigDecimal t1 = parametroService.obtenerValor("IR5TA_TRAMO1_TASA", anioFiscal, null);
        BigDecimal t2 = parametroService.obtenerValor("IR5TA_TRAMO2_TASA", anioFiscal, null);
        BigDecimal t3 = parametroService.obtenerValor("IR5TA_TRAMO3_TASA", anioFiscal, null);
        BigDecimal t4 = parametroService.obtenerValor("IR5TA_TRAMO4_TASA", anioFiscal, null);
        BigDecimal t5 = parametroService.obtenerValor("IR5TA_TRAMO5_TASA", anioFiscal, null);

        BigDecimal impuesto = BigDecimal.ZERO;

        // Tramo 1: 0 .. lim1
        impuesto = impuesto.add(rentaNeta.min(lim1).multiply(t1));
        // Tramo 2: lim1 .. lim2
        if (rentaNeta.compareTo(lim1) > 0) {
            impuesto = impuesto.add(rentaNeta.min(lim2).subtract(lim1).multiply(t2));
        }
        // Tramo 3: lim2 .. lim3
        if (rentaNeta.compareTo(lim2) > 0) {
            impuesto = impuesto.add(rentaNeta.min(lim3).subtract(lim2).multiply(t3));
        }
        // Tramo 4: lim3 .. lim4
        if (rentaNeta.compareTo(lim3) > 0) {
            impuesto = impuesto.add(rentaNeta.min(lim4).subtract(lim3).multiply(t4));
        }
        // Tramo 5: > lim4
        if (rentaNeta.compareTo(lim4) > 0) {
            impuesto = impuesto.add(rentaNeta.subtract(lim4).multiply(t5));
        }
        return impuesto.setScale(2, RoundingMode.HALF_UP);
    }

    // ======================================================================
    // PASO 9 — ESSALUD EMPLEADOR con mínimo + split EPS (SPEC §5.5)
    // ======================================================================

    /**
     * Calcula el aporte ESSALUD con piso regulatorio y, si el trabajador tiene
     * EPS, lo divide en aporte empleador (6.75%) + copago trabajador (2.25%).
     *
     * <p>Base: {@code essaludBase = MAX(baseImponible * TASA_ESSALUD, ESSALUD_MINIMO)}.
     * El umbral "1130" del Excel es derivado ({@code ESSALUD_MINIMO / TASA_ESSALUD})
     * y NO se hardcodea — el {@code MAX} produce el mismo resultado (REGLA-02).
     *
     * <p>Sin EPS → 06001, 9% completo al empleador (LEY-02, no descuenta).
     * <br>Con EPS → 06002 empleador 6.75% + 05309 copago trabajador 2.25%.
     *
     * @return el copago EPS del trabajador (descuento). {@code ZERO} si no tiene EPS.
     */
    public BigDecimal calcularEssaludEmpleador(
            MovimientoPlanilla movimiento,
            Empleado empleado,
            BigDecimal baseImponible,
            int anioFiscal,
            String regimenLaboralCodigo,
            Long empleadoId,
            String periodo) {

        if (baseImponible.signum() <= 0) return BigDecimal.ZERO;

        // F1.6 — Tope 45% UIT EsSalud SOLO para CAS (decisión RRHH C2 / 2026-05-31).
        // 728, SERVIR y 276 no se topean: sin regresión respecto al motor v2.
        BigDecimal baseAntesTope = baseImponible;
        baseImponible = aplicarTopeEssaludCAS(baseImponible, regimenLaboralCodigo, anioFiscal);

        BigDecimal tasaEssalud = parametroService.obtenerValor("TASA_ESSALUD", anioFiscal, null);
        BigDecimal minimo      = parametroService.obtenerValor("ESSALUD_MINIMO", anioFiscal, null);

        // essaludBase = MAX(base*9%, mínimo) — equivalente al IF(remun<=1130) del Excel.
        BigDecimal baseCalculo = baseImponible.multiply(tasaEssalud);
        BigDecimal essaludBase = baseCalculo.max(minimo).setScale(2, RoundingMode.HALF_UP);
        boolean minimoActivado = essaludBase.compareTo(
                baseCalculo.setScale(2, RoundingMode.HALF_UP)) > 0;

        boolean tieneEps = empleado != null && "S".equalsIgnoreCase(empleado.getHasEps());

        if (!tieneEps) {
            // 9% completo al empleador — informativo, NO descuenta (LEY-02).
            grabarDetalle(movimiento.getId(), conceptoPorMef(MEF_ESSALUD), essaludBase,
                    "ESSALUD empleador " + porcentajeTexto(tasaEssalud));
            registrarSnapshotEssalud(empleadoId, periodo, movimiento.getId(), anioFiscal,
                    baseAntesTope, baseImponible, essaludBase,
                    tasaEssalud, minimo, minimoActivado, false, regimenLaboralCodigo);
            return BigDecimal.ZERO;
        }

        // Con EPS: split proporcional del essaludBase según las tasas de BD.
        BigDecimal tasaEmp    = parametroService.obtenerValor(
                "TASA_ESSALUD_EPS_EMPLEADOR", anioFiscal, null);
        BigDecimal tasaCopago = parametroService.obtenerValor(
                "TASA_ESSALUD_EPS_COPAGO", anioFiscal, null);

        BigDecimal montoEmpleador = essaludBase.multiply(tasaEmp)
                .divide(tasaEssalud, 2, RoundingMode.HALF_UP);
        BigDecimal montoCopago = essaludBase.multiply(tasaCopago)
                .divide(tasaEssalud, 2, RoundingMode.HALF_UP);

        grabarDetalle(movimiento.getId(), conceptoPorMef(MEF_ESSALUD_EPS), montoEmpleador,
                "ESSALUD empleador con EPS " + porcentajeTexto(tasaEmp));
        grabarDetalle(movimiento.getId(), conceptoPorMef(MEF_COPAGO_EPS), montoCopago,
                "Copago EPS trabajador " + porcentajeTexto(tasaCopago));
        registrarSnapshotEssalud(empleadoId, periodo, movimiento.getId(), anioFiscal,
                baseAntesTope, baseImponible, essaludBase,
                tasaEssalud, minimo, minimoActivado, true, regimenLaboralCodigo);

        // El copago SÍ es descuento al trabajador.
        return montoCopago;
    }

    private void registrarSnapshotEssalud(
            Long empleadoId, String periodo, Long movimientoId, int anioFiscal,
            BigDecimal baseAntesTope, BigDecimal baseAsegurable, BigDecimal essaludBase,
            BigDecimal tasa, BigDecimal minimo, boolean minimoActivado,
            boolean tieneEps, String regimen) {

        boolean topeAplicado = baseAsegurable.compareTo(baseAntesTope) < 0;
        String formula = "essaludBase = MAX(baseAsegurable × " + escala(tasa)
                + ", " + escala(minimo) + ")"
                + (minimoActivado ? " → mínimo activado" : "")
                + (topeAplicado ? " | tope 45% UIT aplicado" : "");

        calculoSnapshotService.registrar(
                CalculoSnapshotService.registro(
                                empleadoId, periodo, CalculoSnapshotService.REGLA_ESSALUD)
                        .movimiento(movimientoId)
                        .base(baseAsegurable)
                        .resultado(essaludBase)
                        .version(String.valueOf(anioFiscal))
                        .formula(formula)
                        .param("regimen", regimen)
                        .param("baseAntesTope", baseAntesTope)
                        .param("baseAsegurable", baseAsegurable)
                        .param("tasaEssalud", tasa)
                        .param("essaludMinimo", minimo)
                        .param("topeAplicado", topeAplicado)
                        .param("minimoActivado", minimoActivado)
                        .param("tieneEps", tieneEps));
    }

    // ======================================================================
    // PASO 10 — TOTALES + validación neto 50% (REGLA SERVIR-07 / §5.4)
    // ======================================================================

    /**
     * Persiste los totales del movimiento y aplica la validación neto 50%.
     *
     * <p>SPEC §5.4: {@code baseLibreDisponibilidad =
     * ingresosMensualesPermanentes − descuentosLegales − mandatosJudiciales}.
     * Si {@code neto >= umbral} → ESTADO_NETO = 'BIEN'; si no → 'NETO_NO_VA'.
     * {@code descuentosLegales} incluye renta (IR4ta CAS o IR5ta) y aporte
     * pensionario real (AFP/ONP), según el régimen aplicable.
     *
     * <p>El motor MARCA el estado; no aborta la generación (igual que el Excel,
     * "NETO NO VA" es una etiqueta). El bloqueo efectivo corresponde al flujo
     * de aprobación de planilla: no se aprueba con un movimiento en NETO_NO_VA.
     */
    public void calcularTotalesYCUC(
            MovimientoPlanilla movimiento,
            BigDecimal totalIngresos,
            BigDecimal totalDescuentos,
            BigDecimal ingresosMensualesPermanentes,
            BigDecimal retencionRenta,
            BigDecimal aportePension,
            BigDecimal descuentoJudicial) {

        // FASE 2: Consolidación exacta de totales para planillas ADICIONALES (recalcula desde BD + actual)
        if (movimiento.getTipoPlanilla() != null && movimiento.getTipoPlanilla().startsWith("ADICIONAL_") && movimiento.getId() != null) {
            List<MovimientoPlanillaDetalle> detalles = detalleRepository.findByMovimientoPlanillaId(movimiento.getId());
            BigDecimal tIngresos = BigDecimal.ZERO;
            BigDecimal tDescuentos = BigDecimal.ZERO;
            BigDecimal iPermanentes = BigDecimal.ZERO;
            BigDecimal rRenta = BigDecimal.ZERO;
            BigDecimal aPension = BigDecimal.ZERO;
            BigDecimal dJudicial = BigDecimal.ZERO;

            for (MovimientoPlanillaDetalle d : detalles) {
                BigDecimal m = BigDecimal.valueOf(d.getMonto() != null ? d.getMonto() : 0.0);
                String tipo = d.getConceptoTipo() != null ? d.getConceptoTipo() : "";
                
                if ("REMUNERATIVO".equalsIgnoreCase(tipo) || "NO_REMUNERATIVO".equalsIgnoreCase(tipo)) {
                    tIngresos = tIngresos.add(m);
                    if ("REMUNERATIVO".equalsIgnoreCase(tipo)) {
                        iPermanentes = iPermanentes.add(m);
                    }
                } else if ("DESCUENTO".equalsIgnoreCase(tipo) || "APORTE_TRABAJADOR".equalsIgnoreCase(tipo) || "DESCUENTO_JUDICIAL".equalsIgnoreCase(tipo)) {
                    tDescuentos = tDescuentos.add(m);
                    
                    Optional<ConceptoPlanilla> cOpt = conceptoRepository.findById(d.getConceptoPlanillaId());
                    if (cOpt.isPresent()) {
                        ConceptoPlanilla c = cOpt.get();
                        String mef = c.getCodigoMef();
                        String cod = c.getCodigo();
                        if ("05101".equals(mef) || "IR4TA_CAS".equals(cod)) {
                            rRenta = rRenta.add(m);
                        }
                        if ("05001".equals(mef) || "05002".equals(mef) || "05003".equals(mef) || "05004".equals(mef)) {
                            aPension = aPension.add(m);
                        }
                    }
                    if ("DESCUENTO_JUDICIAL".equalsIgnoreCase(tipo)) {
                        dJudicial = dJudicial.add(m);
                    }
                }
            }
            totalIngresos = tIngresos;
            totalDescuentos = tDescuentos;
            ingresosMensualesPermanentes = iPermanentes;
            retencionRenta = rRenta;
            aportePension = aPension;
            descuentoJudicial = dJudicial;
        }

        BigDecimal neto = totalIngresos.subtract(totalDescuentos)
                .setScale(2, RoundingMode.HALF_UP);

        movimiento.setTotalIngresos(totalIngresos.setScale(2, RoundingMode.HALF_UP).doubleValue());
        movimiento.setTotalDescuentos(totalDescuentos.setScale(2, RoundingMode.HALF_UP).doubleValue());
        movimiento.setNetoPagar(neto.doubleValue());

        // Validación neto 50% — controla el margen para descuentos voluntarios
        // o de terceros; los mandatos judiciales reducen antes la base libre.
        BigDecimal descuentosLegales = retencionRenta.add(aportePension);
        BigDecimal baseLibreDisponibilidad = ingresosMensualesPermanentes
                .subtract(descuentosLegales)
                .subtract(descuentoJudicial)
                .max(BigDecimal.ZERO);
        BigDecimal umbral = baseLibreDisponibilidad
                .multiply(MEDIO)
                .setScale(2, RoundingMode.HALF_UP);
        movimiento.setNeto50pctMinimo(umbral.doubleValue());

        boolean netoOk = neto.compareTo(umbral) >= 0;
        movimiento.setEstadoNeto(netoOk ? "BIEN" : "NETO_NO_VA");

        if (neto.signum() <= 0) {
            movimiento.setEstado("REVISAR");
            movimiento.setObservacion("NETO <= 0 — revisar descuentos antes de aprobar");
        } else if (!netoOk) {
            movimiento.setEstado("REVISAR");
            movimiento.setObservacion(
                    "NETO_NO_VA — neto " + neto.toPlainString()
                            + " es menor al mínimo 50% (" + umbral.toPlainString() + ")");
        }

        // V012_03 — Persistir días laborados netos (30 − faltas − eventos) para que
        // la lista y la boleta muestren el valor real en vez del 30 hardcodeado.
        int diasLaborados = calcularDiasLaborados(
                movimiento.getEmpleadoId(), movimiento.getPeriodo());
        movimiento.setDiasLaborados(diasLaboradosSeguro(
                diasLaborados, movimiento.getEmpleadoId(), movimiento.getPeriodo()));

        movimientoRepository.save(movimiento);
    }

    /**
     * Guard de rango [0, 31] para los días laborados antes de persistir. Un valor
     * fuera de rango indica un cálculo anómalo: no se persiste el valor calculado,
     * se usa {@link #DIAS_LAB_DEFAULT} (30) y se registra una advertencia con el
     * empleado y período afectados (mismo criterio que el override de tasa AFP
     * fuera de rango).
     */
    static int diasLaboradosSeguro(int dias, Long empleadoId, String periodo) {
        if (dias < DIAS_LAB_MIN || dias > DIAS_LAB_MAX) {
            log.warn("Días laborados fuera de rango [{}, {}]: {} para EMPLEADO_ID={} "
                    + "PERIODO={}. Se persiste {} por defecto.",
                    DIAS_LAB_MIN, DIAS_LAB_MAX, dias, empleadoId, periodo, DIAS_LAB_DEFAULT);
            return DIAS_LAB_DEFAULT;
        }
        return dias;
    }

    // ======================================================================
    // HELPERS
    // ======================================================================

    private void borrarMovimientoAnterior(Long empleadoId, String periodo, String tipoPlanilla, Long empleadoPuestoId) {
        if (tipoPlanilla != null && tipoPlanilla.startsWith("ADICIONAL_")) {
            // FASE 2: No borrar en llamadas sucesivas del mismo lote para permitir consolidación
            // de boletas (un solo cabezal por empleado con múltiples detalles)
            return;
        }

        Optional<MovimientoPlanilla> opt;
        if (tipoPlanilla == null || tipoPlanilla.equals("ORDINARIA")) {
            opt = movimientoRepository.findByEmpleadoIdAndPeriodoAndActivo(empleadoId, periodo, 1);
        } else {
            opt = movimientoRepository.findByEmpleadoIdAndPeriodoAndTipoPlanillaAndEmpleadoPuestoIdAndActivo(
                    empleadoId, periodo, tipoPlanilla, empleadoPuestoId, 1);
        }

        opt.ifPresent(mov -> {
            // Borrar primero lo que apunta al movimiento (FK) antes del
            // movimiento mismo: detalle, conciliación AIRHSP y abono banco.
            detalleRepository.deleteByMovimientoPlanillaId(mov.getId());
            conciliacionRepository.deleteByMovimientoPlanillaId(mov.getId());
            abonoBancoRepository.deleteByMovimientoPlanillaId(mov.getId());
            movimientoRepository.delete(mov);
        });
    }

    // ======================================================================
    // PASO 16 — CONCILIACIÓN AIRHSP AUTOMÁTICA (M13 / SPEC §12.2 PANTALLA-06)
    // ======================================================================

    /**
     * Crea el registro {@code INDECI_CONCILIACION_AIRHSP} del movimiento.
     *
     * <p>{@code montoSistema} = remuneración bruta calculada por el motor;
     * {@code montoAirhsp} = {@code Empleado.AIRHSP_MONTO} (lo registrado en el
     * MEF; 0 si no hay registro). Si la diferencia es ≤ 0.01 nace CONCILIADO;
     * si no, PENDIENTE — y la PANTALLA-06 exige justificarla.
     *
     * <p>La columna DIFERENCIA es VIRTUAL en BD: no se setea desde Java.
     */
    public void crearConciliacionAirhsp(
            MovimientoPlanilla movimiento,
            Empleado empleado,
            PeriodoPlanilla periodoPlanilla,
            BigDecimal totalIngresos) {

        double montoSistema = totalIngresos
                .setScale(2, RoundingMode.HALF_UP).doubleValue();
        double montoAirhsp = (empleado != null && empleado.getAirhspMonto() != null)
                ? empleado.getAirhspMonto()
                : 0d;

        ConciliacionAirhsp conciliacion = new ConciliacionAirhsp();
        conciliacion.setEmpleadoId(movimiento.getEmpleadoId());
        conciliacion.setMovimientoPlanillaId(movimiento.getId());
        conciliacion.setPeriodoPlanillaId(periodoPlanilla.getId());
        conciliacion.setMontoSistema(montoSistema);
        conciliacion.setMontoAirhsp(montoAirhsp);
        boolean cuadra = Math.abs(montoSistema - montoAirhsp) <= 0.01;
        conciliacion.setEstado(cuadra ? "CONCILIADO" : "PENDIENTE");
        conciliacion.setCreatedAt(LocalDateTime.now());
        conciliacionRepository.save(conciliacion);
    }

    private MovimientoPlanilla crearCabecera(Long empleadoId, String periodo, String tipoPlanilla, 
                                             Long empleadoPuestoId, LocalDate fechaInicioPago, LocalDate fechaFinPago, Long loteId) {
        // FASE 2: Consolidación de boleta adicional
        if (tipoPlanilla != null && tipoPlanilla.startsWith("ADICIONAL_")) {
            List<MovimientoPlanilla> existentes = movimientoRepository.findByEmpleadoIdAndPeriodoAndActivo(empleadoId, periodo, 1)
                    .stream().filter(m -> tipoPlanilla.equals(m.getTipoPlanilla())).toList();
            if (!existentes.isEmpty()) {
                MovimientoPlanilla existente = existentes.get(0);
                if (fechaInicioPago != null && (existente.getFechaInicioPago() == null || fechaInicioPago.isBefore(existente.getFechaInicioPago()))) {
                    existente.setFechaInicioPago(fechaInicioPago);
                }
                if (fechaFinPago != null && (existente.getFechaFinPago() == null || fechaFinPago.isAfter(existente.getFechaFinPago()))) {
                    existente.setFechaFinPago(fechaFinPago);
                }
                return existente;
            }
        }

        MovimientoPlanilla movimiento = new MovimientoPlanilla();
        movimiento.setEmpleadoId(empleadoId);
        movimiento.setPeriodo(periodo);
        movimiento.setTipoPlanilla(tipoPlanilla != null ? tipoPlanilla : "ORDINARIA");
        movimiento.setEmpleadoPuestoId(empleadoPuestoId);
        movimiento.setFechaInicioPago(fechaInicioPago);
        movimiento.setFechaFinPago(fechaFinPago);
        movimiento.setLoteId(loteId);
        movimiento.setObservacion("PLANILLA GENERADA");
        movimiento.setActivo(1);
        movimiento.setEstado("GENERADO");
        movimiento.setCreatedAt(LocalDateTime.now());
        movimiento.setTotalIngresos(0.0);
        movimiento.setTotalDescuentos(0.0);
        movimiento.setNetoPagar(0.0);
        
        // FASE 4 - Snapshot de inmutabilidad antes del primer save
        snapshotFactory.capturarMetadata(movimiento);
        
        return movimientoRepository.save(movimiento);
    }

    public MovimientoPlanillaDetalle grabarDetalle(
            Long movimientoId, ConceptoPlanilla concepto,
            BigDecimal monto, String observacion) {
        MovimientoPlanillaDetalle det = new MovimientoPlanillaDetalle();
        det.setMovimientoPlanillaId(movimientoId);
        det.setConceptoPlanillaId(concepto.getId());
        // SPEC_CONCEPTOS_PLANILLA P3 — snapshot histórico (solo aditivo, no cambia
        // el monto): congela código/nombre/tipo del concepto al grabar el detalle.
        det.setConceptoCodigo(concepto.getCodigo());
        det.setConceptoNombre(concepto.getNombre());
        det.setConceptoTipo(concepto.getTipoConcepto());
        det.setMonto(monto.setScale(2, RoundingMode.HALF_UP).doubleValue());
        det.setCantidad(1.0);
        det.setObservacion(observacion);
        det.setCreatedAt(LocalDateTime.now());
        return detalleRepository.save(det);
    }

    private ConceptoPlanilla conceptoPorMef(String codigoMef) {
        return conceptoRepository.findByCodigoMefAndActivo(codigoMef, 1)
                .orElseThrow(() -> new NegocioException(
                        "Concepto MEF " + codigoMef + " no existe o no está activo. "
                                + "Ejecutar seed V010_04__seed_conceptos_mef.sql."));
    }

    /** Track B F4 — Resolutor público de concepto por CODIGO_MEF (gratificación CAS). */
    public ConceptoPlanilla conceptoPorCodigoMef(String codigoMef) {
        return conceptoPorMef(codigoMef);
    }

    /**
     * FASE 1 — Resuelve el concepto de retención IR 4ta CAS por CODIGO interno
     * (no por CODIGO_MEF). Es retención tributaria SUNAT (3042), no concepto de
     * ingreso MEF/AIRHSP: su CODIGO_MEF es 'NO_APLICA' y NO bloquea la planilla.
     * Si el concepto no existe/está inactivo es un error técnico de configuración
     * (lo pre-detecta el preflight V14). Ejecutar seed V010_49.
     */
    public ConceptoPlanilla conceptoIr4taCas() {
        return conceptoRepository.findByCodigoAndActivo(CODIGO_INTERNO_IR4TA_CAS, 1)
                .orElseThrow(() -> new NegocioException(
                        "Concepto IR4TA_CAS no configurado o inactivo. "
                                + "Ejecutar seed V010_49__concepto_ir4ta_cas_sunat_3042.sql."));
    }

    public String resolverRegimenLaboralCodigo(Long regimenLaboralId) {
        return regimenLaboralRepository.findById(regimenLaboralId)
                .map(RegimenLaboral::getCodigo)
                .orElse(null);
    }

    /**
     * Devuelve el TIPO del régimen pensionario (ONP | AFP | ...). Se usa TIPO
     * y no CODIGO: el CODIGO trae la AFP específica (PROFUTURO, INTEGRA…),
     * mientras que TIPO clasifica el sistema previsional.
     */
    private String resolverRegimenPensionarioTipo(Long regimenPensionarioId) {
        return regimenPensionarioRepository.findById(regimenPensionarioId)
                .map(RegimenPensionario::getTipo)
                .orElse(null);
    }

    private static String normalizarTipoPensionario(String tipoRegimen) {
        return tipoRegimen == null ? "" : tipoRegimen.trim().toUpperCase();
    }

    private int anioDePeriodo(String periodo) {
        if (periodo == null || periodo.length() < 4) {
            throw new NegocioException("Periodo inválido: " + periodo);
        }
        try {
            return Integer.parseInt(periodo.substring(0, 4));
        } catch (NumberFormatException ex) {
            throw new NegocioException("Periodo inválido: " + periodo);
        }
    }

    public BigDecimal toBigDecimal(Double valor) {
        return valor == null ? BigDecimal.ZERO : BigDecimal.valueOf(valor);
    }

    /**
     * F2 — Base remunerativa del vínculo para el período. Prioridad: historial
     * remunerativo VIGENTE y APROBADO a la fecha del período; si no hay, fallback a
     * {@code EMPLEADO_PLANILLA.SUELDO_BASICO} con advertencia auditable
     * {@code USANDO_FALLBACK_LEGACY_SUELDO_BASICO}. No inventa cálculo por tramos.
     */
    public BigDecimal resolverBaseRemunerativa(EmpleadoPlanilla planilla, String periodo) {
        if (planilla == null) {
            return BigDecimal.ZERO;
        }
        if (planilla.getId() != null && periodo != null) {
            java.time.LocalDate inicio = ParametroRemunerativoService.periodoToFechaInicio(periodo);
            java.time.LocalDate fin = inicio.withDayOfMonth(inicio.lengthOfMonth());

            // Guard de tramos (F2): un cambio remunerativo DENTRO del período (con
            // vigencia posterior al día 1) exigiría calcular por tramos, no soportado.
            // Decisión RR.HH.: no inventar cálculo → alerta bloqueante.
            if (remuneracionHistRepository.countCambiosEnRango(planilla.getId(), inicio, fin) > 0) {
                throw new NegocioException(
                        "Cambio remunerativo dentro del período " + periodo
                        + ": el cálculo por tramos no está soportado. Regularice la vigencia para "
                        + "que inicie al comienzo del período, o configure una regla de prorrateo.");
            }

            var vig = remuneracionHistRepository.findVigenteAprobada(
                    planilla.getId(), inicio,
                    org.springframework.data.domain.PageRequest.of(0, 1));
            if (!vig.isEmpty() && vig.get(0).getRemuneracionTotal() != null) {
                return BigDecimal.valueOf(vig.get(0).getRemuneracionTotal());
            }
        }
        log.warn("USANDO_FALLBACK_LEGACY_SUELDO_BASICO empleadoPlanillaId={} periodo={} — "
                + "sin historial remunerativo vigente aprobado; se usa EMPLEADO_PLANILLA.SUELDO_BASICO",
                planilla.getId(), periodo);
        return toBigDecimal(planilla.getSueldoBasico());
    }

    private BigDecimal primeraTasaNoNula(Double explicita, java.util.function.Supplier<BigDecimal> fallback) {
        BigDecimal emp = normalizarTasaEmpleado(explicita);
        return emp != null ? emp : fallback.get();
    }

    /**
     * Normaliza una tasa por-empleado a fracción: si viene como porcentaje (&gt;1,
     * ej. 13.0) la divide entre 100; si ya es fracción (≤1) la deja igual. Devuelve
     * {@code null} si no hay valor positivo.
     */
    static BigDecimal normalizarTasaEmpleado(Double explicita) {
        if (explicita == null || explicita < 0) {
            return null;
        }
        if (explicita == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal v = BigDecimal.valueOf(explicita);
        return v.compareTo(BigDecimal.ONE) > 0 ? v.divide(CIEN, 6, RoundingMode.HALF_UP) : v;
    }

    /**
     * Resuelve la tasa AFP a aplicar (REGLA-02): prioriza el valor por-empleado
     * solo si es un override plausible (&gt;0 y ≤ {@code maxFraccion}); de lo contrario
     * usa la tasa oficial de la vigencia AFP; si no hay vigencia, cae al respaldo
     * paramétrico. Blinda contra datos mal digitados (ej. comisión 78%).
     *
     * @param porcentajeEmpleado tasa cargada en INDECI_EMPLEADO_PENSION (puede ser null)
     * @param oficialPct         tasa de la vigencia AFP como porcentaje (ej. 0.67) o null
     * @param fallback           respaldo si no hay vigencia ni override válido
     * @param maxFraccion        techo de plausibilidad para el override (fracción)
     */
    static BigDecimal tasaAfpConOverride(
            Double porcentajeEmpleado, BigDecimal oficialPct,
            java.util.function.Supplier<BigDecimal> fallback, BigDecimal maxFraccion) {
        BigDecimal emp = normalizarTasaEmpleado(porcentajeEmpleado);
        if (emp != null && emp.compareTo(maxFraccion) <= 0) {
            return emp;
        }
        BigDecimal oficial = pctAFraccion(oficialPct);
        return oficial != null ? oficial : fallback.get();
    }

    /**
     * Comisión sobre flujo aplicable en la planilla MENSUAL. En esquema MIXTA
     * (comisión sobre saldo) el flujo mensual es 0%: la comisión anual sobre el saldo
     * acumulado del fondo se cobra aparte, una vez al año, y queda fuera del alcance
     * de la planilla mensual.
     * TODO(afp-comision-saldo): implementar el cálculo anual de comisión sobre saldo
     * para afiliados MIXTA (proceso separado, no mensual).
     */
    static BigDecimal tasaComisionFlujo(
            boolean esMixta, Double porcentajeEmpleado, BigDecimal oficialPct,
            java.util.function.Supplier<BigDecimal> fallback, BigDecimal maxFraccion) {
        if (esMixta) {
            return BigDecimal.ZERO;
        }
        return tasaAfpConOverride(porcentajeEmpleado, oficialPct, fallback, maxFraccion);
    }

    /** true si el tipo de comisión del afiliado es MIXTA (comisión sobre saldo). */
    private boolean esComisionMixta(Long tipoComisionAfpId) {
        if (tipoComisionAfpId == null) {
            return false;
        }
        return tipoComisionAfpRepository.findById(tipoComisionAfpId)
                .map(t -> TIPO_COMISION_MIXTA.equalsIgnoreCase(t.getCodigo()))
                .orElse(false);
    }

    /** Convierte un porcentaje de vigencia (ej. 1.55 = 1.55%) a fracción (0.0155). */
    static BigDecimal pctAFraccion(BigDecimal pct) {
        if (pct == null || pct.signum() <= 0) {
            return null;
        }
        return pct.divide(CIEN, 6, RoundingMode.HALF_UP);
    }

    private String porcentajeTexto(BigDecimal tasa) {
        return tasa.multiply(CIEN)
                .setScale(2, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString() + "%";
    }

    // ======================================================================
    // ENTRY POINTS RESTANTES (sin cambios funcionales)
    // ======================================================================

    /**
     * Genera la planilla de todos los empleados activos del período.
     *
     * <p>Spec 011 / C2 (BKD-001): NO aborta cuando un empleado falla — procesa
     * todos y devuelve un {@link GeneracionMasivaResultDto} con exitosos y
     * fallidos (cada fallido con su empleadoId y la razón). Cada empleado se
     * genera en su propia transacción (vía el proxy {@code self}).
     */
    public GeneracionMasivaResultDto generarTodoPeriodo(GenerarPlanillaCabeceraDto request) {
        LocalDate inicioPeriodo = ParametroRemunerativoService.periodoToFechaInicio(request.getPeriodo());
        LocalDate finPeriodo = inicioPeriodo.withDayOfMonth(inicioPeriodo.lengthOfMonth());
        List<EmpleadoPlanilla> empleados = planillaRepository.findEmpleadosParaGeneracion(
                request.getRegimenLaboralId(),
                request.getTipoContratoId(),
                request.getCondicionLaboralId(),
                request.getModalidadCasId(),
                inicioPeriodo,
                finPeriodo
        );
        List<GeneracionFallidaDto> fallidos = new ArrayList<>();
        List<Long> exitososIds = new ArrayList<>();
        int exitosos = 0;

        for (EmpleadoPlanilla planilla : empleados) {
            try {
                self.generar(planilla.getEmpleadoId(), request.getPeriodo());
                exitosos++;
                exitososIds.add(planilla.getEmpleadoId());
            } catch (Exception e) {
                GeneracionFallidaDto fallida = new GeneracionFallidaDto();
                fallida.setEmpleadoId(planilla.getEmpleadoId());
                fallida.setRazon(e.getMessage());
                fallidos.add(fallida);
            }
        }

        GeneracionMasivaResultDto resultado = new GeneracionMasivaResultDto();
        resultado.setTotal(empleados.size());
        resultado.setExitosos(exitosos);
        resultado.setFallidos(fallidos);
        resultado.setExitososIds(exitososIds);
        return resultado;
    }

    public ResumenPlanillaDto obtenerResumen(Long empleadoId, String periodo) {
        MovimientoPlanilla mov = movimientoRepository
                .findByEmpleadoIdAndPeriodoAndActivo(empleadoId, periodo, 1)
                .orElseThrow(() -> new NegocioException("Planilla no encontrada"));

        ResumenPlanillaDto dto = new ResumenPlanillaDto();
        dto.setEmpleadoId(empleadoId);
        dto.setPeriodo(periodo);
        dto.setTotalIngresos(mov.getTotalIngresos());
        dto.setTotalDescuentos(mov.getTotalDescuentos());
        dto.setNetoPagar(mov.getNetoPagar());
        dto.setNeto50pctMinimo(mov.getNeto50pctMinimo());
        dto.setEstadoNeto(mov.getEstadoNeto());
        return dto;
    }

    // ======================================================================
    // ACCUMULATORS (clases internas, no Lombok para evitar overhead)
    // ======================================================================

    public static final class RemunerativosResult {
        public BigDecimal totalRemunerativo = BigDecimal.ZERO;
        public BigDecimal baseAportePension = BigDecimal.ZERO;
        public BigDecimal baseEssalud       = BigDecimal.ZERO;
    }

    public static final class ManualesResult {
        public BigDecimal ingresos          = BigDecimal.ZERO;
        public BigDecimal descuentos        = BigDecimal.ZERO;
        public BigDecimal ingresosMensualesPermanentes = BigDecimal.ZERO;
        public BigDecimal descuentoJudicial = BigDecimal.ZERO;
        public BigDecimal baseAportePension = BigDecimal.ZERO;
        public BigDecimal baseEssalud       = BigDecimal.ZERO;
    }

    // ======================================================================
    // F1.3 Motor v3 — Helpers de prorrateo por días laborados (PASO 3)
    //
    // Estos helpers son INFRAESTRUCTURA: están listos para consumir, pero el
    // motor existente todavía NO los invoca. Los pasos del motor que toman
    // ventaja del prorrateo se conectan en F1.5 (incrementos DS automáticos)
    // y F2 (eventos del período: maternidad, licencias, etc.).
    //
    // Visibilidad package-private para tests unitarios sin Spring.
    // ======================================================================

    /**
     * F1.3c — Prorratea un monto mensual por días laborados.
     *
     * Fórmula del Excel CAS consolidado del cliente y D.Leg. 276:
     * <pre>
     *     monto_prorrateado = monto_mensual / 30 × días_laborados
     * </pre>
     *
     * El divisor 30 es constante (mes calendar SUNAT/MEF) — NO depende del
     * mes real (febrero 28/29, meses de 31). Si se necesita "días naturales"
     * en otro contexto, crear otro helper; este es deliberadamente fijo.
     *
     * Casos borde:
     * <ul>
     *   <li>{@code montoMensual} null o cero → {@link BigDecimal#ZERO}.</li>
     *   <li>{@code diasLaborados} ≤ 0 → {@link BigDecimal#ZERO}.</li>
     *   <li>{@code diasLaborados} ≥ 30 → {@code montoMensual} tal cual (mes
     *       completo, sin redondeo extra: respeta la escala original).</li>
     *   <li>1 ≤ {@code diasLaborados} ≤ 29 → división con escala 2 HALF_UP.</li>
     * </ul>
     *
     * @return prorrateo redondeado a 2 decimales (HALF_UP), o el monto original
     *         si el mes está completo.
     */
    static BigDecimal prorratear(BigDecimal montoMensual, int diasLaborados) {
        if (montoMensual == null || montoMensual.signum() == 0) {
            return BigDecimal.ZERO;
        }
        if (diasLaborados <= 0) {
            return BigDecimal.ZERO;
        }
        if (diasLaborados >= 30) {
            return montoMensual;
        }
        return montoMensual
                .multiply(BigDecimal.valueOf(diasLaborados))
                .divide(TREINTA, 2, RoundingMode.HALF_UP);
    }

    /**
     * F1.3b — Calcula días efectivamente laborados del empleado en el período.
     *
     * Fórmula:
     * <pre>
     *     diasLaborados = DIAS_LAB_DEFAULT − dias_falta
     *                     (mínimo 0)
     * </pre>
     *
     * <p>Sólo se leen los días de falta si {@code AsistenciaCabecera.estado =
     * "VALIDADA"} (mismo criterio que el PASO 7b actual del motor —
     * intencional: sin asistencia validada, el motor no descuenta días).</p>
     *
     * <p>NO descuenta tardanzas, permisos ni feriados compensables: esos
     * generan descuentos parciales en PASO 7b (descuento por minutos), no
     * días enteros perdidos. Únicamente cuenta {@code DIAS_FALTA} de la
     * cabecera M04.</p>
     *
     * <p>Eventos del período (F2: LICENCIA_SIN_GOCE, SUSPENSION_NO_SUBSIDIADA)
     * se sumarán a {@code dias_falta} aquí cuando F2 entregue
     * {@code INDECI_EMPLEADO_EVENTO}.</p>
     *
     * <p>Hoy {@code DIAS_LAB_DEFAULT} es {@code 30} constante. F1.5 leerá el
     * campo real {@code EmpleadoPlanilla.diasLaboradosDefault} (V010_36) para
     * soportar tiempo parcial.</p>
     *
     * @return días laborados, mínimo 0, máximo {@link #DIAS_LAB_DEFAULT}.
     */
    int calcularDiasLaborados(Long empleadoId, String periodo) {
        int diasFalta = asistenciaCabeceraRepository
                .findByEmpleadoIdAndPeriodoAndActivo(empleadoId, periodo, 1)
                .filter(c -> ASISTENCIA_VALIDADA.equalsIgnoreCase(c.getEstado()))
                .map(c -> c.getDiasFalta() != null ? c.getDiasFalta() : 0)
                .orElse(0);

        // F2.3 — Sumar días afectos de los eventos del período cuyo tipo
        // tiene afectaDiasLaborados='S' (maternidad, licencia sin goce, cese,
        // permiso personal, etc.). El query ya filtra por flag y por activo.
        int diasEventos = sumarDiasAfectosEventos(empleadoId, periodo);

        // F1.8 — Días de subsidio (enfermedad/maternidad) reducen los días
        // laborados de forma OBLIGATORIA e incondicional, con el MISMO contrato
        // de dominio que usa el haber en calcularRemunerativos (DRY). Así la
        // boleta persiste los días reales (p. ej. 20) y los conceptos manuales
        // prorratean en sincronía con el haber.
        int diasSubsidio = subsidioPlanillaIntegracionService
                .diasSubsidioMotor(empleadoId, periodo);

        int dias = Math.max(0, DIAS_LAB_DEFAULT - diasFalta - diasEventos - diasSubsidio);

        // Fase 3 — acotar por los días del vínculo dentro del período (cese/alta
        // intra-mes), para que la boleta muestre los días realmente trabajados.
        // Para meses completos diasVinculo = 30 → no cambia nada.
        LocalDate inicioPeriodo = ParametroRemunerativoService.periodoToFechaInicio(periodo);
        LocalDate finPeriodo = inicioPeriodo.withDayOfMonth(inicioPeriodo.lengthOfMonth());
        int diasVinculo = planillaRepository
                .findVinculosVigentesEnPeriodo(empleadoId, inicioPeriodo, finPeriodo)
                .stream().findFirst()
                .map(p -> diasVinculoEnPeriodo(p, periodo))
                .orElse(DIAS_LAB_DEFAULT);
        return Math.min(dias, diasVinculo);
    }

    /**
     * Fase 3 — Días del vínculo DENTRO del período según cese/alta intra-mes, en
     * convención /30 (sector público). Devuelve {@link #DIAS_LAB_DEFAULT} (mes
     * completo) cuando el vínculo cubre todo el período; un valor 0..29 solo
     * cuando la fecha de inicio de contrato o la de cese caen dentro del período.
     * Así el mes de ingreso/cese se prorratea y los meses completos NO cambian
     * (cero regresión).
     */
    int diasVinculoEnPeriodo(EmpleadoPlanilla planilla, String periodo) {
        if (planilla == null || periodo == null) {
            return DIAS_LAB_DEFAULT;
        }
        LocalDate inicioPeriodo = ParametroRemunerativoService.periodoToFechaInicio(periodo);
        LocalDate finPeriodo = inicioPeriodo.withDayOfMonth(inicioPeriodo.lengthOfMonth());
        LocalDate inicioContrato = planilla.getFechaInicioContrato() != null
                ? planilla.getFechaInicioContrato() : planilla.getFechaInicio();
        LocalDate cese = planilla.getFechaCese();

        boolean altaIntraMes = inicioContrato != null
                && inicioContrato.isAfter(inicioPeriodo)
                && !inicioContrato.isAfter(finPeriodo);
        boolean ceseIntraMes = cese != null
                && !cese.isBefore(inicioPeriodo)
                && cese.isBefore(finPeriodo);

        if (!altaIntraMes && !ceseIntraMes) {
            return DIAS_LAB_DEFAULT; // mes completo → sin prorrateo
        }

        int diaInicio = altaIntraMes
                ? Math.min(inicioContrato.getDayOfMonth(), DIAS_LAB_DEFAULT) : 1;
        int diaFin = ceseIntraMes
                ? Math.min(cese.getDayOfMonth(), DIAS_LAB_DEFAULT) : DIAS_LAB_DEFAULT;
        int dias = diaFin - diaInicio + 1;
        if (dias < 0) dias = 0;
        if (dias > DIAS_LAB_DEFAULT) dias = DIAS_LAB_DEFAULT;
        return dias;
    }

    /**
     * Fase 3 — Base prorrateada por los días del vínculo dentro del período
     * (cese/alta intra-mes). Para meses completos devuelve la base intacta (cero
     * regresión). Fuente ÚNICA del prorrateo de la base: la usan tanto la
     * remuneración del cargo como la base del IR4ta CAS, para que ambas queden
     * consistentes (evita retener impuesto sobre un mes completo cuando se pagó
     * proporcional).
     */
    public BigDecimal prorratearBasePorVinculo(
            BigDecimal base, EmpleadoPlanilla planilla, String periodo) {
        if (base == null) {
            return BigDecimal.ZERO;
        }
        int diasVinc = diasVinculoEnPeriodo(planilla, periodo);
        return diasVinc < DIAS_LAB_DEFAULT ? prorratear(base, diasVinc) : base;
    }

    /**
     * F1.9 — Base tributaria de IR 4ta CAS prorrateada por días EFECTIVOS =
     * días de vínculo (cese/alta) − días de subsidio. Los subsidios de EsSalud
     * son INAFECTOS al Impuesto a la Renta (Art. 18 TUO LIR): la retención debe
     * calcularse ÚNICAMENTE sobre los haberes pagados por el empleador, nunca
     * sobre el monto subsidiado. Reutiliza el mismo contrato de dominio que el
     * haber ({@code diasSubsidioMotor}) → DRY con F1.8.
     *
     * <p>Cero regresión: sin subsidio {@code diasSubsidio=0 → diasHaber=diasVinc},
     * idéntico a {@link #prorratearBasePorVinculo}.</p>
     */
    public BigDecimal prorratearBaseTributariaPorVinculoYSubsidio(
            BigDecimal base, EmpleadoPlanilla planilla, String periodo) {
        if (base == null) {
            return BigDecimal.ZERO;
        }
        int diasVinc = diasVinculoEnPeriodo(planilla, periodo);
        int diasSubsidio = subsidioPlanillaIntegracionService
                .diasSubsidioMotor(planilla.getEmpleadoId(), periodo);
        int diasHaber = Math.max(0, diasVinc - diasSubsidio);
        return diasHaber < DIAS_LAB_DEFAULT ? prorratear(base, diasHaber) : base;
    }

    /**
     * F2.3 — Suma {@code diasAfectos} de los eventos del período del empleado
     * cuyo {@code TipoEvento.afectaDiasLaborados='S'}. Si la fila tiene
     * {@code diasAfectos} null → deriva de {@code fechaFin - fechaInicio + 1}.
     *
     * <p>Defensivo: si el repo devuelve {@code null} o lista vacía, retorna 0.</p>
     */
    private int sumarDiasAfectosEventos(Long empleadoId, String periodo) {
        int totalDistribucion = eventoDistribucionMesRepository
                .findTramosDiasLaboradosPorEmpleadoYPeriodo(empleadoId, periodo)
                .stream()
                .mapToInt(d -> d.getDiasSubsidio() != null ? d.getDiasSubsidio() : 0)
                .sum();

        return totalDistribucion + sumarDiasEventosNoSubsidiados(empleadoId, periodo);
    }

    /**
     * SPEC_VACACIONES F9.1 (LSG) — Días afectos de los eventos del período cuyo
     * tipo {@code afectaDiasLaborados='S'} y que <b>NO</b> tienen distribución de
     * subsidio (licencia sin goce, suspensión no subsidiada, permiso sin goce,
     * cese-evento). Es el subconjunto de {@link #sumarDiasAfectosEventos} que el
     * empleador <b>no remunera</b> y que, por tanto, reduce tanto los días
     * laborados persistidos como el <b>haber prorrateado</b> (PASO 3/5).
     *
     * <p>Aislado a propósito de los días de subsidio ({@code diasSubsidioMotor},
     * origen {@code SUBSIDIO_LIQUIDACION}) para no descontar dos veces: los
     * subsidiados los cubre EsSalud y ya reducen el haber por su propia vía.</p>
     */
    private int sumarDiasEventosNoSubsidiados(Long empleadoId, String periodo) {
        java.time.LocalDate inicio = ParametroRemunerativoService.periodoToFechaInicio(periodo);
        java.time.LocalDate fin = inicio.withDayOfMonth(inicio.lengthOfMonth());

        java.util.Set<Long> eventosConDistribucion = new java.util.HashSet<>(
                eventoDistribucionMesRepository.findEventoIdsConTramoEnPeriodo(
                        empleadoId, periodo));

        var eventos = empleadoEventoRepository
                .findVigentesParaMotor(empleadoId, periodo, inicio, fin);

        int totalEventos = 0;
        for (var e : eventos) {
            if (eventosConDistribucion.contains(e.getId())) {
                continue;
            }
            if (e.getDiasAfectos() != null) {
                totalEventos += e.getDiasAfectos();
            } else if (e.getFechaInicio() != null && e.getFechaFin() != null) {
                totalEventos += (int) (java.time.temporal.ChronoUnit.DAYS
                        .between(e.getFechaInicio(), e.getFechaFin()) + 1);
            }
        }
        return totalEventos;
    }

    /** P0 — true si el evento tiene desglose en más de un periodo de planilla. */
    private boolean eventoCruzaMeses(Long eventoId) {
        if (eventoId == null) {
            return false;
        }
        return eventoDistribucionMesRepository.countDistinctPeriodosByEmpleadoEventoId(eventoId) > 1;
    }

    /**
     * F1.4c Motor v3 PASO 5b — Calcula el monto de reintegro para un empleado
     * en un período, prorrateando una base remunerativa por los días de
     * reintegro vigentes en {@code INDECI_EMPLEADO_REINTEGRO}.
     *
     * <p>Fórmula: <code>reintegro = base / 30 × dias_reintegro</code>
     * (delega en {@link #prorratear(BigDecimal, int)}).</p>
     *
     * <p><b>Estado actual (F1.4 alcance acotado)</b>: este helper está listo
     * para invocarse pero el flujo {@link #generar(Long, String)} NO lo
     * llama todavía. La integración real se hace en F1.5 cuando:
     * <ul>
     *   <li>La base remunerativa pase a leerse de
     *       {@code EmpleadoPlanilla.MONTO_CONTRATO} (V010_36) en lugar del
     *       total de EmpleadoConcepto.</li>
     *   <li>El reintegro se grabe como detalle MEF independiente (CODIGO_MEF
     *       definitivo lo entrega RRHH — LEY-01).</li>
     * </ul></p>
     *
     * <p>TODO F1.5: cambiar la base a {@code MONTO_CONTRATO} puro y grabar
     * detalle con concepto MEF de reintegro.</p>
     *
     * @param empleadoId        empleado al que se evalúa el reintegro.
     * @param periodo           período "YYYYMM".
     * @param baseRemunerativa  base sobre la que prorratear. Tomada hoy de la
     *                          suma de EmpleadoConcepto remunerativos del PASO 5
     *                          (deuda técnica documentada).
     * @return monto del reintegro, o {@link BigDecimal#ZERO} si no hay
     *         reintegro vigente para el período.
     */
    public BigDecimal calcularReintegro(Long empleadoId, String periodo, BigDecimal baseRemunerativa) {
        return empleadoReintegroRepository
                .findByEmpleadoIdAndPeriodoAndActivo(empleadoId, periodo, 1)
                .map(r -> prorratear(baseRemunerativa, r.getDiasReintegro()))
                .orElse(BigDecimal.ZERO);
    }

    /**
     * F1.6 Motor v3 PASO 12 — Tope 45% UIT en la base de EsSalud SOLO para
     * régimen CAS (decisión RRHH C2 / 2026-05-31).
     *
     * <p>Sustento: SUNAT/MEF aplica un tope operativo al cálculo de EsSalud
     * sobre rentas CAS. Para 728, SERVIR y 276 NO hay tope (el cálculo usa
     * la base imponible completa, como hasta ahora).</p>
     *
     * <p>Fórmula:
     * <pre>
     *     si régimen = "CAS":  base_topeada = MIN(base, UIT × TOPE_ESSALUD_PCT_UIT)
     *     si otro régimen:    base_topeada = base
     * </pre></p>
     *
     * <p>Los parámetros vienen de {@code INDECI_PARAMETRO_REMUNERATIVO}:
     * <ul>
     *   <li>{@code TOPE_ESSALUD_PCT_UIT} (V010_37 = 0.45 para 2026).</li>
     *   <li>{@code UIT} (V010_03 = 5350 para 2026; ajustar cuando el MEF
     *       publique nuevo valor).</li>
     * </ul></p>
     *
     * <p>Defensivo: si {@code TOPE_ESSALUD_PCT_UIT} no está sembrado (caso
     * raro en BD legacy) → devuelve la base intacta (sin tope, sin
     * NegocioException — evita romper el cálculo si el parámetro está
     * pendiente de cargar).</p>
     *
     * <p>Package-private para tests directos sin necesidad de ejercer
     * {@link #generar(Long, String)} completo.</p>
     *
     * @param baseImponible          base de EsSalud antes del tope.
     * @param regimenLaboralCodigo   código del régimen (típicamente "276",
     *                               "728", "CAS", "SERVIR"). {@code null}
     *                               se trata como "no CAS" → sin tope.
     * @param anioFiscal             año para resolver los parámetros vigentes.
     * @return base topeada si aplica, o la original si no.
     */
    BigDecimal aplicarTopeEssaludCAS(
            BigDecimal baseImponible,
            String regimenLaboralCodigo,
            int anioFiscal) {

        if (baseImponible == null || baseImponible.signum() <= 0) {
            return baseImponible == null ? BigDecimal.ZERO : baseImponible;
        }
        if (!esRegimenCas(regimenLaboralCodigo)) {
            return baseImponible;
        }

        BigDecimal topeFactor = parametroService
                .obtenerValorOpcional("TOPE_ESSALUD_PCT_UIT", anioFiscal, null)
                .orElse(BigDecimal.ZERO);
        if (topeFactor.signum() == 0) {
            return baseImponible; // Defensa: parámetro pendiente de cargar.
        }

        BigDecimal uit = parametroService.obtenerValor("UIT", anioFiscal, null);
        BigDecimal topeBase = uit.multiply(topeFactor);
        return baseImponible.min(topeBase);
    }

    /**
     * F1.7 Motor v3 PASO 9bis — Retención IR 4ta categoría SOLO régimen CAS
     * (decisión RRHH C1 / 2026-05-31).
     *
     * <p>Sustento: Excel CAS consolidado del cliente + Anexo 2 SUNAT. CAS
     * tributa 4ta, no 5ta (corrige memoria histórica errónea que decía CAS=5ta).
     * Otros regímenes (276/728/SERVIR) NO tributan 4ta — esos retienen 5ta
     * vía {@link #calcular5taCategoria}.</p>
     *
     * <p>Fórmula del Excel (§17.7 SPEC):
     * <pre>
     *     INDICADOR_4TA =
     *         SI tieneSuspension4ta        → "SUSPENSION" → IR_4TA = 0
     *         SI base ≤ BASE_INAFECTA_IR4TA → "INAFECTO"   → IR_4TA = 0
     *         resto                        → "NORMAL"     → IR_4TA = base × TASA_IR4TA
     * </pre></p>
     *
     * <p>Parámetros vienen de {@code INDECI_PARAMETRO_REMUNERATIVO} (V010_37):
     * <ul>
     *   <li>{@code BASE_INAFECTA_IR4TA} = 1500.00 PEN (2026).</li>
     *   <li>{@code TASA_IR4TA} = 0.08 PCT (8%).</li>
     * </ul></p>
     *
     * <p><b>Estado actual (F1.7 alcance acotado)</b>:
     * <ul>
     *   <li>{@code tieneSuspension4ta} llega como parámetro y hoy se invoca
     *       siempre con {@code false}. RRHH definirá el modelo (¿columna en
     *       {@code EmpleadoPension}? ¿tabla {@code INDECI_SUSPENSION_4TA}?)
     *       en una fase posterior.</li>
     *   <li>El helper devuelve el monto pero NO graba detalle MEF — el motor
     *       lo suma a {@code totalDescuentos} sin línea propia. F1.5b o una
     *       fase de "presentación" agregará la línea cuando RRHH entregue
     *       CODIGO_MEF de "Retención IR 4ta CAS".</li>
     * </ul></p>
     *
     * <p>Defensivo: si los parámetros no están en BD (caso legacy) devuelve
     * {@link BigDecimal#ZERO} sin lanzar — el cálculo de planilla no se
     * bloquea por un parámetro faltante.</p>
     *
     * <p>Package-private para tests directos.</p>
     *
     * @param baseImponible         base remunerativa afecta (típicamente
     *                              {@code baseImponiblePens} del motor).
     * @param regimenLaboralCodigo  código del régimen ("CAS", "1057", "728", etc.).
     *                              Solo aplica para CAS.
     * @param anioFiscal            año para resolver parámetros vigentes.
     * @param tieneSuspension4ta    {@code true} si el empleado tiene constancia
     *                              de suspensión de retenciones SUNAT vigente.
     * @return monto de IR 4ta a retener, {@link BigDecimal#ZERO} si no aplica.
     */
    public BigDecimal calcular4taCategoriaCAS(
            BigDecimal baseImponible,
            String regimenLaboralCodigo,
            int anioFiscal,
            boolean tieneSuspension4ta) {

        if (!esRegimenCas(regimenLaboralCodigo)) {
            return BigDecimal.ZERO;
        }
        if (baseImponible == null || baseImponible.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        if (tieneSuspension4ta) {
            return BigDecimal.ZERO;
        }

        // V010_76: preferir config anual dedicada; fallback a TBL_PARAMETRO_REMUNERATIVO.
        // La config anual almacena tasa como porcentaje (ej. 8.00).
        // El parametroService histórico almacena tasa como fracción (ej. 0.08).
        java.util.Optional<Ir4taConfigAnual> configOpt =
                ir4taConfigService.resolverPorAnio(anioFiscal,
                        LocalDate.of(anioFiscal, 6, 15));

        if (configOpt.isPresent()) {
            Ir4taConfigAnual cfg = configOpt.get();
            BigDecimal baseInafecta = cfg.getBaseInafectaIr4ta();
            BigDecimal tasaPct      = cfg.getTasaIr4ta();
            if (baseInafecta == null || tasaPct == null) return BigDecimal.ZERO;
            if (baseImponible.compareTo(baseInafecta) <= 0) return BigDecimal.ZERO;
            return baseImponible.multiply(tasaPct)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }

        // Fallback: TBL_PARAMETRO_REMUNERATIVO (tasa almacenada como fracción).
        BigDecimal baseInafecta = parametroService
                .obtenerValorOpcional("BASE_INAFECTA_IR4TA", anioFiscal, null)
                .orElse(null);
        if (baseInafecta == null) return BigDecimal.ZERO;
        if (baseImponible.compareTo(baseInafecta) <= 0) return BigDecimal.ZERO;

        BigDecimal tasaFrac = parametroService
                .obtenerValorOpcional("TASA_IR4TA", anioFiscal, null)
                .orElse(null);
        if (tasaFrac == null) return BigDecimal.ZERO;

        return baseImponible.multiply(tasaFrac).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * F1.5b — Verifica si un concepto cuyo {@code REGIMEN_APLICABLE} es
     * {@code regimenAplicableCsv} aplica al empleado cuyo régimen laboral es
     * {@code regimenEmpleadoCodigo}.
     *
     * <p>Soporta los formatos del catálogo INDECI_CONCEPTO_PLANILLA:
     * <ul>
     *   <li>Valor único: "728" — el concepto aplica solo a 728.</li>
     *   <li>CSV: "728,1057" — el concepto aplica a 728 y a 1057 (decretos
     *       supremos del pacto colectivo MEF).</li>
     *   <li>"TODOS" — el concepto aplica a cualquier régimen.</li>
     *   <li>{@code null} o vacío — interpretado como "TODOS" (compatibilidad
     *       con conceptos legacy sin metadata cargada).</li>
     * </ul></p>
     *
     * <p>Parseo: se admite separador "," con o sin espacios alrededor; cada
     * elemento se compara case-insensitive.</p>
     *
     * <p>Defensivo: si {@code regimenEmpleadoCodigo} es null → devuelve true
     * (no bloquear cálculo por dato faltante; ya validan otros pasos).</p>
     *
     * <p>Package-private static para tests directos sin Spring.</p>
     *
     * @return true si el concepto aplica al régimen del empleado.
     */
    static boolean regimenAplicaConcepto(
            String regimenAplicableCsv,
            String regimenEmpleadoCodigo) {
        // Fuente única de la regla (incluye alias CAS≡1057). Ver RegimenAplicableHelper.
        return RegimenAplicableHelper.aplica(regimenAplicableCsv, regimenEmpleadoCodigo);
    }

    public static boolean esRegimenCas(String regimenLaboralCodigo) {
        if (regimenLaboralCodigo == null) {
            return false;
        }
        String codigo = regimenLaboralCodigo.trim().toUpperCase();
        return REG_LABORAL_CAS.equals(codigo) || REG_LABORAL_CAS_1057.equals(codigo);
    }

    /**
     * true si el código corresponde al régimen SERVIR (Ley 30057). Acepta el
     * código canónico de {@code INDECI_REGIMEN_LABORAL.CODIGO} ("30057") y el
     * alias textual "SERVIR" usado en catálogos MEF/SUNAT. Mismo criterio que
     * {@link #esRegimenCas} (que unifica "CAS" ≡ "1057").
     */
    public static boolean esRegimenServir(String regimenLaboralCodigo) {
        if (regimenLaboralCodigo == null) {
            return false;
        }
        String codigo = regimenLaboralCodigo.trim().toUpperCase();
        return REG_LABORAL_SERVIR.equals(codigo) || REG_LABORAL_SERVIR_30057.equals(codigo);
    }
}
