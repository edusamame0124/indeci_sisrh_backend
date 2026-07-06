package com.indeci.rrhh.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.BeneficioCasCalculadoDto;
import com.indeci.rrhh.dto.GratificacionCasDto;
import com.indeci.rrhh.entity.ReglaBeneficioCas;
import com.indeci.rrhh.repository.ReglaBeneficioCasRepository;

import lombok.RequiredArgsConstructor;

/**
 * F2bis — Beneficios CAS 2026 (decisión RRHH C6 / 2026-05-31).
 *
 * <p>Calcula aguinaldos CAS (Fiestas Patrias en julio / Navidad en diciembre)
 * más la bonificación extraordinaria asociada. Reemplaza al estado "diferido"
 * histórico: ahora SÍ entra al ciclo, pero protegido por feature flag para
 * que se active solo cuando RRHH cargue los valores que publica MEF en cada
 * fecha.</p>
 *
 * <p>Códigos de parámetro esperados en {@code INDECI_PARAMETRO_REMUNERATIVO}:</p>
 * <ul>
 *   <li>{@code AGUINALDO_CAS_JULIO} (PEN) — monto fijo del aguinaldo.</li>
 *   <li>{@code AGUINALDO_CAS_DICIEMBRE} (PEN).</li>
 *   <li>{@code BONIF_EXTRAORD_PCT} (PCT) — porcentaje sobre el aguinaldo.</li>
 * </ul>
 *
 * <p>Comportamiento defensivo: si los parámetros no están sembrados (caso
 * habitual hasta que el MEF publica el DS anual), el service devuelve 0 sin
 * lanzar. Esto permite que el flag esté ON sin romper la planilla en meses
 * donde aún no hay DS.</p>
 *
 * <p>F2bis NO graba detalles MEF en {@code MovimientoPlanillaDetalle} ni se
 * conecta a {@code generar()}. La integración con el motor se decide cuando
 * RRHH entregue los CODIGO_MEF de "Aguinaldo CAS" y "Bonificación
 * Extraordinaria" — similar a F2.4 SubsidioCalculadorService.</p>
 */
@Service
@RequiredArgsConstructor
public class BeneficiosCasService {

    private static final String REG_CAS = "CAS";
    private static final String COD_AGUINALDO_JULIO     = "AGUINALDO_CAS_JULIO";
    private static final String COD_AGUINALDO_DICIEMBRE = "AGUINALDO_CAS_DICIEMBRE";
    private static final String COD_BONIF_PCT           = "BONIF_EXTRAORD_PCT";

    private final ParametroRemunerativoService parametroService;
    private final ReglaBeneficioCasRepository reglaRepository;

    @Value("${feature.beneficios-cas-2026.enabled:false}")
    private boolean beneficiosCasEnabled;

    /**
     * Calcula el aguinaldo CAS + bonificación extraordinaria para un empleado
     * en un período. Devuelve {@code noAplica()} si:
     * <ul>
     *   <li>El feature flag está OFF.</li>
     *   <li>El régimen no es CAS.</li>
     *   <li>El mes del período no es julio (07) ni diciembre (12).</li>
     * </ul>
     *
     * @param periodo               formato "YYYYMM" o "YYYY-MM".
     * @param regimenLaboralCodigo  código del régimen del empleado.
     */
    public BeneficioCasCalculadoDto calcular(
            String periodo,
            String regimenLaboralCodigo) {

        if (!beneficiosCasEnabled) {
            return BeneficioCasCalculadoDto.noAplica();
        }
        if (!REG_CAS.equalsIgnoreCase(regimenLaboralCodigo)) {
            return BeneficioCasCalculadoDto.noAplica();
        }

        LocalDate fechaPeriodo = parsearPeriodo(periodo);
        int mes = fechaPeriodo.getMonthValue();
        String codigoAguinaldo;
        String tipo;
        if (mes == 7) {
            codigoAguinaldo = COD_AGUINALDO_JULIO;
            tipo = "AGUINALDO_JULIO";
        } else if (mes == 12) {
            codigoAguinaldo = COD_AGUINALDO_DICIEMBRE;
            tipo = "AGUINALDO_DICIEMBRE";
        } else {
            return BeneficioCasCalculadoDto.noAplica();
        }

        BigDecimal montoAguinaldo = parametroService
                .obtenerValorOpcionalEnFecha(codigoAguinaldo, fechaPeriodo, null)
                .orElse(BigDecimal.ZERO);
        BigDecimal pctBonif = parametroService
                .obtenerValorOpcionalEnFecha(COD_BONIF_PCT, fechaPeriodo, null)
                .orElse(BigDecimal.ZERO);

        BigDecimal bonif = montoAguinaldo
                .multiply(pctBonif)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = montoAguinaldo.add(bonif);

        return new BeneficioCasCalculadoDto(true, tipo, montoAguinaldo, bonif, total);
    }

