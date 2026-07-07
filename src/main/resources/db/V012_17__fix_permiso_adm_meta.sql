-- ============================================================================
-- V012_17 — Repara el permiso ADM_META faltante en SS_PERMISO.
--
-- Hallazgo (2026-07-07): en el entorno GESTIONRRHH, SS_PERMISO tiene ADM_USERS y
-- ADM_AUDIT pero NO ADM_META (el seed V001 fase 1 quedó incompleto). Como
-- AdminRolController y AdminPermisoController exigen @PreAuthorize(ADM_META),
-- el catálogo de Roles/Permisos devuelve 403 a todos salvo SUPER_ADMIN (bypass),
-- rompiendo el bootstrap de la ficha de usuario (GET /api/admin/roles).
--
-- Este script:
--   1. Crea ADM_META en SS_PERMISO si falta (mismas columnas que V001).
--   2. Lo asigna a los roles que V001 preveía (SUPER_ADMIN, ADMIN_TI, ADMIN) y
--      a GESTOR_USUARIOS (V012_16), sin duplicar.
--
-- Idempotente (MERGE por CODIGO / por par rol+permiso). Ejecutar en GESTIONRRHH.
-- OJO: tras aplicarlo, los usuarios afectados deben cerrar sesión y volver a
-- entrar — el permiso viaja en el JWT y se resuelve en el login.
-- ============================================================================

SET SERVEROUTPUT ON;
SET DEFINE OFF;

-- ── 1. Crear el permiso ADM_META si no existe ──────────────────────────────
-- El TIPO no se hardcodea ('ADM' viola CK_SS_PERMISO_TIPO en este entorno): se
-- reutiliza el mismo valor que ya usan ADM_USERS/ADM_AUDIT, que existen y pasan
-- la constraint. Al MATCHED NO se toca el TIPO, para no romper filas válidas.
MERGE INTO GESTIONRRHH.SS_PERMISO d
USING (
    SELECT 'ADM_META' AS CODIGO,
           'Catálogo roles y permisos' AS DESCRIPCION,
           (SELECT MAX(TIPO) FROM GESTIONRRHH.SS_PERMISO
             WHERE UPPER(CODIGO) IN ('ADM_USERS', 'ADM_AUDIT')) AS TIPO
      FROM DUAL
) s ON (UPPER(d.CODIGO) = UPPER(s.CODIGO))
WHEN MATCHED THEN
    UPDATE SET d.DESCRIPCION = s.DESCRIPCION, d.ACTIVO = 'S'
WHEN NOT MATCHED THEN
    INSERT (CODIGO, DESCRIPCION, TIPO, ACTIVO, ORDEN, DESPLEGABLE)
    VALUES (s.CODIGO, s.DESCRIPCION, s.TIPO, 'S', 0, 'N');

-- ── 2. Asignar ADM_META a los roles que deben tenerlo ──────────────────────
MERGE INTO GESTIONRRHH.SS_ROL_PERMISO d
USING (
    SELECT r.ID_ROL, p.ID_PERMISO
      FROM GESTIONRRHH.SS_ROL r
      CROSS JOIN GESTIONRRHH.SS_PERMISO p
     WHERE UPPER(r.CODIGO) IN ('SUPER_ADMIN', 'ADMIN_TI', 'ADMIN', 'GESTOR_USUARIOS')
       AND UPPER(p.CODIGO) = 'ADM_META'
) s ON (d.ID_ROL = s.ID_ROL AND d.ID_PERMISO = s.ID_PERMISO)
WHEN NOT MATCHED THEN
    INSERT (ID_ROL, ID_PERMISO) VALUES (s.ID_ROL, s.ID_PERMISO);

COMMIT;

-- ── 3. Verificación en salida ──────────────────────────────────────────────
PROMPT V012_17 — permisos de GESTOR_USUARIOS tras la reparacion:
SELECT p.CODIGO
  FROM GESTIONRRHH.SS_ROL r
  JOIN GESTIONRRHH.SS_ROL_PERMISO rp ON rp.ID_ROL = r.ID_ROL
  JOIN GESTIONRRHH.SS_PERMISO   p  ON p.ID_PERMISO = rp.ID_PERMISO
 WHERE UPPER(r.CODIGO) = 'GESTOR_USUARIOS'
 ORDER BY p.CODIGO;

PROMPT V012_17 aplicado. Los usuarios deben re-loguear para refrescar el JWT.
