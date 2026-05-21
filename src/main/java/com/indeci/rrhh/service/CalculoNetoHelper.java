package com.indeci.rrhh.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Spec 013 / C1 — Lógica PURA de cálculo de neto para el preview del modal
 * "Asignar Descuento / Ajuste Manual".
 *
 * <p>Clase NUEVA. Sin estado, sin {@code @Transactional}, sin acceso a BD: solo
 * recibe conceptos ya resueltos (monto calculado) y devuelve aritmética. La usa
 * {@link EstimacionNetoService} para estimar el neto sin grabar nada.
 * {@code GeneradorPlanillaService} podría reutilizarla en un refactor futuro,
 * pero NO se modifica ahora.
 *
 * <p>Clasificación de conceptos — espeja {@code GeneradorPlanillaService}:
 * <ul>
 *   <li>{@code REMUNERATIVO} / {@code NO_REMUNERATIVO} → ingreso (suma al neto)</li>
 *   <li>{@code DESCUENTO} / {@code APORTE_TRABAJADOR}   → descuento (resta del neto)</li>
 *   <li>{@code APORTE_EMPLEADOR} → informativo, NO afecta el neto (LEY-02)</li>
 * </ul>
 */
@Component
public class CalculoNetoHelper {

    private static final BigDecimal MEDIO = new BigDecimal("0.50");

    /** Un concepto ya resuelto: su {@code TIPO_CONCEPTO} y su monto en soles. */
    public record ConceptoAplicado(String tipoConcepto, BigDecimal monto) {}

    /**
     * Calcula el neto = {@code remuneracion + ingresos − descuentos}.
     *
     * @param conceptos   conceptos VARIABLES ya resueltos: no-remunerativos,
     *                    descuentos y aportes del trabajador. NO debe incluir la
     *                    remuneración base (se pasa aparte para evitar doble conteo).
     * @param remuneracion remuneración base mensual (suma de los conceptos
     *                    {@code REMUNERATIVO} del empleado). {@code null} → cero.
     * @return el neto, escala 2, HALF_UP.
     */
    public BigDecimal calcularNeto(List<ConceptoAplicado> conceptos, BigDecimal remuneracion) {
        BigDecimal neto = remuneracion == null ? BigDecimal.ZERO : remuneracion;
        if (conceptos != null) {
            for (ConceptoAplicado c : conceptos) {
                BigDecimal monto = c.monto() == null ? BigDecimal.ZERO : c.monto();
                switch (clasificar(c.tipoConcepto())) {
                    case "INGRESO"   -> neto = neto.add(monto);
                    case "DESCUENTO" -> neto = neto.subtract(monto);
                    default          -> { /* APORTE_EMPLEADOR — informativo (LEY-02) */ }
                }
            }
        }
        return neto.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * REGLA SERVIR-07 (SPEC §5.4): el neto no puede caer por debajo del 50% de
     * la remuneración del trabajador.
     *
     * @return {@code true} si {@code netoEstimado >= remuneracionBase × 0.50}.
     *         {@code false} si algún argumento es {@code null}.
     */
    public boolean validarRegla50(BigDecimal netoEstimado, BigDecimal remuneracionBase) {
        if (netoEstimado == null || remuneracionBase == null) {
            return false;
        }
        BigDecimal umbral = remuneracionBase.multiply(MEDIO).setScale(2, RoundingMode.HALF_UP);
        return netoEstimado.compareTo(umbral) >= 0;
    }

    /** Normaliza el TIPO_CONCEPTO a INGRESO | DESCUENTO | APORTE_EMPLEADOR. */
    private String clasificar(String tipoConcepto) {
        if (tipoConcepto == null) {
            return "INGRESO";
        }
        return switch (tipoConcepto.toUpperCase()) {
            case "REMUNERATIVO", "NO_REMUNERATIVO" -> "INGRESO";
            case "DESCUENTO", "APORTE_TRABAJADOR"  -> "DESCUENTO";
            case "APORTE_EMPLEADOR"                -> "APORTE_EMPLEADOR";
            default                                -> "INGRESO";
        };
    }
}
