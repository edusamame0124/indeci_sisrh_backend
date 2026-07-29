-- ============================================================================
-- V012_44 — Limpieza rol ADMIN_TI (Directiva "Administrador Único Absoluto")
--
-- Contexto: ADMIN_TI se había dejado como placeholder "congelado" para un
-- futuro Soporte Técnico Nivel 1 (Helpdesk) — ver V012_43. El usuario decidió
-- retirarlo también: SUPER_ADMIN queda como único rol TI.
--
-- No toca USERS.ROLE_ID (lección de V012_43: esa columna es una FK legacy
-- hacia GESTIONRRHH.ROLES, una tabla ajena a SS_ROL — ver
-- AdminUserService.resolveLegacyUsersRoleId(), que ya no depende de SS_ROL).
--
-- Este script:
--   1. Reasigna a SUPER_ADMIN cualquier SS_USUARIO_ROL (sistema SISRH) que
--      hoy apunte a ADMIN_TI (evita duplicar si el usuario ya tiene SUPER_ADMIN).
--   2. Elimina los permisos otorgados a ADMIN_TI en SS_ROL_PERMISO.
--   3. INACTIVA (ACTIVO='N') la fila ADMIN_TI en SS_ROL. NO se elimina la fila.
--
-- Idempotente. Ejecutar en GESTIONRRHH.
-- ============================================================================

SET SERVEROUTPUT ON;
SET DEFINE OFF;

DECLARE
    v_id_admin_ti    NUMBER;
    v_id_super_admin NUMBER;
    v_filas          NUMBER := 0;
BEGIN
    SELECT ID_ROL INTO v_id_admin_ti    FROM GESTIONRRHH.SS_ROL WHERE UPPER(CODIGO) = 'ADMIN_TI';
    SELECT ID_ROL INTO v_id_super_admin FROM GESTIONRRHH.SS_ROL WHERE UPPER(CODIGO) = 'SUPER_ADMIN';

    -- ── 1. SS_USUARIO_ROL: ADMIN_TI → SUPER_ADMIN (solo sistema SISRH) ──────
    INSERT INTO GESTIONRRHH.SS_USUARIO_ROL (ID_USER, ID_ROL, SISTEMA)
    SELECT ur.ID_USER, v_id_super_admin, ur.SISTEMA
      FROM GESTIONRRHH.SS_USUARIO_ROL ur
     WHERE ur.ID_ROL = v_id_admin_ti
       AND UPPER(TRIM(ur.SISTEMA)) = 'SISRH'
       AND NOT EXISTS (
             SELECT 1 FROM GESTIONRRHH.SS_USUARIO_ROL x
              WHERE x.ID_USER = ur.ID_USER
                AND x.ID_ROL  = v_id_super_admin
                AND UPPER(TRIM(x.SISTEMA)) = 'SISRH'
           );
    v_filas := SQL%ROWCOUNT;

    DELETE FROM GESTIONRRHH.SS_USUARIO_ROL
     WHERE ID_ROL = v_id_admin_ti
       AND UPPER(TRIM(SISTEMA)) = 'SISRH';

    DBMS_OUTPUT.PUT_LINE('SS_USUARIO_ROL: ' || v_filas || ' usuario(s) reasignado(s) ADMIN_TI -> SUPER_ADMIN.');

    -- ── 2. Retirar permisos otorgados a ADMIN_TI ─────────────────────────────
    DELETE FROM GESTIONRRHH.SS_ROL_PERMISO WHERE ID_ROL = v_id_admin_ti;
    DBMS_OUTPUT.PUT_LINE('SS_ROL_PERMISO: ' || SQL%ROWCOUNT || ' permiso(s) retirado(s) de ADMIN_TI.');

    -- ── 3. Inactivar el rol (no borrar) ──────────────────────────────────────
    UPDATE GESTIONRRHH.SS_ROL
       SET ACTIVO = 'N', NOMBRE = 'Administrador TI (retirado V012_44)'
     WHERE ID_ROL = v_id_admin_ti;

    COMMIT;
    DBMS_OUTPUT.PUT_LINE('V012_44 finalizado: rol ADMIN_TI inactivado.');
EXCEPTION
    WHEN NO_DATA_FOUND THEN
        DBMS_OUTPUT.PUT_LINE('V012_44: rol ADMIN_TI o SUPER_ADMIN no existe en SS_ROL — nada que migrar.');
        ROLLBACK;
END;
/

PROMPT V012_44 — limpieza rol ADMIN_TI aplicada.
