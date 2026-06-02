package com.indeci.rrhh.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Service;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.SubsidioCalculadoDto;
import com.indeci.rrhh.entity.EmpleadoEvento;
import com.indeci.rrhh.entity.MovimientoPlanilla;
import com.indeci.rrhh.entity.TipoEvento;
import com.indeci.rrhh.repository.MovimientoPlanillaRepository;

import lombok.RequiredArgsConstructor;

/**
 * F2.4 — Cálculo de subsidios (maternidad/enfermedad) según la fórmula del
 * Excel CAS consolidado del cliente y la práctica EsSalud.
 *
 * <p>Fórmula formal (SPEC §17.7):
 * <pre>
 *     DIAS_DESCANSO              = FECHA_FIN − FECHA_INICIO + 1
 *     REMUNERACION_DIARIA        = REMUNERACION_MENSUAL_BASE / 30
 *     SUBTOTAL_REMUNERATIVO      = DIAS_DESCANSO × REMUNERACION_DIARIA
 *
 *     PROMEDIO_MENSUAL_SUBSIDIO  = SUMA(últimos 12 meses) / 12
 *     SUBSIDIO_DIARIO_ESSALUD    = PROMEDIO_MENSUAL / 30
 *     SUBSIDIO_ESSALUD           = DIAS_DESCANSO × SUBSIDIO_DIARIO_ESSALUD
 *
 *     DIFERENCIA_ASUMIDA_INDECI  = SUBTOTAL_REMUNERATIVO − SUBSIDIO_ESSALUD
 * </pre></p>
 *
 * <p>Defensivos:</p>
 * <ul>
 *   <li>Si {@code tipoEvento.generaSubsidio != 'S'} → devuelve {@link SubsidioCalculadoDto#noAplica}.</li>
 *   <li>Si las fechas son inválidas → {@code NegocioException}.</li>
 *   <li>Si no hay historial de 12 meses → fallback a {@code remuneracionMensualBase}
 *       (el empleado recién ingresa o no tiene planillas previas).</li>
 *   <li>Si hay menos de 12 meses de historial → promedia los disponibles
 *       (divide por la cantidad real, no por 12).</li>
 * </ul>
 *
 * <p>F2.4 NO graba detalles MEF — esa parte se hará cuando RRHH entregue los
 * CODIGO_MEF de "Subsidio Maternidad EsSalud", "Subsidio Enfermedad" y
 * "Diferencia INDECI" (LEY-01).</p>
 */
@Service
@RequiredArgsConstructor
public class SubsidioCalculadorService {

    private static final BigDecimal TREINTA = new BigDecimal("30");
    /** Cantidad de meses del promedio EsSalud (D.S. 005-2011-TR Art. 9). */
    private static final int MESES_PROMEDIO = 12;

    private final MovimientoPlanillaRepository movimientoRepository;

