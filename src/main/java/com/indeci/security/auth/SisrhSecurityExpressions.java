package com.indeci.security.auth;

/**
 * Expresiones SpEL reutilizables para {@code @PreAuthorize}.
 * Los permisos se cargan como authorities desde JWT ({@code JwtAuthFilter}).
 */
public final class SisrhSecurityExpressions {

    private SisrhSecurityExpressions() {}

    private static final String SA = "hasAuthority('ROLE_SUPER_ADMIN')";

    public static final String SUPER_ADMIN = SA;

    public static final String ADM_USERS   = SA + " or hasAuthority('" + SisrhPermission.ADM_USERS   + "')";
    public static final String ADM_AUDIT   = SA + " or hasAuthority('" + SisrhPermission.ADM_AUDIT   + "')";
    public static final String ADM_META    = SA + " or hasAuthority('" + SisrhPermission.ADM_META    + "')";

    public static final String CAT_READ    = SA + " or hasAuthority('" + SisrhPermission.CAT_READ    + "')";
    public static final String CAT_WRITE   = SA + " or hasAuthority('" + SisrhPermission.CAT_WRITE   + "')";

    public static final String EMP_READ    = SA + " or hasAuthority('" + SisrhPermission.EMP_READ    + "')";
    public static final String EMP_WRITE   = SA + " or hasAuthority('" + SisrhPermission.EMP_WRITE   + "')";

    public static final String PLA_READ    = SA + " or hasAuthority('" + SisrhPermission.PLA_READ    + "')";
    public static final String PLA_WRITE   = SA + " or hasAuthority('" + SisrhPermission.PLA_WRITE   + "')";
    public static final String PLA_APPROVE = SA + " or hasAuthority('" + SisrhPermission.PLA_APPROVE + "')";

    public static final String RPT_READ    = SA + " or hasAuthority('" + SisrhPermission.RPT_READ    + "')";
    public static final String RPT_WRITE   = SA + " or hasAuthority('" + SisrhPermission.RPT_WRITE   + "')";

}
