package com.indeci.exception;

/**
 * F1.5b — Lanzada cuando un EmpleadoConcepto está asignado a un empleado
 * cuyo régimen laboral NO coincide con {@code ConceptoPlanilla.regimenAplicable}.
 *
 * <p>Sustento normativo: los Decretos Supremos de pacto colectivo centralizado
 * MEF aplican a regímenes específicos (típicamente 728 y 1057). Pagar un DS
 * 327-2025-EF a un empleado del régimen 276 sería un pago indebido (276
 * recibe sus aumentos vía MUC en AIRHSP, no por estos DS).</p>
 *
 * <p>El motor v3 (con {@code motor.v3.prorrateo.enabled=true}) consulta
 * {@code REGIMEN_APLICABLE} de cada concepto manual y lanza esta excepción
 * cuando hay incompatibilidad. Esto actúa como "guard normativo" que evita
 * planillas con pagos no permitidos por ley.</p>
 */
public class ConceptoRegimenNoAplicableException extends NegocioException {

    public ConceptoRegimenNoAplicableException(
            String codigoMef,
            String nombreConcepto,
            String regimenEmpleado,
            String regimenAplicable) {
        super("Concepto " + codigoMef + " '" + nombreConcepto
                + "' no aplica al régimen " + regimenEmpleado
                + " (régimen permitido: " + regimenAplicable + ")."
                + " Revisar EmpleadoConcepto o el catálogo INDECI_CONCEPTO_PLANILLA.");
    }
}
