-- ============================================================================
-- V012_44 ROLLBACK — Reactiva el rol ADMIN_TI.
--
-- NO revierte la reasignación de usuarios (SS_USUARIO_ROL): esos cambios
-- movieron usuarios reales a SUPER_ADMIN y deshacerlos a ciegas podría chocar
-- con asignaciones hechas después de la migración. Si se necesita revertir la
-- asignación de un usuario puntual, hacerlo a mano desde Administración > Usuarios.
--
-- Este rollback solo:
--   1. Reactiva la fila ADMIN_TI en SS_ROL (ACTIVO='S') y restaura su NOMBRE.
--   2. Restaura en SS_ROL_PERMISO los mismos permisos que V001 (fase 1) le dio
--      originalmente (idénticos a los de SUPER_ADMIN/ADMIN).
--
-- Idempotente. Ejecutar en GESTIONRRHH.
-- ============================================================================

SET SERVEROUTPUT ON;
SET DEFINE OFF;

DECLARE
    v_id_admin_ti NUMBER;
BEGIN
    UPDATE GESTIONRRHH.SS_ROL
       SET ACTIVO = 'S', NOMBRE = 'Administrador TI'
     WHERE UPPER(CODIGO) = 'ADMIN_TI';

    SELECT ID_ROL INTO v_id_admin_ti FROM GESTIONRRHH.SS_ROL WHERE UPPER(CODIGO) = 'ADMIN_TI';

    INSERT INTO GESTIONRRHH.SS_ROL_PERMISO (ID_ROL, ID_PERMISO)
    SELECT v_id_admin_ti, p.ID_PERMISO
      FROM GESTIONRRHH.SS_PERMISO p
     WHERE UPPER(p.CODIGO) IN (
           'ADM_USERS','ADM_AUDIT','ADM_META',
           'CAT_READ','CAT_WRITE','EMP_READ','EMP_WRITE',
           'PLA_READ','PLA_WRITE','PLA_APPROVE','RPT_READ','RPT_WRITE')
       AND NOT EXISTS (
             SELECT 1 FROM GESTIONRRHH.SS_ROL_PERMISO rp
              WHERE rp.ID_ROL = v_id_admin_ti AND rp.ID_PERMISO = p.ID_PERMISO
           );

    COMMIT;
    DBMS_OUTPUT.PUT_LINE('V012_44 rollback: rol ADMIN_TI reactivado y permisos restaurados.');
EXCEPTION
    WHEN NO_DATA_FOUND THEN
        DBMS_OUTPUT.PUT_LINE('V012_44 rollback: rol ADMIN_TI no existe en SS_ROL.');
        ROLLBACK;
END;
/

PROMPT V012_44 rollback — rol ADMIN_TI reactivado.
