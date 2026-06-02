package com.indeci.rrhh.dto;

import java.math.BigDecimal;

/**
 * F2.4 — Resultado del cálculo de subsidio (maternidad o enfermedad) según
 * la fórmula del Excel CAS consolidado del cliente.
 *
 * <p>Campos:</p>
 * <ul>
 *   <li>{@code aplica} — false si el tipo de evento no genera subsidio o si
 *       las fechas son inválidas. Todos los demás campos van en 0.</li>
 *   <li>{@code diasDescanso} — {@code fechaFin - fechaInicio + 1}.</li>
 *   <li>{@code remuneracionDiaria} — {@code remuneracionMensualBase / 30}.</li>
 *   <li>{@code subtotalRemunerativo} — {@code diasDescanso × remuneracionDiaria}.
 *       Lo que el empleado dejaría de percibir si no hubiera subsidio.</li>
 *   <li>{@code promedioMensual12Meses} — promedio de remuneración bruta de los
 *       últimos 12 meses anteriores al inicio del evento. Si no hay historial
 *       suficiente, cae al fallback {@code remuneracionMensualBase}.</li>
 *   <li>{@code subsidioDiarioEssalud} — {@code promedioMensual / 30}.</li>
 *   <li>{@code subsidioEssalud} — {@code diasDescanso × subsidioDiarioEssalud}.
 *       Lo que paga EsSalud al asegurado.</li>
 *   <li>{@code diferenciaAsumidaIndeci} — {@code subtotalRemunerativo −
 *       subsidioEssalud}. Lo que la entidad cubre para que el empleado no
 *       pierda ingresos.</li>
 * </ul>
 *
 * <p>F2.4 no graba detalles MEF — eso ocurrirá cuando RRHH entregue los
 * códigos MEF de "Subsidio Maternidad EsSalud" / "Subsidio Enfermedad" /
 * "Diferencia INDECI" (LEY-01: no inventar).</p>
 */
public record SubsidioCalculadoDto(
        boolean aplica,
        int diasDescanso,
        BigDecimal remuneracionDiaria,
        BigDecimal subtotalRemunerativo,
        BigDecimal promedioMensual12Meses,
        BigDecimal subsidioDiarioEssalud,
        BigDecimal subsidioEssalud,
        BigDecimal diferenciaAsumidaIndeci) {

    /** Resultado neutro para casos donde no aplica (tipo no genera subsidio, etc.). */
    public static SubsidioCalculadoDto noAplica() {
        return new SubsidioCalculadoDto(
                false, 0,
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
