package com.indeci.rrhh.service;

import com.indeci.exception.ConceptoRegimenNoAplicableException;
import com.indeci.exception.ConceptoSinCodigoMefException;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.GeneracionFallidaDto;
import com.indeci.rrhh.dto.GeneracionMasivaResultDto;
import com.indeci.rrhh.dto.ResumenPlanillaDto;
import com.indeci.rrhh.entity.*;
import com.indeci.rrhh.repository.*;

import lombok.RequiredArgsConstructor;

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
    private static final String REG_LABORAL_SERVIR = "SERVIR";

    // Coincide con INDECI_REGIMEN_PENSIONARIO.TIPO (no CODIGO — ese trae la AFP).
    private static final String REG_PENS_ONP = "ONP";
    private static final String REG_PENS_AFP = "AFP";

    private static final BigDecimal CIEN    = new BigDecimal("100");
    private static final BigDecimal DOCE    = new BigDecimal("12");
    private static final BigDecimal SIETE   = new BigDecimal("7");
    private static final BigDecimal MEDIO   = new BigDecimal("0.5");
    /** F1.3c — Divisor fijo del prorrateo mensual (días calendar SUNAT/MEF). */
    private static final BigDecimal TREINTA = new BigDecimal("30");

    /**
     * F1.3b — Días estándar por defecto cuando {@link EmpleadoPlanilla} no
     * tiene aún cargado el campo {@code DIAS_LABORADOS_DEFAULT} (V010_36).
     * El motor lee este valor hasta que F1.5 conecte el campo real.
     */
    private static final int DIAS_LAB_DEFAULT = 30;

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

    // ======================================================================
    // ENTRY POINT
    // ======================================================================

    @Transactional
    public void generar(Long empleadoId, String periodo) {

        // 1. Validar periodo
        PeriodoPlanilla periodoPlanilla = periodoRepository
                .findByPeriodoAndActivo(periodo, 1)
                .orElseThrow(() -> new NegocioException("Periodo no existe"));
        if ("CERRADO".equalsIgnoreCase(periodoPlanilla.getEstado())) {
            throw new NegocioException("El periodo " + periodo + " está cerrado");
        }

        // 2. Configuración planilla del empleado
        EmpleadoPlanilla planilla = planillaRepository
                .findFirstByEmpleadoIdAndActivo(empleadoId, 1)
                .orElseThrow(() -> new NegocioException(
                        "Empleado sin configuración planilla"));

        // 3. Pensión vigente (opcional — sin pensión, no se calcula aporte)
        Optional<EmpleadoPension> pensionOpt =
                empleadoPensionRepository.findFirstByEmpleadoIdAndActivo(empleadoId, 1);

        // 3b. Empleado (para HAS_EPS — split ESSALUD §5.5). Opcional.
        Empleado empleado = empleadoRepository.findById(empleadoId).orElse(null);

        int anioFiscal = anioDePeriodo(periodo);

        // 4. Borrar movimiento anterior + crear cabecera
        borrarMovimientoAnterior(empleadoId, periodo);
        MovimientoPlanilla movimiento = crearCabecera(empleadoId, periodo);

        BigDecimal totalIngresos    = BigDecimal.ZERO;
        BigDecimal totalDescuentos  = BigDecimal.ZERO;
        BigDecimal baseImponiblePens = BigDecimal.ZERO;
        BigDecimal baseImponibleEss  = BigDecimal.ZERO;

        // 5. Conceptos remunerativos calculados por el motor
        RemunerativosResult rem =
                calcularRemunerativos(movimiento, planilla, anioFiscal);
        totalIngresos     = totalIngresos.add(rem.totalRemunerativo);
        baseImponiblePens = baseImponiblePens.add(rem.baseAportePension);
        baseImponibleEss  = baseImponibleEss.add(rem.baseEssalud);

        // 6. No remunerativos automáticos (Etapa 1: vacío. Aguinaldos/gratificaciones a Etapa 2)
        // calcularNoRemunerativos(movimiento, planilla, periodo); // placeholder

        // Régimen laboral (necesario para PASO 7 validación normativa F1.5b
        // y PASO 9 tope EsSalud CAS F1.6). Se resuelve una sola vez.
        String regimenLaboralCodigo =
                resolverRegimenLaboralCodigo(planilla.getRegimenLaboralId());

        // 7. Conceptos manuales (EmpleadoConcepto) — valida LEY-01 + F1.5b:
        //    régimen aplicable del concepto + prorrateo por días laborados.
        ManualesResult manuales =
                aplicarConceptosManuales(movimiento, planilla, regimenLaboralCodigo, periodo);
        totalIngresos     = totalIngresos.add(manuales.ingresos);
        totalDescuentos   = totalDescuentos.add(manuales.descuentos);
        baseImponiblePens = baseImponiblePens.add(manuales.baseAportePension);
        baseImponibleEss  = baseImponibleEss.add(manuales.baseEssalud);

        // 7.5. F1.5a Motor v3 — Reintegro por días (feature flag).
        //      Conceptualmente es el PASO 5b del SPEC, pero en el motor actual
        //      se aplica DESPUÉS del PASO 7 porque la base remunerativa real
        //      vive en EmpleadoConcepto manuales (deuda técnica documentada).
        //
        //      Si motor.v3.prorrateo.enabled=true y existe INDECI_EMPLEADO_REINTEGRO
        //      vigente para el período → prorratea totalIngresos por los días de
        //      reintegro y suma el monto al total + bases (pensión/ESSALUD).
        //
        //      F1.5b conectará MONTO_CONTRATO real e incrementos DS prorrateados
        //      vía flag ES_PRORRATEABLE en INDECI_CONCEPTO_PLANILLA, y grabará
        //      detalle MEF independiente (RRHH debe dar CODIGO_MEF de "Reintegro CAS").
        //      Hoy el monto queda embebido en totalIngresos sin línea propia en
        //      boleta — el cálculo es correcto, la presentación es F1.5b.
        if (motorV3ProrrateoEnabled) {
            BigDecimal reintegro =
                    calcularReintegro(empleadoId, periodo, totalIngresos);
            if (reintegro.signum() > 0) {
                totalIngresos     = totalIngresos.add(reintegro);
                baseImponiblePens = baseImponiblePens.add(reintegro);
                baseImponibleEss  = baseImponibleEss.add(reintegro);
            }
        }

        // 7b. Descuento de asistencia (PASO 7) — tardanzas + faltas de la
        //     asistencia VALIDADA del período (D.Leg. 276 Art. 24). No reduce
        //     la base imponible: es una deducción sobre el bruto.
        BigDecimal descuentoAsistencia =
                calcularDescuentoAsistencia(movimiento, empleadoId, periodo);
        totalDescuentos = totalDescuentos.add(descuentoAsistencia);

        // 8. Aporte pensionario (ONP/AFP) — LEY-02: ESSALUD NUNCA aquí
        // (regimenLaboralCodigo ya resuelto antes del PASO 7 para F1.5b)
        BigDecimal aportePension = BigDecimal.ZERO;
        if (pensionOpt.isPresent()) {
            aportePension = calcularAportePensionario(
                    movimiento, pensionOpt.get(), baseImponiblePens, anioFiscal);
            totalDescuentos = totalDescuentos.add(aportePension);
        }

        // 8b. Retención 5ta categoría (BW) — LEY-03: solo régimen 728 y SERVIR
        BigDecimal retencion5ta = calcular5taCategoria(
                movimiento, planilla, baseImponiblePens, anioFiscal);
        totalDescuentos = totalDescuentos.add(retencion5ta);

        // 8c. F1.7 Motor v3 PASO 9bis — IR 4ta categoría SOLO régimen CAS
        //     (decisión RRHH C1 / 2026-05-31). Sustituye al PASO 8b para CAS:
        //     LEY-03 dice que CAS NO retiene 5ta; en su lugar, retiene 4ta al 8%
        //     cuando la base supera el inafecto (1500) y no tiene constancia
        //     de suspensión SUNAT. Aplicar bajo feature flag motor v3, como F1.5a.
        //
        //     TODO F1.7b/c: leer "tieneSuspension4ta" de BD (modelo aún por
        //     definir con RRHH — posible columna en EmpleadoPension o tabla
        //     INDECI_SUSPENSION_RETENCION_SUNAT). Hoy default false.
        //     TODO presentación: grabar detalle MEF con CODIGO_MEF de
        //     "Retención IR 4ta CAS" cuando RRHH lo entregue. Hoy embebido
        //     en totalDescuentos sin línea propia en boleta.
        if (motorV3ProrrateoEnabled) {
            BigDecimal ir4taCas = calcular4taCategoriaCAS(
                    baseImponiblePens, regimenLaboralCodigo, anioFiscal,
                    /*tieneSuspension4ta*/ false);
            if (ir4taCas.signum() > 0) {
                totalDescuentos = totalDescuentos.add(ir4taCas);
            }
        }

        // 9. ESSALUD empleador con mínimo + split EPS + tope 45% UIT solo CAS (F1.6).
        //    El copago EPS del trabajador (si tiene EPS) SÍ es descuento — se suma al total.
        BigDecimal copagoEps = calcularEssaludEmpleador(
                movimiento, empleado, baseImponibleEss, anioFiscal, regimenLaboralCodigo);
        totalDescuentos = totalDescuentos.add(copagoEps);

        // 10. Totales, validación neto 50% (REGLA SERVIR-07) y persistencia
        calcularTotalesYCUC(movimiento, totalIngresos, totalDescuentos,
                retencion5ta, aportePension);

        // 16. Conciliación AIRHSP automática (PASO 16): compara el monto del
        //     sistema (remuneración bruta calculada) contra el AIRHSP_MONTO
        //     registrado en el MEF. Lo consume la PANTALLA-06.
        crearConciliacionAirhsp(movimiento, empleado, periodoPlanilla, totalIngresos);
    }

    // ======================================================================
    // PASO 5 — REMUNERATIVOS automáticos (asignación familiar)
    // ======================================================================

    private RemunerativosResult calcularRemunerativos(
            MovimientoPlanilla movimiento,
            EmpleadoPlanilla planilla,
            int anioFiscal) {

        RemunerativosResult result = new RemunerativosResult();

        // Asignación familiar (régimen 728 o CAS)
        if (planilla.getTieneAsignacionFamiliar() != null
                && planilla.getTieneAsignacionFamiliar() == 1
                && planilla.getRegimenLaboralId() != null) {

            String regimenCodigo = resolverRegimenLaboralCodigo(planilla.getRegimenLaboralId());

            String mefAsig = null;
            if (REG_LABORAL_728.equals(regimenCodigo)) mefAsig = MEF_ASIG_FAMILIAR_728;
            if (REG_LABORAL_CAS.equals(regimenCodigo)) mefAsig = MEF_ASIG_FAMILIAR_CAS;

            if (mefAsig != null) {
                BigDecimal monto = parametroService.obtenerValor(
                        "ASIG_FAMILIAR", anioFiscal, null);
                ConceptoPlanilla concepto = conceptoPorMef(mefAsig);
                grabarDetalle(movimiento.getId(), concepto, monto,
                        "Asignación familiar (" + regimenCodigo + ")");
                acumular(result, concepto, monto);
            }
        }

        // El sueldo básico NO se suma aquí: el motor toma los ingresos
        // remunerativos exclusivamente de EmpleadoConcepto (ver
        // aplicarConceptosManuales). EmpleadoPlanilla.sueldoBasico se usa solo
        // como base para conceptos manuales calculados por porcentaje.
        return result;
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

    private ManualesResult aplicarConceptosManuales(
            MovimientoPlanilla movimiento,
            EmpleadoPlanilla planilla,
            String regimenLaboralCodigo,
            String periodo) {

        ManualesResult result = new ManualesResult();
        BigDecimal sueldoBasico = toBigDecimal(planilla.getSueldoBasico());

        // F1.5b — días laborados se calculan UNA VEZ por empleado/período;
        // se reusan en cada EmpleadoConcepto prorrateable. Solo se calcula
        // si el flag está ON (sino no se usa).
        int diasLab = motorV3ProrrateoEnabled
                ? calcularDiasLaborados(planilla.getEmpleadoId(), periodo)
                : DIAS_LAB_DEFAULT;

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

            // F1.5b — Si el concepto es prorrateable y el flag está ON,
            // prorratear el monto por días laborados (helper F1.3c).
            if (motorV3ProrrateoEnabled
                    && "S".equalsIgnoreCase(concepto.getEsProrrateable())) {
                monto = prorratear(monto, diasLab);
            }

            grabarDetalle(movimiento.getId(), concepto, monto, concepto.getNombre());

            String tipo = resolverTipoConcepto(concepto);
            switch (tipo) {
                case "REMUNERATIVO", "NO_REMUNERATIVO" -> {
                    result.ingresos = result.ingresos.add(monto);
                    if ("S".equalsIgnoreCase(concepto.getAfectoAportePens())) {
                        result.baseAportePension = result.baseAportePension.add(monto);
                    }
                    if ("S".equalsIgnoreCase(concepto.getAfectoEssalud())) {
                        result.baseEssalud = result.baseEssalud.add(monto);
                    }
                }
                case "DESCUENTO", "APORTE_TRABAJADOR" ->
                    result.descuentos = result.descuentos.add(monto);
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
    private BigDecimal calcularDescuentoAsistencia(
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

        return total;
    }

    // ======================================================================
    // PASO 8 — APORTE PENSIONARIO (ONP / AFP)
    // ======================================================================

    private BigDecimal calcularAportePensionario(
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

        BigDecimal totalAporte = BigDecimal.ZERO;

        if (REG_PENS_ONP.equalsIgnoreCase(tipoRegimen)) {
            BigDecimal tasa = primeraTasaNoNula(
                    pension.getPorcentajeAporte(),
                    () -> parametroService.obtenerValor("TASA_ONP", anioFiscal, null));
            BigDecimal monto = baseImponible.multiply(tasa).setScale(2, RoundingMode.HALF_UP);
            grabarDetalle(movimiento.getId(), conceptoPorMef(MEF_APORTE_ONP), monto,
                    "Aporte ONP " + porcentajeTexto(tasa));
            totalAporte = totalAporte.add(monto);

        } else if (REG_PENS_AFP.equalsIgnoreCase(tipoRegimen)) {
            BigDecimal tasa = primeraTasaNoNula(
                    pension.getPorcentajeAporte(),
                    () -> parametroService.obtenerValor("TASA_AFP_APORTE", anioFiscal, null));
            BigDecimal aporte = baseImponible.multiply(tasa).setScale(2, RoundingMode.HALF_UP);
            grabarDetalle(movimiento.getId(), conceptoPorMef(MEF_APORTE_AFP), aporte,
                    "Aporte AFP " + porcentajeTexto(tasa));
            totalAporte = totalAporte.add(aporte);

            // Comisión AFP: varía por AFP (§16.3). Solo si está cargada en
            // EmpleadoPension. primeraTasaNoNula normaliza el % (ej. 1.5 -> 0.015).
            BigDecimal comisionTasa = primeraTasaNoNula(
                    pension.getPorcentajeComision(), () -> BigDecimal.ZERO);
            if (comisionTasa.signum() > 0) {
                BigDecimal monto = baseImponible.multiply(comisionTasa)
                        .setScale(2, RoundingMode.HALF_UP);
                grabarDetalle(movimiento.getId(), conceptoPorMef(MEF_COMISION_AFP), monto,
                        "Comisión AFP " + porcentajeTexto(comisionTasa));
                totalAporte = totalAporte.add(monto);
            }

            // Prima de seguro AFP CON TOPE (SPEC §5.6):
            //   base  = MIN(baseImponible, TOPE_SEGURO_AFP)
            //   prima = tasa cargada en EmpleadoPension o, si no, PRIMA_AFP global
            //           (§16.3: la prima 1.37% es igual en todas las AFP).
            BigDecimal primaTasa = primeraTasaNoNula(
                    pension.getPorcentajeSeguro(),
                    () -> parametroService.obtenerValor("PRIMA_AFP", anioFiscal, null));
            if (primaTasa.signum() > 0) {
                BigDecimal tope = parametroService.obtenerValor(
                        "TOPE_SEGURO_AFP", anioFiscal, null);
                BigDecimal baseSeguro = baseImponible.min(tope);
                BigDecimal montoSeguro = baseSeguro.multiply(primaTasa)
                        .setScale(2, RoundingMode.HALF_UP);
                boolean conTope = baseImponible.compareTo(tope) > 0;
                grabarDetalle(movimiento.getId(), conceptoPorMef(MEF_SEGURO_AFP), montoSeguro,
                        "Prima seguro AFP " + porcentajeTexto(primaTasa)
                                + (conTope ? " (base topada)" : ""));
                totalAporte = totalAporte.add(montoSeguro);
            }
        }

        return totalAporte;
    }

    // ======================================================================
    // PASO 8b — RETENCIÓN 5TA CATEGORÍA (BW — SPEC §5.7 / LEY-03)
    // ======================================================================

    /**
     * Calcula el BW (IR 5ta sobre la remuneración mensual). LEY-03: aplica
     * SOLO a régimen 728 y SERVIR — para 276 y CAS retorna ZERO siempre.
     *
     * <p>Proyección lineal simple: {@code rentaBrutaAnual = baseRemuneracion × 12}.
     * El refinamiento con gratificaciones, aguinaldos y retenido acumulado, y
     * el BX del aguinaldo (§5.7), corresponde a Etapa 3.
     *
     * <p>{@code rentaNeta = MAX(0, rentaBrutaAnual − 7×UIT)}. Sobre la renta
     * neta se aplica la escala progresiva del TUO LIR (§16.2).
     * {@code BW mensual = impuestoAnual / 12}.
     *
     * @return el BW mensual (descuento al trabajador); ZERO si no aplica.
     */
    private BigDecimal calcular5taCategoria(
            MovimientoPlanilla movimiento,
            EmpleadoPlanilla planilla,
            BigDecimal baseRemuneracion,
            int anioFiscal) {

        if (baseRemuneracion.signum() <= 0 || planilla.getRegimenLaboralId() == null) {
            return BigDecimal.ZERO;
        }

        // LEY-03 — 5ta categoría aplica únicamente a 728 y SERVIR.
        String regimen = resolverRegimenLaboralCodigo(planilla.getRegimenLaboralId());
        if (!REG_LABORAL_728.equals(regimen) && !REG_LABORAL_SERVIR.equals(regimen)) {
            return BigDecimal.ZERO;
        }

        BigDecimal uit = parametroService.obtenerValor("UIT", anioFiscal, null);
        BigDecimal deduccion = uit.multiply(SIETE);

        BigDecimal rentaBrutaAnual = baseRemuneracion.multiply(DOCE);
        BigDecimal rentaNeta = rentaBrutaAnual.subtract(deduccion);
        if (rentaNeta.signum() <= 0) {
            return BigDecimal.ZERO; // bajo las 7 UIT — exonerado
        }

        BigDecimal impuestoAnual = aplicarEscalaProgresiva5ta(rentaNeta, uit, anioFiscal);
        BigDecimal bwMensual = impuestoAnual.divide(DOCE, 2, RoundingMode.HALF_UP);
        if (bwMensual.signum() <= 0) {
            return BigDecimal.ZERO;
        }

        grabarDetalle(movimiento.getId(), conceptoPorMef(MEF_RETENCION_5TA), bwMensual,
                "Retención IR 5ta categoría (BW)");
        return bwMensual;
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
    private BigDecimal calcularEssaludEmpleador(
            MovimientoPlanilla movimiento,
            Empleado empleado,
            BigDecimal baseImponible,
            int anioFiscal,
            String regimenLaboralCodigo) {

        if (baseImponible.signum() <= 0) return BigDecimal.ZERO;

        // F1.6 — Tope 45% UIT EsSalud SOLO para CAS (decisión RRHH C2 / 2026-05-31).
        // 728, SERVIR y 276 no se topean: sin regresión respecto al motor v2.
        baseImponible =
                aplicarTopeEssaludCAS(baseImponible, regimenLaboralCodigo, anioFiscal);

        BigDecimal tasaEssalud = parametroService.obtenerValor("TASA_ESSALUD", anioFiscal, null);
        BigDecimal minimo      = parametroService.obtenerValor("ESSALUD_MINIMO", anioFiscal, null);

        // essaludBase = MAX(base*9%, mínimo) — equivalente al IF(remun<=1130) del Excel.
        BigDecimal essaludBase = baseImponible.multiply(tasaEssalud)
                .max(minimo)
                .setScale(2, RoundingMode.HALF_UP);

        boolean tieneEps = empleado != null && "S".equalsIgnoreCase(empleado.getHasEps());

        if (!tieneEps) {
            // 9% completo al empleador — informativo, NO descuenta (LEY-02).
            grabarDetalle(movimiento.getId(), conceptoPorMef(MEF_ESSALUD), essaludBase,
                    "ESSALUD empleador " + porcentajeTexto(tasaEssalud));
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

        // El copago SÍ es descuento al trabajador.
        return montoCopago;
    }

    // ======================================================================
    // PASO 10 — TOTALES + validación neto 50% (REGLA SERVIR-07 / §5.4)
    // ======================================================================

    /**
     * Persiste los totales del movimiento y aplica la validación neto 50%.
     *
     * <p>SPEC §5.4: {@code umbral = (remun − ir5ta − aporte_pension − judicial) × 0.5}.
     * Si {@code neto >= umbral} → ESTADO_NETO = 'BIEN'; si no → 'NETO_NO_VA'.
     * El componente judicial es 0 en Etapa 2 (M07 descuentos judiciales aún no
     * implementado) — queda explícito para incorporarlo después.
     *
     * <p>El motor MARCA el estado; no aborta la generación (igual que el Excel,
     * "NETO NO VA" es una etiqueta). El bloqueo efectivo corresponde al flujo
     * de aprobación de planilla: no se aprueba con un movimiento en NETO_NO_VA.
     */
    private void calcularTotalesYCUC(
            MovimientoPlanilla movimiento,
            BigDecimal totalIngresos,
            BigDecimal totalDescuentos,
            BigDecimal retencion5ta,
            BigDecimal aportePension) {

        BigDecimal neto = totalIngresos.subtract(totalDescuentos)
                .setScale(2, RoundingMode.HALF_UP);

        movimiento.setTotalIngresos(totalIngresos.setScale(2, RoundingMode.HALF_UP).doubleValue());
        movimiento.setTotalDescuentos(totalDescuentos.setScale(2, RoundingMode.HALF_UP).doubleValue());
        movimiento.setNetoPagar(neto.doubleValue());

        // Validación neto 50% — REGLA SERVIR-07 (§5.4).
        BigDecimal judicial = BigDecimal.ZERO; // M07 aún no implementado (Etapa 2)
        BigDecimal umbral = totalIngresos
                .subtract(retencion5ta)
                .subtract(aportePension)
                .subtract(judicial)
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
        movimientoRepository.save(movimiento);
    }

    // ======================================================================
    // HELPERS
    // ======================================================================

    private void borrarMovimientoAnterior(Long empleadoId, String periodo) {
        movimientoRepository
                .findByEmpleadoIdAndPeriodoAndActivo(empleadoId, periodo, 1)
                .ifPresent(mov -> {
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
    private void crearConciliacionAirhsp(
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

    private MovimientoPlanilla crearCabecera(Long empleadoId, String periodo) {
        MovimientoPlanilla movimiento = new MovimientoPlanilla();
        movimiento.setEmpleadoId(empleadoId);
        movimiento.setPeriodo(periodo);
        movimiento.setObservacion("PLANILLA GENERADA");
        movimiento.setActivo(1);
        movimiento.setEstado("GENERADO");
        movimiento.setCreatedAt(LocalDateTime.now());
        movimiento.setTotalIngresos(0.0);
        movimiento.setTotalDescuentos(0.0);
        movimiento.setNetoPagar(0.0);
        return movimientoRepository.save(movimiento);
    }

    private MovimientoPlanillaDetalle grabarDetalle(
            Long movimientoId, ConceptoPlanilla concepto,
            BigDecimal monto, String observacion) {
        MovimientoPlanillaDetalle det = new MovimientoPlanillaDetalle();
        det.setMovimientoPlanillaId(movimientoId);
        det.setConceptoPlanillaId(concepto.getId());
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

    private String resolverRegimenLaboralCodigo(Long regimenLaboralId) {
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

    private BigDecimal toBigDecimal(Double valor) {
        return valor == null ? BigDecimal.ZERO : BigDecimal.valueOf(valor);
    }

    private BigDecimal primeraTasaNoNula(Double explicita, java.util.function.Supplier<BigDecimal> fallback) {
        if (explicita != null && explicita > 0) {
            // Si el valor explícito viene como porcentaje (ej. 13.0), convertirlo a fracción
            BigDecimal v = BigDecimal.valueOf(explicita);
            if (v.compareTo(BigDecimal.ONE) > 0) {
                return v.divide(CIEN, 6, RoundingMode.HALF_UP);
            }
            return v;
        }
        return fallback.get();
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
    public GeneracionMasivaResultDto generarTodoPeriodo(String periodo) {
        List<EmpleadoPlanilla> empleados = planillaRepository.findByActivo(1);
        List<GeneracionFallidaDto> fallidos = new ArrayList<>();
        int exitosos = 0;

        for (EmpleadoPlanilla planilla : empleados) {
            try {
                self.generar(planilla.getEmpleadoId(), periodo);
                exitosos++;
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

    private static final class RemunerativosResult {
        BigDecimal totalRemunerativo = BigDecimal.ZERO;
        BigDecimal baseAportePension = BigDecimal.ZERO;
        BigDecimal baseEssalud       = BigDecimal.ZERO;
    }

    private static final class ManualesResult {
        BigDecimal ingresos          = BigDecimal.ZERO;
        BigDecimal descuentos        = BigDecimal.ZERO;
        BigDecimal baseAportePension = BigDecimal.ZERO;
        BigDecimal baseEssalud       = BigDecimal.ZERO;
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

        return Math.max(0, DIAS_LAB_DEFAULT - diasFalta - diasEventos);
    }

    /**
     * F2.3 — Suma {@code diasAfectos} de los eventos del período del empleado
     * cuyo {@code TipoEvento.afectaDiasLaborados='S'}. Si la fila tiene
     * {@code diasAfectos} null → deriva de {@code fechaFin - fechaInicio + 1}.
     *
     * <p>Defensivo: si el repo devuelve {@code null} o lista vacía, retorna 0.</p>
     */
    private int sumarDiasAfectosEventos(Long empleadoId, String periodo) {
        java.time.LocalDate inicio = ParametroRemunerativoService.periodoToFechaInicio(periodo);
        java.time.LocalDate fin = inicio.withDayOfMonth(inicio.lengthOfMonth());

        var eventos = empleadoEventoRepository
                .findVigentesParaMotor(empleadoId, periodo, inicio, fin);

        int total = 0;
        for (var e : eventos) {
            if (e.getDiasAfectos() != null) {
                total += e.getDiasAfectos();
            } else if (e.getFechaInicio() != null && e.getFechaFin() != null) {
                // Derivar: días naturales del rango.
                total += (int) (java.time.temporal.ChronoUnit.DAYS
                        .between(e.getFechaInicio(), e.getFechaFin()) + 1);
            }
        }
        return total;
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
    BigDecimal calcularReintegro(Long empleadoId, String periodo, BigDecimal baseRemunerativa) {
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
        if (!REG_LABORAL_CAS.equals(regimenLaboralCodigo)) {
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
     * @param regimenLaboralCodigo  código del régimen ("CAS", "728", etc.).
     *                              Solo aplica si es exactamente "CAS".
     * @param anioFiscal            año para resolver parámetros vigentes.
     * @param tieneSuspension4ta    {@code true} si el empleado tiene constancia
     *                              de suspensión de retenciones SUNAT vigente.
     * @return monto de IR 4ta a retener, {@link BigDecimal#ZERO} si no aplica.
     */
    BigDecimal calcular4taCategoriaCAS(
            BigDecimal baseImponible,
            String regimenLaboralCodigo,
            int anioFiscal,
            boolean tieneSuspension4ta) {

        if (!REG_LABORAL_CAS.equals(regimenLaboralCodigo)) {
            return BigDecimal.ZERO;
        }
        if (baseImponible == null || baseImponible.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        if (tieneSuspension4ta) {
            return BigDecimal.ZERO;
        }

        BigDecimal baseInafecta = parametroService
                .obtenerValorOpcional("BASE_INAFECTA_IR4TA", anioFiscal, null)
                .orElse(null);
        if (baseInafecta == null) {
            return BigDecimal.ZERO; // Defensa: parámetro pendiente de cargar.
        }
        if (baseImponible.compareTo(baseInafecta) <= 0) {
            return BigDecimal.ZERO; // INAFECTO.
        }

        BigDecimal tasa = parametroService
                .obtenerValorOpcional("TASA_IR4TA", anioFiscal, null)
                .orElse(null);
        if (tasa == null) {
            return BigDecimal.ZERO; // Defensa: parámetro pendiente de cargar.
        }

        return baseImponible.multiply(tasa).setScale(2, RoundingMode.HALF_UP);
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

        if (regimenEmpleadoCodigo == null) {
            return true; // Defensivo: no bloquear sin dato.
        }
        if (regimenAplicableCsv == null || regimenAplicableCsv.isBlank()) {
            return true; // Compat: concepto sin metadata cargada.
        }
        String csv = regimenAplicableCsv.trim();
        if ("TODOS".equalsIgnoreCase(csv)) {
            return true;
        }
        for (String token : csv.split(",")) {
            if (token.trim().equalsIgnoreCase(regimenEmpleadoCodigo)) {
                return true;
            }
        }
        return false;
    }
}
