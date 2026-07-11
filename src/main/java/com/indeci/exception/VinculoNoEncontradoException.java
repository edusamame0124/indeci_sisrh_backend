package com.indeci.exception;

/**
 * SPEC_VACACIONES F1 — el empleado no tiene ningún vínculo activo (o sin fechas
 * válidas) para calcular su tiempo de servicio.
 *
 * <p>Extiende {@link RuntimeException} (NO {@link NegocioException}) para que el
 * {@code GlobalExceptionHandler} la mapee a <b>HTTP 404</b> y no a 400.
 */
public class VinculoNoEncontradoException extends RuntimeException {
    public VinculoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
