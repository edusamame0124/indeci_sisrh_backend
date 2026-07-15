package com.indeci.sistema.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.indeci.sistema.dto.GdrDirectoryUserResponse;
import com.indeci.sistema.repository.UsuarioSistemaRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Directorio de usuarios GDR_USUARIO del sistema 'rendimiento', expuesto a GDR
 * vía {@code GET /api/sistemas/rendimiento/usuarios}. Solo lectura.
 *
 * Resuelve todo en UNA consulta nativa (sin N+1): cruza INDECI_USUARIO_SISTEMA
 * (ROLES_EXTERNOS contiene el rol, AREA_CODIGO = oficina) con INDECI_PERSONA
 * (DNI y nombre) y USERS, e incluye el nombre de la oficina. Filtra por DNI
 * (prefijo), nombre o username y limita a 25 filas.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GdrDirectoryService {

    private static final String ROL_DEFECTO = "GDR_USUARIO";
    private static final int MIN_QUERY = 2;

    private final UsuarioSistemaRepository usuarioSistemaRepository;

    @Transactional(readOnly = true)
    public List<GdrDirectoryUserResponse> buscarUsuariosGdr(String q, String rol) {
        String qClean = q == null ? "" : q.trim();
        if (qClean.length() < MIN_QUERY) {
            return List.of();
        }
        String rolFiltro = ((rol == null || rol.isBlank()) ? ROL_DEFECTO : rol.trim())
                .toUpperCase(Locale.ROOT);
        String qPrefix = qClean + "%";
        String qLike = "%" + qClean.toUpperCase(Locale.ROOT) + "%";

        List<Object[]> rows = usuarioSistemaRepository.searchGdrDirectory(rolFiltro, qPrefix, qLike);
        List<GdrDirectoryUserResponse> out = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            out.add(new GdrDirectoryUserResponse(
                    asString(r[0]),   // dni
                    asString(r[1]),   // nombreCompleto
                    asString(r[2]),   // username
                    asString(r[3]),   // areaCodigo
                    asString(r[4]),   // areaNombre
                    asString(r[5]))); // estado
        }
        log.info("[GDR-DIR] q='{}' rol='{}' resultados={}", qClean, rolFiltro, out.size());
        return out;
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
