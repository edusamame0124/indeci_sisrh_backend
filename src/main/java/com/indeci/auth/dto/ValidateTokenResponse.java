package com.indeci.auth.dto;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Fase 3 SSO — Cuerpo de respuesta de {@code GET /api/auth/validate}.
 *
 * Lo consumen SISCONV (8081) y GDR (8082) para verificar el token JWT emitido
 * por el SISRH y derivar las authorities del usuario dentro de su propio
 * sistema desde {@code sistemas[su-codigo]}.
 *
 * Si el token es válido pero el usuario NO tiene asignación para el sistema
 * llamador, ese sistema lo verá como {@code sistemas[X] == null} y debe
 * responder 403 al usuario.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ValidateTokenResponse {

    /** Username de la cuenta (claim "sub" del JWT). */
    private String subject;

    /** Roles macro del SISRH (intactos Fase 1/2). */
    private List<String> roles;

    /** Permisos granulares del SISRH (intactos Fase 1/2). */
    private List<String> permisos;

    /**
     * Mapa código de sistema → roles del usuario en ese sistema.
     * Siempre contiene la entrada "sisrh"; puede contener "convocatoria",
     * "rendimiento", etc., si el usuario tiene asignación activa.
     */
    private Map<String, List<String>> sistemas;

    /** Empleado vinculado a la cuenta (Spec 011/B2). NULL si no tiene. */
    private Long empleadoId;

    /** Epoch seconds de expiración del access token. */
    private Long exp;
}
