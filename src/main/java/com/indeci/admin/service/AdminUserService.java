package com.indeci.admin.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.indeci.admin.dto.AccesoSistemaDto;
import com.indeci.admin.dto.AccesoSistemaPutItem;
import com.indeci.admin.dto.AccesosPutRequest;
import com.indeci.admin.dto.AdminUserCreateRequest;
import com.indeci.admin.dto.AdminUserDetailResponse;
import com.indeci.admin.dto.AdminUserPermisoDeniesPutRequest;
import com.indeci.admin.dto.AdminUserRolesPutRequest;
import com.indeci.admin.dto.AdminUserStatusPatchRequest;
import com.indeci.admin.dto.AdminUserSummaryResponse;
import com.indeci.admin.dto.PermisoDeniedResponse;
import com.indeci.audit.annotation.Auditable;
import com.indeci.exception.NegocioException;
import com.indeci.sistema.entity.Sistema;
import com.indeci.sistema.entity.UsuarioSistema;
import com.indeci.sistema.repository.SistemaRepository;
import com.indeci.sistema.repository.SistemaRolRepository;
import com.indeci.sistema.repository.UsuarioSistemaRepository;
import com.indeci.user.entity.Permiso;
import com.indeci.user.entity.Rol;
import com.indeci.user.entity.User;
import com.indeci.user.entity.UsuarioPermisoDeny;
import com.indeci.user.entity.UsuarioRol;
import com.indeci.user.repository.PermisoRepository;
import com.indeci.user.repository.RolRepository;
import com.indeci.user.repository.UserRepository;
import com.indeci.user.repository.UsuarioPermisoDenyRepository;
import com.indeci.user.repository.UsuarioRolRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private static final String CODIGO_SISRH = "sisrh";
    private static final TypeReference<List<String>> LIST_OF_STRING = new TypeReference<>() {};

    private final UserRepository userRepository;
    private final UsuarioRolRepository usuarioRolRepository;
    private final UsuarioPermisoDenyRepository usuarioPermisoDenyRepository;
    private final RolRepository rolRepository;
    private final PermisoRepository permisoRepository;
    private final PasswordEncoder passwordEncoder;
    private final SistemaRepository sistemaRepository;
    private final UsuarioSistemaRepository usuarioSistemaRepository;
    private final SistemaRolRepository sistemaRolRepository;
    private final ObjectMapper objectMapper;

    @Value("${indeci.admin.new-user-default-role-id:}")
    private String newUserDefaultRoleIdRaw;

    @Value("${indeci.admin.new-user-default-role-codigo:}")
    private String newUserDefaultRoleCodigo;

    /** FK legacy USERS.ROLE_ID → catálogo padre (distinto del rol en SS_USUARIO_ROL). */
    @Value("${indeci.admin.new-user-legacy-role-codigo:ADMIN}")
    private String newUserLegacyRoleCodigo;

    @Transactional(readOnly = true)
    public Page<AdminUserSummaryResponse> listUsers(
            String q,
            String status,
            String sistema,
            Pageable pageable) {
        String qClean = q == null || q.isBlank() ? null : q.trim();
        String statusClean = status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
        String sistemaClean = sistema == null || sistema.isBlank() ? null : sistema.trim().toLowerCase(Locale.ROOT);
        Page<User> users = userRepository.searchAdminUsers(qClean, statusClean, sistemaClean, pageable);
        return users.map(u -> new AdminUserSummaryResponse(
                u.getId(),
                u.getUsername(),
                u.getStatus(),
                buildAccesos(u.getId())));
    }

    @Transactional(readOnly = true)
    public AdminUserDetailResponse getUser(Long id) {
        User u = userRepository.findById(id)
                .orElseThrow(() -> new NegocioException("Usuario no encontrado"));
        List<Long> roles = usuarioRolRepository.findByUserId(id).stream()
                .map(UsuarioRol::getRolId)
                .toList();
        List<Long> denied = usuarioPermisoDenyRepository.findByUserId(id).stream()
                .map(UsuarioPermisoDeny::getPermisoId)
                .toList();
        return new AdminUserDetailResponse(
                u.getId(),
                u.getUsername(),
                u.getStatus(),
                roles,
                denied,
                buildAccesos(u.getId()));
    }

    @Auditable(accion = "ADMIN_USER_CREATE")
    @Transactional
    public AdminUserDetailResponse createUser(AdminUserCreateRequest req) {
        String username = req.getUsername().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new NegocioException("El nombre de usuario ya existe");
        }
        Long assignedRolId = resolveNewUserDefaultRolId();
        Long legacyRoleId = resolveLegacyUsersRoleId();

        User u = new User();
        u.setUsername(username);
        u.setRoleId(legacyRoleId);
        String initialPassword = req.getPassword();
        if (initialPassword == null || initialPassword.isEmpty()) {
            initialPassword = UUID.randomUUID().toString();
        }
        String encoded = passwordEncoder.encode(initialPassword);
        u.setPassword(encoded);
        u.setPasswordHash(encoded);
        u.setStatus("ACTIVE");
        u.setNewClave("S");
        u.setOtpHabilitado("N");
        u.setCreatedAt(LocalDateTime.now());
        User saved = userRepository.save(u);

        UsuarioRol ur = new UsuarioRol();
        ur.setUserId(saved.getId());
        ur.setRolId(assignedRolId);
        ur.setSistema("SISRH");
        usuarioRolRepository.save(ur);

        return getUser(saved.getId());
    }

    /**
     * USERS.ROLE_ID tiene FK legacy (FK_USERS_USERS_IBFK_1). Los roles nuevos Fase 1
     * pueden no existir en esa tabla padre; se usa un código histórico (ADMIN).
     * Los permisos reales vienen de SS_USUARIO_ROL.
     */
    private Long resolveLegacyUsersRoleId() {
        String raw = newUserLegacyRoleCodigo != null ? newUserLegacyRoleCodigo.trim() : "";
        final String cod = raw.isEmpty() ? "ADMIN" : raw;
        return rolRepository.findFirstByCodigoIgnoreCase(cod)
                .map(Rol::getId)
                .orElseThrow(() -> new NegocioException(
                        "El rol legacy para USERS.ROLE_ID ("
                                + cod
                                + ") no existe. Revise indeci.admin.new-user-legacy-role-codigo."));
    }

    private Long resolveNewUserDefaultRolId() {
        String rawId = newUserDefaultRoleIdRaw != null ? newUserDefaultRoleIdRaw.trim() : "";
        if (!rawId.isEmpty()) {
            final long rid;
            try {
                rid = Long.parseLong(rawId);
            } catch (NumberFormatException e) {
                throw new NegocioException("Configuración indeci.admin.new-user-default-role-id no es numérica");
            }
            if (!rolRepository.existsById(rid)) {
                throw new NegocioException("El rol por defecto (ID) no existe en el catálogo");
            }
            return rid;
        }
        String cod =
                newUserDefaultRoleCodigo != null ? newUserDefaultRoleCodigo.trim() : "";
        if (!cod.isEmpty()) {
            Rol rol = rolRepository.findFirstByCodigoIgnoreCase(cod)
                    .orElseThrow(() ->
                            new NegocioException("El rol por defecto (código) no existe en el catálogo"));
            return rol.getId();
        }
        return rolRepository
                .findEligibleRolesForAutoAssign(PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .map(Rol::getId)
                .orElseThrow(
                        () -> new NegocioException(
                                "No hay roles activos configurados en gestionrrhh.SS_ROL (ACTIVO=S/1/…). "
                                        + "Cargue catálogo o establezca indeci.admin.new-user-default-role-id o "
                                        + "indeci.admin.new-user-default-role-codigo en application.properties."));
    }

    @Auditable(accion = "ADMIN_USER_STATUS")
    @Transactional
    public void patchStatus(Long id, AdminUserStatusPatchRequest req) {
        User u = userRepository.findById(id)
                .orElseThrow(() -> new NegocioException("Usuario no encontrado"));
        u.setStatus(req.getStatus());
        userRepository.save(u);
    }

    /**
     * Spec 011 / B2 — Vincula (o desvincula con {@code null}) la cuenta con un
     * empleado. Habilita el self-service del Portal del Empleado (PANTALLA-08).
     */
    @Auditable(accion = "ADMIN_USER_EMPLEADO")
    @Transactional
    public void asignarEmpleado(Long id, Long empleadoId) {
        User u = userRepository.findById(id)
                .orElseThrow(() -> new NegocioException("Usuario no encontrado"));
        u.setEmpleadoId(empleadoId);
        userRepository.save(u);
    }

    @Auditable(accion = "ADMIN_USER_ROLES")
    @Transactional
    public void putRoles(Long id, AdminUserRolesPutRequest req) {
        userRepository.findById(id).orElseThrow(() -> new NegocioException("Usuario no encontrado"));
        for (Long rid : req.getRoleIds()) {
            if (!rolRepository.existsById(rid)) {
                throw new NegocioException("Rol inválido");
            }
        }
        usuarioRolRepository.deleteByUserId(id);
        for (Long rid : req.getRoleIds()) {
            UsuarioRol ur = new UsuarioRol();
            ur.setUserId(id);
            ur.setRolId(rid);
            ur.setSistema("SISRH");
            usuarioRolRepository.save(ur);
        }
    }

    @Auditable(accion = "ADMIN_USER_RESET_PASSWORD")
    @Transactional
    public void resetPassword(Long id) {
        User u = userRepository.findById(id)
                .orElseThrow(() -> new NegocioException("Usuario no encontrado"));
        u.setNewClave("S");
        userRepository.save(u);
    }

    @Transactional(readOnly = true)
    public List<PermisoDeniedResponse> listDenied(Long id) {
        userRepository.findById(id).orElseThrow(() -> new NegocioException("Usuario no encontrado"));
        List<PermisoDeniedResponse> out = new ArrayList<>();
        for (UsuarioPermisoDeny d : usuarioPermisoDenyRepository.findByUserId(id)) {
            Permiso p = permisoRepository.findById(d.getPermisoId()).orElse(null);
            String nombre = p != null ? p.getDescripcion() : null;
            out.add(new PermisoDeniedResponse(d.getPermisoId(), p != null ? p.getCodigo() : null, nombre));
        }
        return out;
    }

    @Auditable(accion = "ADMIN_USER_PERMISO_DENY")
    @Transactional
    public void putDenied(Long id, AdminUserPermisoDeniesPutRequest req) {
        userRepository.findById(id).orElseThrow(() -> new NegocioException("Usuario no encontrado"));
        for (Long pid : req.getPermisoIds()) {
            if (!permisoRepository.existsById(pid)) {
                throw new NegocioException("Permiso inválido");
            }
        }
        usuarioPermisoDenyRepository.deleteByUserId(id);
        for (Long pid : req.getPermisoIds()) {
            UsuarioPermisoDeny row = new UsuarioPermisoDeny();
            row.setUserId(id);
            row.setPermisoId(pid);
            usuarioPermisoDenyRepository.save(row);
        }
    }

    @Transactional(readOnly = true)
    public List<AccesoSistemaDto> getAccesos(Long id) {
        userRepository.findById(id).orElseThrow(() -> new NegocioException("Usuario no encontrado"));
        return buildAccesos(id);
    }

    @Auditable(accion = "ADMIN_USER_ACCESOS")
    @Transactional
    public void putAccesos(Long id, AccesosPutRequest req) {
        userRepository.findById(id).orElseThrow(() -> new NegocioException("Usuario no encontrado"));
        for (AccesoSistemaPutItem item : req.accesos()) {
            String codigo = normalizeCodigoSistema(item.codigo());
            if (CODIGO_SISRH.equals(codigo)) {
                continue;
            }
            Sistema sistema = sistemaRepository.findByCodigo(codigo)
                    .orElseThrow(() -> new NegocioException("Sistema no encontrado: " + codigo));
            if (sistema.getActivo() == null || sistema.getActivo() != 1) {
                throw new NegocioException("Sistema inactivo: " + codigo);
            }
            List<String> roles = normalizeRoles(item.roles());
            if (Boolean.TRUE.equals(item.activo()) && roles.isEmpty()) {
                throw new NegocioException("Debe seleccionar al menos un rol para " + sistema.getNombre());
            }
            for (String rol : roles) {
                if (!sistemaRolRepository.existsBySistemaIdAndCodigoRolAndActivo(sistema.getId(), rol, 1)) {
                    throw new NegocioException("Rol invÃ¡lido para " + sistema.getNombre() + ": " + rol);
                }
            }

            UsuarioSistema row = usuarioSistemaRepository
                    .findByUserIdAndSistemaId(id, sistema.getId())
                    .orElseGet(() -> {
                        UsuarioSistema created = new UsuarioSistema();
                        created.setUserId(id);
                        created.setSistemaId(sistema.getId());
                        created.setCreatedAt(LocalDateTime.now());
                        return created;
                    });
            row.setActivo(Boolean.TRUE.equals(item.activo()) ? 1 : 0);
            row.setRolesExternos(Boolean.TRUE.equals(item.activo()) ? writeRoles(roles) : null);
            usuarioSistemaRepository.save(row);
        }
    }

    private List<AccesoSistemaDto> buildAccesos(Long userId) {
        Map<Long, UsuarioSistema> asignaciones = new LinkedHashMap<>();
        for (UsuarioSistema us : usuarioSistemaRepository.findByUserId(userId)) {
            asignaciones.put(us.getSistemaId(), us);
        }

        List<AccesoSistemaDto> out = new ArrayList<>();
        for (Sistema sistema : sistemaRepository.findByActivoOrderByOrdenAsc(1)) {
            if (CODIGO_SISRH.equals(sistema.getCodigo())) {
                out.add(new AccesoSistemaDto(
                        sistema.getCodigo(),
                        sistema.getNombre(),
                        !usuarioRolRepository.findByUserId(userId).isEmpty(),
                        buildSisrhRoles(userId)));
                continue;
            }
            UsuarioSistema us = asignaciones.get(sistema.getId());
            boolean activo = us != null && us.getActivo() != null && us.getActivo() == 1;
            out.add(new AccesoSistemaDto(
                    sistema.getCodigo(),
                    sistema.getNombre(),
                    activo,
                    activo ? parseRoles(us.getRolesExternos()) : List.of()));
        }
        return out;
    }

    private List<String> normalizeRoles(List<String> raw) {
        if (raw == null) {
            return List.of();
        }
        return raw.stream()
                .filter(r -> r != null && !r.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private List<String> buildSisrhRoles(Long userId) {
        return usuarioRolRepository.findByUserId(userId)
                .stream()
                .filter(ur -> ur.getSistema() == null || "SISRH".equalsIgnoreCase(ur.getSistema()))
                .map(ur -> rolRepository.findById(ur.getRolId()).orElse(null))
                .filter(rol -> rol != null && rol.getCodigo() != null)
                .map(Rol::getCodigo)
                .distinct()
                .toList();
    }

    private String normalizeCodigoSistema(String codigo) {
        return codigo == null ? "" : codigo.trim().toLowerCase(Locale.ROOT);
    }

    private List<String> parseRoles(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> roles = objectMapper.readValue(json, LIST_OF_STRING);
            return normalizeRoles(roles);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String writeRoles(List<String> roles) {
        try {
            return objectMapper.writeValueAsString(roles);
        } catch (Exception ex) {
            throw new NegocioException("No se pudieron registrar los roles externos");
        }
    }
}
