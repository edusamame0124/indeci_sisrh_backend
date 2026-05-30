package com.indeci.sistema.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.indeci.sistema.entity.Sistema;
import com.indeci.sistema.entity.UsuarioSistema;
import com.indeci.sistema.repository.SistemaRepository;
import com.indeci.sistema.repository.UsuarioSistemaRepository;
import com.indeci.user.entity.User;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Fase 3 SSO — Construye el claim {@code sistemas} del JWT.
 *
 * Devuelve un {@code Map<codigo, roles[]>} con:
 *   - "sisrh"        → roles macro del SISRH del propio usuario (siempre incluido).
 *   - "<otro>"       → roles externos por cada asignación activa en
 *                      INDECI_USUARIO_SISTEMA contra un sistema activo en
 *                      INDECI_SISTEMA.
 *
 * Los roles externos se almacenan como array JSON en VARCHAR2(500). Si el JSON
 * está corrupto se loguea WARN y se degrada a lista vacía — el login NO debe
 * romperse por una fila mal formada (defense in depth).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UsuarioSistemaService {

    private static final String CODIGO_SISRH = "sisrh";
    private static final TypeReference<List<String>> LIST_OF_STRING =
            new TypeReference<>() {};

    private final SistemaRepository sistemaRepository;
    private final UsuarioSistemaRepository usuarioSistemaRepository;
    private final ObjectMapper objectMapper;

    /**
     * Construye el mapa {@code codigo -> roles[]} listo para inyectar como claim
     * en el JWT y para que el Portal Selector pinte una card por entrada.
     *
     * @param user        usuario autenticado (su ID se usa para buscar asignaciones).
     * @param rolesSisrh  roles macro del SISRH ya calculados por AuthService
     *                    (se reutilizan tal cual, evita doble consulta a
     *                    INDECI_USUARIO_ROL / INDECI_ROL).
     */
    public Map<String, List<String>> obtenerSistemasDe(User user, List<String> rolesSisrh) {

        // 1. SISRH siempre presente. Si el usuario tiene rol macro en SISRH,
        //    la card del selector quedará habilitada. Si no, queda bloqueada.
        Map<String, List<String>> resultado = new LinkedHashMap<>();
        resultado.put(CODIGO_SISRH, rolesSisrh != null ? List.copyOf(rolesSisrh) : List.of());

        // 2. Sistemas externos. Si el usuario no tiene asignaciones, devolvemos
        //    el mapa con solo "sisrh" — el frontend lo interpreta como "un solo
        //    sistema" y salta el selector.
        List<UsuarioSistema> asignaciones =
                usuarioSistemaRepository.findByUserIdAndActivo(user.getId(), 1);

        if (asignaciones.isEmpty()) {
            return resultado;
        }

        // 3. Cargar catálogo de sistemas activos en una sola query (el catálogo
        //    es pequeño: 3 filas hoy). Mapa ID -> CODIGO para el lookup O(1).
        Map<Long, String> sistemasActivos = new HashMap<>();
        for (Sistema s : sistemaRepository.findByActivoOrderByOrdenAsc(1)) {
            sistemasActivos.put(s.getId(), s.getCodigo());
        }

        // 4. Por cada asignación, si el sistema está activo y NO es "sisrh"
        //    (ya inyectado arriba), parsear el JSON y agregarlo al resultado.
        for (UsuarioSistema asignacion : asignaciones) {
            String codigo = sistemasActivos.get(asignacion.getSistemaId());
            if (codigo == null || CODIGO_SISRH.equals(codigo)) {
                continue;
            }
            resultado.put(codigo, parsearRolesExternos(asignacion));
        }

        return resultado;
    }

    private List<String> parsearRolesExternos(UsuarioSistema asignacion) {
        String json = asignacion.getRolesExternos();
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> roles = objectMapper.readValue(json, LIST_OF_STRING);
            return roles != null ? roles : List.of();
        } catch (Exception ex) {
            log.warn("ROLES_EXTERNOS corrupto en INDECI_USUARIO_SISTEMA id={}: {}",
                    asignacion.getId(), ex.getMessage());
            return List.of();
        }
    }

    /**
     * Variante para el endpoint {@code GET /api/auth/validate}: solo necesita el
     * userId (no requiere el objeto User completo) y los roles ya extraídos del
     * claim. Útil cuando validamos un token entrante desde SISCONV / GDR.
     */
    public Optional<Map<String, List<String>>> obtenerSistemasPorUserId(
            Long userId, List<String> rolesSisrh) {

        if (userId == null) {
            return Optional.empty();
        }
        User stub = new User();
        stub.setId(userId);
        return Optional.of(obtenerSistemasDe(stub, rolesSisrh));
    }
}
