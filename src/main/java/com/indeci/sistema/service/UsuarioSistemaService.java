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
 * Devuelve un {@code Map<codigo, roles[]>} con UNA entrada por cada sistema
 * ACTIVO de INDECI_SISTEMA (no solo los asignados), para que el Portal Selector
 * siempre muestre todas las cards:
 *   - "sisrh"        → roles macro del SISRH del propio usuario.
 *   - "<otro>"       → roles externos si hay asignación activa en
 *                      INDECI_USUARIO_SISTEMA; lista VACÍA si no está asignado
 *                      (la card aparece BLOQUEADA en el selector).
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

        // 1. Sembrar TODOS los sistemas activos (orden ASC) con lista vacía. El
        //    Portal Selector pinta una card por cada sistema activo; las que
        //    queden con roles vacíos se muestran BLOQUEADAS ("contacte al admin").
        //    Mapa ID -> CODIGO para resolver las asignaciones externas en O(1).
        Map<String, List<String>> resultado = new LinkedHashMap<>();
        Map<Long, String> sistemasActivos = new HashMap<>();
        for (Sistema s : sistemaRepository.findByActivoOrderByOrdenAsc(1)) {
            sistemasActivos.put(s.getId(), s.getCodigo());
            resultado.put(s.getCodigo(), new ArrayList<>());
        }

        // 2. SISRH: roles macro del propio usuario. Si no tiene roles SISRH queda
        //    con lista vacía → card bloqueada (igual que los demás sistemas).
        //    put() sobre una clave existente conserva su posición (orden por ORDEN).
        resultado.put(CODIGO_SISRH, rolesSisrh != null ? List.copyOf(rolesSisrh) : List.of());

        // 3. Asignaciones externas activas del usuario → sobrescriben los roles
        //    del sistema correspondiente (si está activo y no es "sisrh"). Los
        //    sistemas activos sin asignación se quedan con la lista vacía del paso 1.
        List<UsuarioSistema> asignaciones =
                usuarioSistemaRepository.findByUserIdAndActivo(user.getId(), 1);
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
     * Construye el mapa {@code codigo -> areaCodigo} para el claim {@code areas} del JWT.
     * Solo incluye sistemas externos con area asignada y activa.
     * SISRH no tiene area en este modelo (siempre omitido).
     */
    public Map<String, String> obtenerAreasDe(User user) {
        Map<String, String> resultado = new LinkedHashMap<>();
        Map<Long, String> sistemasActivos = new HashMap<>();
        for (Sistema s : sistemaRepository.findByActivoOrderByOrdenAsc(1)) {
            sistemasActivos.put(s.getId(), s.getCodigo());
        }
        List<UsuarioSistema> asignaciones =
                usuarioSistemaRepository.findByUserIdAndActivo(user.getId(), 1);
        for (UsuarioSistema asignacion : asignaciones) {
            String codigo = sistemasActivos.get(asignacion.getSistemaId());
            if (codigo == null || CODIGO_SISRH.equals(codigo)) {
                continue;
            }
            if (asignacion.getAreaCodigo() != null && !asignacion.getAreaCodigo().isBlank()) {
                resultado.put(codigo, asignacion.getAreaCodigo().trim());
            }
        }
        return resultado;
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
