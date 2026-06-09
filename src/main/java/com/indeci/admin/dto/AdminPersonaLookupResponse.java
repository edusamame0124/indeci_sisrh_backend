package com.indeci.admin.dto;

/**
 * Resultado de búsqueda de persona para el alta de usuario (preview / autocomplete).
 */
public record AdminPersonaLookupResponse(
        Long personaId,
        String dni,
        String nombreCompleto,
        Long empleadoId,
        String codigoInterno,
        boolean cuentaVinculada,
        String usernameVinculado) {
}
