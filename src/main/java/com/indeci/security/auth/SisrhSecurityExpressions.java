package com.indeci.security.auth;

/**
 * Expresiones SpEL reutilizables para {@code @PreAuthorize}.
 * Los permisos se cargan como authorities desde JWT ({@code JwtAuthFilter}).
 */
public final class SisrhSecurityExpressions {

    private SisrhSecurityExpressions() {}

    public static final String ADM_USERS =
            "hasAuthority('" + SisrhPermission.ADM_USERS + "')";
    public static final String ADM_AUDIT =
            "hasAuthority('" + SisrhPermission.ADM_AUDIT + "')";
    public static final String ADM_META =
            "hasAuthority('" + SisrhPermission.ADM_META + "')";

    public static final String CAT_READ =
            "hasAuthority('" + SisrhPermission.CAT_READ + "')";
    public static final String CAT_WRITE =
            "hasAuthority('" + SisrhPermission.CAT_WRITE + "')";

    public static final String EMP_READ =
            "hasAuthority('" + SisrhPermission.EMP_READ + "')";
    public static final String EMP_WRITE =
            "hasAuthority('" + SisrhPermission.EMP_WRITE + "')";

    public static final String PLA_READ =
            "hasAuthority('" + SisrhPermission.PLA_READ + "')";
    public static final String PLA_WRITE =
            "hasAuthority('" + SisrhPermission.PLA_WRITE + "')";
    public static final String PLA_APPROVE =
            "hasAuthority('" + SisrhPermission.PLA_APPROVE + "')";

    public static final String RPT_READ =
            "hasAuthority('" + SisrhPermission.RPT_READ + "')";
    public static final String RPT_WRITE =
            "hasAuthority('" + SisrhPermission.RPT_WRITE + "')";
}
