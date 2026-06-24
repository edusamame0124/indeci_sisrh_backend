package com.indeci.exception;

/**
 * SPEC_CONCEPTOS_PLANILLA P1 (§8/D5) — Se intentó editar/transicionar un concepto
 * de planilla que ya fue usado en una planilla CERRADA/APROBADA (inmutable).
 *
 * <p>Prohibición firme: no editar ni anular retroactivamente un concepto usado en
 * planilla cerrada; solo crear una nueva configuración vigente hacia adelante.</p>
 *
 * <p>Mapea a HTTP 409 en {@code GlobalExceptionHandler}.</p>
 */
public class ConceptoEnPlanillaCerradaException extends RuntimeException {

    public ConceptoEnPlanillaCerradaException() {
        super("El concepto fue utilizado en una planilla cerrada y no puede "
                + "modificarse. Cree una nueva configuración vigente hacia adelante.");
    }
}
