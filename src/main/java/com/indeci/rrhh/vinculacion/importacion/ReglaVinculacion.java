package com.indeci.rrhh.vinculacion.importacion;

import java.util.List;

/**
 * Una regla de validación sobre una fila del Excel de vinculación.
 *
 * <p><b>Punto de extensión (OCP).</b> Cuando RR.HH. pida una validación nueva, se crea
 * una implementación más y Spring la inyecta sola: {@link VinculacionRowValidator} no se
 * modifica. Cada regla es independiente y testeable por separado.
 *
 * <p>Contrato: una regla <b>no</b> lanza excepciones ni escribe en BD; solo reporta
 * hallazgos. Si la fila no le aplica (p. ej. una regla de CAS sobre una fila SERVIR),
 * devuelve una lista vacía.
 */
public interface ReglaVinculacion {

    /**
     * @param fila fila cruda a evaluar
     * @return hallazgos encontrados; lista vacía si todo está conforme.
     */
    List<RowIssue> validar(VinculacionRowRaw fila);

    /** Orden de ejecución (menor primero). Solo afecta el orden de los mensajes. */
    default int orden() {
        return 100;
    }
}
