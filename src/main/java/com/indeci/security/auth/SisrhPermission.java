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

    /**
     * Integración GDR (Rendimiento): lectura del directorio de usuarios
     * GDR_USUARIO por parte de la cuenta de servicio svc-gdr. Menor privilegio:
     * solo habilita GET /api/sistemas/rendimiento/usuarios.
     */
    public static final String GDR_DIRECTORY_READ = "GDR_DIRECTORY_READ";

    public static final String CAT_READ = "CAT_READ";
    public static final String CAT_WRITE = "CAT_WRITE";

    public static final String EMP_READ = "EMP_READ";
    public static final String EMP_WRITE = "EMP_WRITE";

    public static final String PLA_READ = "PLA_READ";
    public static final String PLA_WRITE = "PLA_WRITE";
    public static final String PLA_APPROVE = "PLA_APPROVE";

    // Asistencia (M04). Familia propia para poder delegar la carga y corrección de
    // marcaciones SIN otorgar acceso a planilla: antes colgaban de PLA_WRITE/EMP_WRITE,
    // lo que hacía imposible segregar al rol ASISTENCIA.
    // READ = consultar asistencia de cualquier empleado; WRITE = importar/editar/recalcular.
    public static final String ASI_READ = "ASI_READ";
    public static final String ASI_WRITE = "ASI_WRITE";

    // Periodos de planilla — SOLO lectura. Permite al rol ASISTENCIA saber qué período
    // está abierto sin concederle PLA_READ (que expone montos y movimientos de planilla).
    public static final String PER_READ = "PER_READ";

    // Liquidación de CTS Trunca (feature 016). Módulo PLANILLAS / LIQUIDACIONES.
    // Semántica: READ = ver módulo/buscar cesantes/ver snapshot; WRITE = calcular/
    // precargar (PENDIENTE/CALCULADO); APPROVE = sellar y cerrar (estado inmutable).
    public static final String PLA_CTS_READ = "PLA_CTS_READ";
    public static final String PLA_CTS_WRITE = "PLA_CTS_WRITE";
    public static final String PLA_CTS_APPROVE = "PLA_CTS_APPROVE";

    public static final String PLA_LBS_READ = "PLA_LBS_READ";
    public static final String PLA_LBS_WRITE = "PLA_LBS_WRITE";

    // Reportes. Los códigos reales en SS_PERMISO son REP_READ / REP_EXPORT; las
    // constantes anteriores (RPT_READ / RPT_WRITE) no existían en el catálogo, por
    // lo que sus @PreAuthorize solo pasaban por el bypass de SUPER_ADMIN.
    public static final String REP_READ = "REP_READ";
    public static final String REP_EXPORT = "REP_EXPORT";

    public static final String SUB_READ            = "SUB_READ";
    public static final String SUB_WRITE           = "SUB_WRITE";
    public static final String SUB_VALIDATE        = "SUB_VALIDATE";
    public static final String SUB_CALCULATE       = "SUB_CALCULATE";
    public static final String SUB_APPLY_PLANILLA  = "SUB_APPLY_PLANILLA";
    public static final String SUB_ESSALUD         = "SUB_ESSALUD";
    public static final String SUB_ADJUST          = "SUB_ADJUST";
    public static final String SUB_ADMIN_CONFIG    = "SUB_ADMIN_CONFIG";
    public static final String SUB_SIMULATE        = "SUB_SIMULATE";
    public static final String SUB_EXPORT          = "SUB_EXPORT";
}
