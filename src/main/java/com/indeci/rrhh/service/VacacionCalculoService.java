package com.indeci.rrhh.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Service;

import com.indeci.rrhh.dto.VacacionCalculoDto;
import com.indeci.rrhh.dto.VacacionCalculoInput;

import lombok.RequiredArgsConstructor;
import com.indeci.rrhh.service.incidencia.IncidenciaLaboralProvider;

/**
 * Motor de cálculo vacacional — SPEC_VACACIONES F3. Servicio
 * que evalúa el récord vacacional (D.Leg. 1405 art. 2.2). Consumido por el padrón (F4/F5).
 *
 * <p>Reglas:
 * <ul>
 *   <li><b>Días que corresponden (P)</b> = años completos × 30.</li>
 *   <li><b>Saldo (R)</b> = corresponden − gozados (puede ser negativo, como el Excel).</li>
 *   <li><b>Truncas (T/U/V)</b>: T = rem/30 × saldo; U = rem/12 × meses; V = rem/360 × días.
 *       Redondeo monetario HALF_UP a 2 decimales.</li>
 *   <li><b>Récord (D5)</b>: días efectivos = días 30/360 − no computables; estado
 *       {@code SIN_RECORD_LEGAL} si &lt; umbral (210 jornada 5d / 260 jornada 6d).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class VacacionCalculoService {

    private final IncidenciaLaboralProvider incidenciaLaboralProvider;

    private static final int DIAS_POR_ANIO = 30;
    private static final int RECORD_JORNADA_6 = 260;
    private static final int RECORD_JORNADA_5 = 210;
    private static final BigDecimal DIVISOR_MES = BigDecimal.valueOf(30);
    private static final BigDecimal DIVISOR_ANIO_MESES = BigDecimal.valueOf(12);
    private static final BigDecimal DIVISOR_ANIO_DIAS = BigDecimal.valueOf(360);

    public VacacionCalculoDto calcular(VacacionCalculoInput in) {
        if (in == null) {
            throw new IllegalArgumentException("VacacionCalculoInput requerido");
        }
        final BigDecimal rem = in.remuneracionMensual() != null
                ? in.remuneracionMensual()
                : BigDecimal.ZERO;

        // P — días que corresponden (F9: tomado directamente del saldo histórico ganado, ya no anios * 30).
        final int diasCorresponden = (int) in.diasGanadosHistoricos();

        // R — saldo (puede ser negativo si el trabajador gozó de más).
        final double saldo = in.diasGanadosHistoricos() - in.diasGozados();

        // T — costo de días no gozados = rem/30 × saldo.
        final BigDecimal costoNoGozadas = rem
                .divide(DIVISOR_MES, 10, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(saldo))
                .setScale(2, RoundingMode.HALF_UP);

        // U — costo truncas por meses = rem/12 × meses.
        final BigDecimal costoTruncasMes = rem
                .divide(DIVISOR_ANIO_MESES, 10, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(in.meses()))
                .setScale(2, RoundingMode.HALF_UP);

        // V — costo truncas por días = rem/360 × días.
        final BigDecimal costoTruncasDia = rem
                .divide(DIVISOR_ANIO_DIAS, 10, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(in.dias()))
                .setScale(2, RoundingMode.HALF_UP);

        // W — total.
        final BigDecimal costoTotal = costoNoGozadas
                .add(costoTruncasMes)
                .add(costoTruncasDia)
                .setScale(2, RoundingMode.HALF_UP);

        // Récord (D5) — días efectivos vs umbral por jornada.
        int diasNoComputables = in.diasNoComputables();
        if (in.empleadoId() != null && in.fechaDesde() != null && in.fechaHasta() != null) {
            diasNoComputables += incidenciaLaboralProvider.obtenerDiasNoComputables(in.empleadoId(), in.fechaDesde(), in.fechaHasta());
        }

        final int diasEfectivos = in.totalDias360() - diasNoComputables;
        final int umbral = in.jornadaDiasSemana() >= 6 ? RECORD_JORNADA_6 : RECORD_JORNADA_5;
        final String estadoRecord = diasEfectivos >= umbral
                ? VacacionCalculoDto.RECORD_OK
                : VacacionCalculoDto.RECORD_SIN;

        return new VacacionCalculoDto(
                diasCorresponden,
                in.diasGozados(),
                saldo,
                costoNoGozadas,
                costoTruncasMes,
                costoTruncasDia,
                costoTotal,
                diasEfectivos,
                umbral,
                estadoRecord);
    }
}
