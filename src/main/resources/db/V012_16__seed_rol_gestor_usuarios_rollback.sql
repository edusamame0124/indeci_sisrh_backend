-- ============================================================================
-- V012_16 ROLLBACK — Elimina el rol GESTOR_USUARIOS y sus asignaciones.
--
-- Quita primero las asignaciones a usuarios (SS_USUARIO_ROL, FK_SSUR_ROL) y los
-- permisos del rol (SS_ROL_PERMISO), y solo entonces el rol en SS_ROL. Si no se
-- borran las filas hijas primero, Oracle lanza ORA-02292 (FK_SSUR_ROL violada).
-- No toca los permisos ADM_* (compartidos con otros roles). Idempotente.
-- Ejecutar en GESTIONRRHH / Oracle 19c+.
-- ============================================================================

SET SERVEROUTPUT ON;
SET DEFINE OFF;

DECLARE
    v_id_rol GESTIONRRHH.SS_ROL.ID_ROL%TYPE;
BEGIN
    SELECT ID_ROL INTO v_id_rol
      FROM GESTIONRRHH.SS_ROL
     WHERE UPPER(CODIGO) = 'GESTOR_USUARIOS';

    -- Orden obligatorio: primero las filas hijas (FKs), luego el rol.
    DELETE FROM GESTIONRRHH.SS_USUARIO_ROL WHERE ID_ROL = v_id_rol;
    DELETE FROM GESTIONRRHH.SS_ROL_PERMISO WHERE ID_ROL = v_id_rol;
    DELETE FROM GESTIONRRHH.SS_ROL         WHERE ID_ROL = v_id_rol;

    DBMS_OUTPUT.PUT_LINE('V012_16 rollback: rol GESTOR_USUARIOS eliminado.');
    COMMIT;
EXCEPTION
    WHEN NO_DATA_FOUND THEN
        DBMS_OUTPUT.PUT_LINE('V012_16 rollback: GESTOR_USUARIOS no existe. Sin cambios.');
END;
/

PROMPT V012_16 rollback aplicado.
