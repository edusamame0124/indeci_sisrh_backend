package com.indeci.exception;

/**
 * Spec 013 / C1 — El concepto es calculado AUTOMÁTICAMENTE por el motor de
 * planilla (aportes ONP/AFP, ESSALUD, copago EPS, retención 5ta, asignación
 * familiar, descuento de asistencia) y por tanto NO puede asignarse a mano
 * como {@code INDECI_EMPLEADO_CONCEPTO}.
 *
 * <p>Mapea a HTTP 422 en {@code GlobalExceptionHandler}.
 */
public class ConceptoNoAsignableManualmenteException extends RuntimeException {

    public ConceptoNoAsignableManualmenteException(String nombreConcepto) {
        super("El concepto '" + nombreConcepto + "' es calculado automáticamente "
                + "por el motor de planilla. No puede asignarse manualmente "
                + "(Ley 32448 — LEY-01).");
    }
}
