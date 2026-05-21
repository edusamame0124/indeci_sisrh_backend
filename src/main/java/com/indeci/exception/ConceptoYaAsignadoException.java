package com.indeci.exception;

/**
 * Spec 013 / C1 — El empleado ya tiene asignado y activo ese concepto de
 * planilla. Para reemplazarlo, primero hay que finalizar (dar de baja) el
 * registro anterior.
 *
 * <p>Mapea a HTTP 409 en {@code GlobalExceptionHandler}.
 */
public class ConceptoYaAsignadoException extends RuntimeException {

    public ConceptoYaAsignadoException() {
        super("El empleado ya tiene este descuento activo. "
                + "Finalice el anterior antes de crear uno nuevo.");
    }
}
