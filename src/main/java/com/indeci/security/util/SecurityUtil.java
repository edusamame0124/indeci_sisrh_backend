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
}