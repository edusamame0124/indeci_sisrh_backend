package com.indeci.sistema.dto;

/**
 * Fila del directorio de usuarios GDR_USUARIO (sistema 'rendimiento') que GDR
 * consume para poblar el buscador de "Asignar Rol GDR". El DNI es la llave
 * puente hacia HR_PERSON en GDR; la oficina (area) alimenta HR_ORG_UNIT.
 */
public record GdrDirectoryUserResponse(
        String dni,
        String nombreCompleto,
        String username,
        String areaCodigo,
        String areaNombre,
        String estado) {
}