    /**
     * Calcula los montos del subsidio para un evento.
     *
     * @param evento                  evento con {@code tipoEvento}, fechas y empleadoId.
     * @param remuneracionMensualBase remuneración mensual actual del empleado
     *                                (típicamente la del período del evento).
     *                                Se usa como base del subtotal remunerativo
     *                                y como fallback si no hay historial.
     */
    public SubsidioCalculadoDto calcular(
            EmpleadoEvento evento,
            BigDecimal remuneracionMensualBase) {

        if (evento == null) {
            throw new NegocioException("Evento nulo");
        }
        TipoEvento tipo = evento.getTipoEvento();
        if (tipo == null || !"S".equalsIgnoreCase(tipo.getGeneraSubsidio())) {
            return SubsidioCalculadoDto.noAplica();
        }
        if (evento.getFechaInicio() == null || evento.getFechaFin() == null) {
            throw new NegocioException(
                    "Evento sin fechas inicio/fin (empleadoId=" + evento.getEmpleadoId() + ")");
        }
        if (evento.getFechaFin().isBefore(evento.getFechaInicio())) {
            throw new NegocioException("Evento con fechaFin < fechaInicio");
        }
        if (remuneracionMensualBase == null || remuneracionMensualBase.signum() <= 0) {
            throw new NegocioException(
                    "Remuneración mensual base inválida (debe ser > 0)");
        }

        long diasDescanso = ChronoUnit.DAYS.between(
                evento.getFechaInicio(), evento.getFechaFin()) + 1;
        if (diasDescanso <= 0) {
            return SubsidioCalculadoDto.noAplica();
        }

        // 1-3. Subtotal remunerativo del período de descanso.
        BigDecimal remDiaria = remuneracionMensualBase
                .divide(TREINTA, 2, RoundingMode.HALF_UP);
        BigDecimal subtotal = remDiaria
                .multiply(BigDecimal.valueOf(diasDescanso))
                .setScale(2, RoundingMode.HALF_UP);

        // 4. Promedio de los últimos 12 meses anteriores al inicio del evento.
        BigDecimal promedioMensual = calcularPromedioUltimos12Meses(
                evento.getEmpleadoId(),
                evento.getFechaInicio(),
                remuneracionMensualBase);

        // 5. Subsidio diario EsSalud.
        BigDecimal subsidioDiario = promedioMensual
                .divide(TREINTA, 2, RoundingMode.HALF_UP);

        // 6. Subsidio total EsSalud (redondeo a entero, práctica EsSalud).
        BigDecimal subsidioEssalud = subsidioDiario
                .multiply(BigDecimal.valueOf(diasDescanso))
                .setScale(0, RoundingMode.HALF_UP);

        // 7. Diferencia que asume INDECI (puede ser negativa si EsSalud paga
        //    más que la base — caso raro pero matemáticamente posible).
        BigDecimal diferenciaIndeci = subtotal.subtract(subsidioEssalud);

        return new SubsidioCalculadoDto(
                true,
                (int) diasDescanso,
                remDiaria,
                subtotal,
                promedioMensual,
                subsidioDiario,
                subsidioEssalud,
                diferenciaIndeci);
    }

    /**
     * Calcula el promedio mensual de {@code totalIngresos} de los últimos 12
     * meses anteriores a {@code fechaBase}. Si no hay historial → fallback.
     * Si hay menos de 12 → promedia los disponibles.
     */
    private BigDecimal calcularPromedioUltimos12Meses(
            Long empleadoId,
            LocalDate fechaBase,
            BigDecimal fallback) {

        YearMonth eventoYM = YearMonth.from(fechaBase);
        YearMonth desde = eventoYM.minusMonths(MESES_PROMEDIO);
        YearMonth hasta = eventoYM.minusMonths(1);

        List<MovimientoPlanilla> historial = movimientoRepository
                .findByEmpleadoIdAndActivo(empleadoId, 1)
                .stream()
                .filter(m -> {
                    YearMonth ym = parsePeriodoSeguro(m.getPeriodo());
                    return ym != null
                            && !ym.isBefore(desde)
                            && !ym.isAfter(hasta);
                })
                .toList();

        if (historial.isEmpty()) {
            return fallback;
        }

        BigDecimal suma = historial.stream()
                .map(m -> m.getTotalIngresos() != null
                        ? BigDecimal.valueOf(m.getTotalIngresos())
                        : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return suma.divide(
                BigDecimal.valueOf(historial.size()),
                2, RoundingMode.HALF_UP);
    }

    /**
     * Parsea {@code "YYYYMM"} o {@code "YYYY-MM"} a {@link YearMonth}. Devuelve
     * {@code null} si el formato no se reconoce (defensivo — no rompe el
     * cálculo si una fila tiene PERIODO basura).
     */
    private static YearMonth parsePeriodoSeguro(String periodo) {
        if (periodo == null) return null;
        String p = periodo.replace("-", "").trim();
        if (p.length() != 6) return null;
        try {
            int anio = Integer.parseInt(p.substring(0, 4));
            int mes  = Integer.parseInt(p.substring(4, 6));
            return YearMonth.of(anio, mes);
        } catch (NumberFormatException | java.time.DateTimeException ex) {
            return null;
        }
    }
}
