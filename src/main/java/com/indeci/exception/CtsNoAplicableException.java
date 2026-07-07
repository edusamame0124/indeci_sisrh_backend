package com.indeci.exception;

/**
 * Feature 016 — CTS no aplicable al régimen/situación del vínculo.
 *
 * <p>Extiende {@link NegocioException} → el {@code GlobalExceptionHandler} la
 * mapea a HTTP 400 con el formato canónico {@code {status,mensaje,requiereCaptcha}}.
 *
 * <p>Casos: régimen CAS 1057 (guard Poka-Yoke, sin CTS por norma) y régimen no
 * habilitado para liquidación de CTS (solo 276 y SERVIR).</p>
 */
public class CtsNoAplicableException extends NegocioException {
    public CtsNoAplicableException(String mensaje) {
        super(mensaje);
    }
}
