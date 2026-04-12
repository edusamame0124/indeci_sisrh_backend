package com.indice.auth.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.indice.security.jwt.JwtProvider;
import com.indice.user.entity.*;
import com.indice.user.repository.*;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final UsuarioRolRepository usuarioRolRepository;
    private final RolRepository rolRepository;
    private final RolPermisoRepository rolPermisoRepository;
    private final PermisoRepository permisoRepository;
    private final JwtProvider jwtProvider;
    private final PasswordEncoder passwordEncoder;

    public String login(String username, String password) {

        // 🔥 1. Buscar usuario
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Usuario no existe"));

        // 🔥 2. Validar password
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("Credenciales inválidas");
        }

        // 🔥 3. Validar estado
        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new RuntimeException("Usuario inactivo");
        }

        // 🔥 4. Obtener roles
        List<UsuarioRol> usuarioRoles =
                usuarioRolRepository.findByUserId(user.getId());

        List<String> roles = new ArrayList<>();

        for (UsuarioRol ur : usuarioRoles) {
            Rol rol = rolRepository.findById(ur.getRolId()).orElse(null);
            if (rol != null) {
                roles.add(rol.getCodigo());
            }
        }

        // 🔥 5. Obtener permisos
        List<String> permisos = new ArrayList<>();

        for (UsuarioRol ur : usuarioRoles) {

            List<RolPermiso> rolPermisos =
                    rolPermisoRepository.findByRolId(ur.getRolId());

            for (RolPermiso rp : rolPermisos) {
                Permiso p = permisoRepository.findById(rp.getPermisoId()).orElse(null);
                if (p != null) {
                    permisos.add(p.getCodigo());
                }
            }
        }

        // 🔥 6. Generar JWT
        return jwtProvider.generarTokenDefinitivo(user, roles, permisos);
    }
}