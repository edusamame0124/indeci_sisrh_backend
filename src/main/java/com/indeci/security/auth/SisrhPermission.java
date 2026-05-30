package com.indeci.security.auth;

/**
 * Códigos de permiso SISRH (gestionrrhh.SS_PERMISO.CODIGO).
 * Deben coincidir con {@code docs/db/V001__seed_roles_permisos_fase1.sql}.
 */
public final class SisrhPermission {

    private SisrhPermission() {}

    public static final String ADM_USERS = "ADM_USERS";
    public static final String ADM_AUDIT = "ADM_AUDIT";
    public static final String ADM_META = "ADM_META";

    public static final String CAT_READ = "CAT_READ";
    public static final String CAT_WRITE = "CAT_WRITE";

    public static final String EMP_READ = "EMP_READ";
    public static final String EMP_WRITE = "EMP_WRITE";

    public static final String PLA_READ = "PLA_READ";
    public static final String PLA_WRITE = "PLA_WRITE";
    public static final String PLA_APPROVE = "PLA_APPROVE";

    public static final String RPT_READ = "RPT_READ";
    public static final String RPT_WRITE = "RPT_WRITE";
}
