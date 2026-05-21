package com.indeci.exception;

/**
 * Spec 010 / LEY-01 (Ley 32448) — Todo concepto pagable debe tener CODIGO_MEF
 * oficial del catálogo AIRHSP. Si el motor encuentra un concepto sin código,
 * la generación se aborta para impedir pagos no autorizados.
 */
public class ConceptoSinCodigoMefException extends RuntimeException {

    public ConceptoSinCodigoMefException(Long conceptoId, String nombre) {
        super("Concepto sin CODIGO_MEF (Ley 32448): id=" + conceptoId
                + (nombre != null ? " nombre='" + nombre + "'" : "")
                + ". Cargar el código oficial AIRHSP antes de regenerar la planilla.");
    }
}
