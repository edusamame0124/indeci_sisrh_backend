package com.indeci.security.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import io.jsonwebtoken.Claims;

public class SecurityUtil {

    public static Long getEmpleadoId() {

        Authentication auth =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        Claims claims =
                (Claims) auth.getDetails();

        Object empleadoId =
                claims.get("empleadoId");

        if (empleadoId == null) {
            return null;
        }

        return ((Number) empleadoId)
                .longValue();
    }

    public static String getUsername() {

        Authentication auth =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        return auth.getName();
    }

    /** true si el usuario autenticado tiene alguna de las authorities dadas (permiso o "ROLE_x"). */
    public static boolean hasAnyAuthority(String... codes) {

        Authentication auth =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        java.util.Set<String> propios = auth.getAuthorities().stream()
                .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                .collect(java.util.stream.Collectors.toSet());

        for (String codigo : codes) {
            if (propios.contains(codigo)) {
                return true;
            }
        }
        return false;
    }
}