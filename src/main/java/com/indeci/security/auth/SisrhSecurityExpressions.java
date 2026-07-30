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

    public static final String GDR_DIRECTORY_READ = SA + " or hasAuthority('" + SisrhPermission.GDR_DIRECTORY_READ + "')";

    public static final String CAT_WRITE   = SA + " or hasAuthority('" + SisrhPermission.CAT_WRITE   + "')";

    public static final String EMP_READ    = SA + " or hasAuthority('" + SisrhPermission.EMP_READ    + "')";
    public static final String EMP_WRITE   = SA + " or hasAuthority('" + SisrhPermission.EMP_WRITE   + "')";

    public static final String PLA_READ    = SA + " or hasAuthority('" + SisrhPermission.PLA_READ    + "')";
    public static final String PLA_WRITE   = SA + " or hasAuthority('" + SisrhPermission.PLA_WRITE   + "')";
    public static final String PLA_APPROVE = SA + " or hasAuthority('" + SisrhPermission.PLA_APPROVE + "')";

    /** Asistencia (M04) — familia propia, independiente de PLA_* y EMP_*. */
    public static final String ASI_READ    = SA + " or hasAuthority('" + SisrhPermission.ASI_READ    + "')";
    public static final String ASI_WRITE   = SA + " or hasAuthority('" + SisrhPermission.ASI_WRITE   + "')";

    /**
     * Lectura de períodos de planilla. Acepta PLA_READ (rol PLANILLA, acceso pleno)
     * o PER_READ (rol ASISTENCIA, que solo necesita ver qué período está abierto).
     * La escritura y la aprobación siguen exigiendo PLA_WRITE / PLA_APPROVE.
     */
    public static final String PERIODO_READ = SA
            + " or hasAuthority('" + SisrhPermission.PLA_READ + "')"
            + " or hasAuthority('" + SisrhPermission.PER_READ + "')";

    /**
     * Lectura de la configuración de jornada y tolerancias (M04). La consumen tanto
     * Planilla como Asistencia: el cálculo de tardanzas depende de ella. Antes la
     * clase entera exigía PLA_WRITE, de modo que un GET de configuración pedía
     * permiso de ESCRITURA de planilla y dejaba fuera al rol ASISTENCIA.
     * La escritura de esa configuración sigue siendo PLA_WRITE.
     */
    public static final String JORNADA_READ = SA
            + " or hasAuthority('" + SisrhPermission.ASI_READ + "')"
            + " or hasAuthority('" + SisrhPermission.PLA_READ + "')";

    public static final String PLA_CTS_READ    = SA + " or hasAuthority('" + SisrhPermission.PLA_CTS_READ    + "')";
    public static final String PLA_CTS_WRITE   = SA + " or hasAuthority('" + SisrhPermission.PLA_CTS_WRITE   + "')";
    public static final String PLA_CTS_APPROVE = SA + " or hasAuthority('" + SisrhPermission.PLA_CTS_APPROVE + "')";

    public static final String PLA_LBS_READ    = SA + " or hasAuthority('" + SisrhPermission.PLA_LBS_READ    + "')";
    public static final String PLA_LBS_WRITE   = SA + " or hasAuthority('" + SisrhPermission.PLA_LBS_WRITE   + "')";

    /** Reportes — códigos reales del catálogo: REP_READ / REP_EXPORT. */
    public static final String REP_READ    = SA + " or hasAuthority('" + SisrhPermission.REP_READ    + "')";
    public static final String REP_EXPORT  = SA + " or hasAuthority('" + SisrhPermission.REP_EXPORT  + "')";

    public static final String SUB_READ           = SA + " or hasAuthority('" + SisrhPermission.SUB_READ           + "')";
    public static final String SUB_WRITE          = SA + " or hasAuthority('" + SisrhPermission.SUB_WRITE          + "')";
    public static final String SUB_VALIDATE       = SA + " or hasAuthority('" + SisrhPermission.SUB_VALIDATE       + "')";
    public static final String SUB_CALCULATE      = SA + " or hasAuthority('" + SisrhPermission.SUB_CALCULATE      + "')";
    public static final String SUB_APPLY_PLANILLA = SA + " or hasAuthority('" + SisrhPermission.SUB_APPLY_PLANILLA + "')";
    public static final String SUB_ESSALUD        = SA + " or hasAuthority('" + SisrhPermission.SUB_ESSALUD        + "')";
    public static final String SUB_ADJUST         = SA + " or hasAuthority('" + SisrhPermission.SUB_ADJUST         + "')";
    public static final String SUB_ADMIN_CONFIG   = SA + " or hasAuthority('" + SisrhPermission.SUB_ADMIN_CONFIG   + "')";
    public static final String SUB_SIMULATE       = SA + " or hasAuthority('" + SisrhPermission.SUB_SIMULATE       + "')";
    public static final String SUB_EXPORT         = SA + " or hasAuthority('" + SisrhPermission.SUB_EXPORT         + "')";

}
