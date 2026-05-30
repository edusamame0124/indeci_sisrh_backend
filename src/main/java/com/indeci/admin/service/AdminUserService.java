package com.indeci.admin.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.indeci.admin.dto.AdminUserCreateRequest;
import com.indeci.admin.dto.AdminUserDetailResponse;
import com.indeci.admin.dto.AdminUserPermisoDeniesPutRequest;
import com.indeci.admin.dto.AdminUserRolesPutRequest;
import com.indeci.admin.dto.AdminUserStatusPatchRequest;
import com.indeci.admin.dto.AdminUserSummaryResponse;
import com.indeci.admin.dto.PermisoDeniedResponse;
import com.indeci.audit.annotation.Auditable;
import com.indeci.exception.NegocioException;
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

    private final UserRepository userRepository;
    private final UsuarioRolRepository usuarioRolRepository;
    private final UsuarioPermisoDenyRepository usuarioPermisoDenyRepository;
    private final RolRepository rolRepository;
    private final PermisoRepository permisoRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${indeci.admin.new-user-default-role-id:}")
    private String newUserDefaultRoleIdRaw;

    @Value("${indeci.admin.new-user-default-role-codigo:}")
    private String newUserDefaultRoleCodigo;

    /** FK legacy USERS.ROLE_ID → catálogo padre (distinto del rol en SS_USUARIO_ROL). */
    @Value("${indeci.admin.new-user-legacy-role-codigo:ADMIN}")
    private String newUserLegacyRoleCodigo;

    @Transactional(readOnly = true)
    public Page<AdminUserSummaryResponse> listUsers(String q, Pageable pageable) {
        Page<User> users = (q == null || q.isBlank())
                ? userRepository.findAll(pageable)
                : userRepository.findByUsernameContainingIgnoreCase(q.trim(), pageable);
        return users.map(u -> new AdminUserSummaryResponse(u.getId(), u.getUsername(), u.getStatus()));
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
        return new AdminUserDetailResponse(u.getId(), u.getUsername(), u.getStatus(), roles, denied);
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
}