    /**
     * Devuelve {@code true} si el flag está activado. Útil para que el
     * endpoint REST/UI muestre o no la sección de beneficios.
     */
    public boolean isEnabled() {
        return beneficiosCasEnabled;
    }

    /**
     * Track B F4 (Ley 32563) — Gratificación CAS del período según la regla
     * vigente en {@code INDECI_REGLA_BENEFICIO_CAS}. Determinación AUTOMÁTICA por
     * período (sin switch manual): julio → Fiestas Patrias, diciembre → Navidad,
     * otro mes → vacío. Monto = remuneración mensual × factor (100%). La legacy de
     * monto fijo está INACTIVA y no participa aquí (guard anti-doble-pago: solo la
     * regla ACTIVA vigente aplica). No depende del feature flag legacy.
     *
     * @param periodo        "YYYYMM" o "YYYY-MM".
     * @param regimenCodigo  código del régimen del empleado (solo CAS aplica).
     * @param base           remuneración mensual CAS (100%).
     */
    public Optional<GratificacionCasDto> calcularGratificacion(
            String periodo, String regimenCodigo, BigDecimal base) {

        if (!REG_CAS.equalsIgnoreCase(regimenCodigo)) {
            return Optional.empty();
        }
        if (base == null || base.signum() <= 0) {
            return Optional.empty();
        }
        LocalDate fecha = parsearPeriodo(periodo);
        int mes = fecha.getMonthValue();
        if (mes != 7 && mes != 12) {
            return Optional.empty();
        }

        List<ReglaBeneficioCas> reglas =
                reglaRepository.findGratificacionesVigentes(REG_CAS, mes, fecha);
        if (reglas.isEmpty()) {
            return Optional.empty();
        }
        // Regla vigente más reciente (la lista viene ordenada por VIGENCIA_DESDE DESC).
        ReglaBeneficioCas regla = reglas.get(0);
        BigDecimal factor = regla.getFactor() != null ? regla.getFactor() : BigDecimal.ONE;
        BigDecimal monto = base.multiply(factor).setScale(2, RoundingMode.HALF_UP);

        return Optional.of(new GratificacionCasDto(
                regla.getCodigoBeneficio(), regla.getCodigoMef(), monto));
    }

    /**
     * Track B F4 — Etiqueta de la gratificación CAS que el motor evaluará
     * AUTOMÁTICAMENTE para el período, para mostrarla read-only en la pantalla de
     * generación (el usuario NO la activa/desactiva; la controla la regla vigente).
     * Vacío si el mes no aplica (≠ julio/diciembre) o no hay regla vigente.
     */
    public Optional<String> gratificacionAplicableEtiqueta(String periodo) {
        LocalDate fecha = parsearPeriodo(periodo);
        int mes = fecha.getMonthValue();
        if (mes != 7 && mes != 12) {
            return Optional.empty();
        }
        List<ReglaBeneficioCas> reglas =
                reglaRepository.findGratificacionesVigentes(REG_CAS, mes, fecha);
        if (reglas.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(etiquetaBeneficio(reglas.get(0).getCodigoBeneficio()));
    }

    private static String etiquetaBeneficio(String codigoBeneficio) {
        return switch (codigoBeneficio) {
            case "GRATIFICACION_FIESTAS_PATRIAS_CAS" -> "Gratificación por Fiestas Patrias (CAS)";
            case "GRATIFICACION_NAVIDAD_CAS" -> "Gratificación por Navidad (CAS)";
            default -> codigoBeneficio;
        };
    }

    private static LocalDate parsearPeriodo(String periodo) {
        if (periodo == null) {
            throw new NegocioException("Período requerido (YYYYMM o YYYY-MM)");
        }
        String p = periodo.replace("-", "").trim();
        if (p.length() != 6) {
            throw new NegocioException(
                    "Período inválido (esperado YYYYMM o YYYY-MM): " + periodo);
        }
        try {
            int anio = Integer.parseInt(p.substring(0, 4));
            int mes  = Integer.parseInt(p.substring(4, 6));
            return LocalDate.of(anio, mes, 1);
        } catch (NumberFormatException | java.time.DateTimeException ex) {
            throw new NegocioException(
                    "Período inválido: " + periodo);
        }
    }
}
