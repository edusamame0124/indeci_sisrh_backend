package com.indeci.rrhh.service;

import com.indeci.exception.ConceptoSinCodigoMefException;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.GeneracionFallidaDto;
import com.indeci.rrhh.dto.GeneracionMasivaResultDto;
import com.indeci.rrhh.dto.ResumenPlanillaDto;
import com.indeci.rrhh.entity.*;
import com.indeci.rrhh.repository.*;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Autowired;
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

    private static final BigDecimal CIEN  = new BigDecimal("100");
    private static final BigDecimal DOCE  = new BigDecimal("12");
    private static final BigDecimal SIETE = new BigDecimal("7");
    private static final BigDecimal MEDIO = new BigDecimal("0.5");

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

    /**
     * Self-reference para que {@link #generarTodoPeriodo(String)} invoque
     * {@link #generar(Long, String)} a través del proxy de Spring y cada
     * empleado ejecute con su propia transacción aislada.
     */
    @Autowired
    @Lazy
    private GeneradorPlanillaService self;

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

        // 7. Conceptos manuales (EmpleadoConcepto) — valida LEY-01
        ManualesResult manuales =
                aplicarConceptosManuales(movimiento, planilla);
        totalIngresos     = totalIngresos.add(manuales.ingresos);
        totalDescuentos   = totalDescuentos.add(manuales.descuentos);
        baseImponiblePens = baseImponiblePens.add(manuales.baseAportePension);
        baseImponibleEss  = baseImponibleEss.add(manuales.baseEssalud);

        // 7b. Descuento de asistencia (PASO 7) — tardanzas + faltas de la
        //     asistencia VALIDADA del período (D.Leg. 276 Art. 24). No reduce
        //     la base imponible: es una deducción sobre el bruto.
        BigDecimal descuentoAsistencia =
                calcularDescuentoAsistencia(movimiento, empleadoId, periodo);
        totalDescuentos = totalDescuentos.add(descuentoAsistencia);

        // 8. Aporte pensionario (ONP/AFP) — LEY-02: ESSALUD NUNCA aquí
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

        // 9. ESSALUD empleador con mínimo + split EPS. El copago EPS del
        //    trabajador (si tiene EPS) SÍ es descuento — se suma al total.
        BigDecimal copagoEps = calcularEssaludEmpleador(
                movimiento, empleado, baseImponibleEss, anioFiscal);
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
            EmpleadoPlanilla planilla) {

        ManualesResult result = new ManualesResult();
        BigDecimal sueldoBasico = toBigDecimal(planilla.getSueldoBasico());

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

            BigDecimal monto = calcularMontoEmpleadoConcepto(ec, sueldoBasico);

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
            int anioFiscal) {

        if (baseImponible.signum() <= 0) return BigDecimal.ZERO;

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
}
