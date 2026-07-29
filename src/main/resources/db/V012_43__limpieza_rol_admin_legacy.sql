-- ============================================================================
-- V012_43 — Limpieza rol legacy ADMIN (Directiva "Administrador Único Absoluto")
--
-- Contexto: ADMIN (NIVEL=20, "Administrador (legacy)") queda retirado como rol
-- funcional. SUPER_ADMIN pasa a ser el único rol TI de nivel superior; ADMIN_TI
-- (NIVEL=10) se mantiene como placeholder inactivo para un futuro Soporte
-- Técnico Nivel 1 (Helpdesk) — ver comentario en sisrh-roles.config.ts.
--
-- OJO: este script NO toca USERS.ROLE_ID. Esa columna es una FK legacy
-- (FK_USERS_USERS_IBFK_1) hacia GESTIONRRHH.ROLES, una tabla de 4 filas
-- (Administrador/Empleado/Branch Admin/Admin Licencias) totalmente ajena al
-- catálogo Fase 1 (SS_ROL/SS_ROL_PERMISO/SS_USUARIO_ROL). No tiene relación
-- semántica con el rol SS_ROL 'ADMIN' que este script retira — confundir
-- ambas tablas causó ORA-02291 en el intento anterior de esta migración.
-- Ver AdminUserService.resolveLegacyUsersRoleId(), corregido por separado
-- para consultar GESTIONRRHH.ROLES directamente.
--
-- Este script:
--   1. Reasigna a SUPER_ADMIN cualquier SS_USUARIO_ROL (sistema SISRH) que
--      hoy apunte a ADMIN (evita duplicar si el usuario ya tiene SUPER_ADMIN).
--   2. Elimina los permisos otorgados a ADMIN en SS_ROL_PERMISO: deja de ser
--      un rol con capacidad de otorgar permisos, aunque algún proceso no
--      filtrara por ACTIVO (obtenerPermisos() en AuthService NO filtra por
--      ACTIVO hoy — confirmado en auditoría previa a este script).
--   3. INACTIVA (ACTIVO='N') la fila ADMIN en SS_ROL. NO se elimina la fila:
--      preserva la fila por si algún registro histórico externo la referencia.
--
-- Idempotente. Ejecutar en GESTIONRRHH.
-- ============================================================================

SET SERVEROUTPUT ON;
SET DEFINE OFF;

DECLARE
    v_id_admin       NUMBER;
    v_id_super_admin NUMBER;
    v_filas          NUMBER := 0;
BEGIN
    SELECT ID_ROL INTO v_id_admin       FROM GESTIONRRHH.SS_ROL WHERE UPPER(CODIGO) = 'ADMIN';
    SELECT ID_ROL INTO v_id_super_admin FROM GESTIONRRHH.SS_ROL WHERE UPPER(CODIGO) = 'SUPER_ADMIN';

    -- ── 1. SS_USUARIO_ROL: ADMIN → SUPER_ADMIN (solo sistema SISRH) ─────────
    INSERT INTO GESTIONRRHH.SS_USUARIO_ROL (ID_USER, ID_ROL, SISTEMA)
    SELECT ur.ID_USER, v_id_super_admin, ur.SISTEMA
      FROM GESTIONRRHH.SS_USUARIO_ROL ur
     WHERE ur.ID_ROL = v_id_admin
       AND UPPER(TRIM(ur.SISTEMA)) = 'SISRH'
       AND NOT EXISTS (
             SELECT 1 FROM GESTIONRRHH.SS_USUARIO_ROL x
              WHERE x.ID_USER = ur.ID_USER
                AND x.ID_ROL  = v_id_super_admin
                AND UPPER(TRIM(x.SISTEMA)) = 'SISRH'
           );
    v_filas := SQL%ROWCOUNT;

    DELETE FROM GESTIONRRHH.SS_USUARIO_ROL
     WHERE ID_ROL = v_id_admin
       AND UPPER(TRIM(SISTEMA)) = 'SISRH';

    DBMS_OUTPUT.PUT_LINE('SS_USUARIO_ROL: ' || v_filas || ' usuario(s) reasignado(s) ADMIN -> SUPER_ADMIN.');

    -- ── 2. Retirar permisos otorgados a ADMIN ────────────────────────────────
    DELETE FROM GESTIONRRHH.SS_ROL_PERMISO WHERE ID_ROL = v_id_admin;
    DBMS_OUTPUT.PUT_LINE('SS_ROL_PERMISO: ' || SQL%ROWCOUNT || ' permiso(s) retirado(s) de ADMIN.');

    -- ── 3. Inactivar el rol (no borrar) ──────────────────────────────────────
    UPDATE GESTIONRRHH.SS_ROL
       SET ACTIVO = 'N', NOMBRE = 'Administrador (legacy, retirado V012_43)'
     WHERE ID_ROL = v_id_admin;

    COMMIT;
    DBMS_OUTPUT.PUT_LINE('V012_43 finalizado: rol ADMIN inactivado.');
EXCEPTION
    WHEN NO_DATA_FOUND THEN
        DBMS_OUTPUT.PUT_LINE('V012_43: rol ADMIN o SUPER_ADMIN no existe en SS_ROL — nada que migrar.');
        ROLLBACK;
END;
/

PROMPT V012_43 — limpieza rol legacy ADMIN aplicada.
