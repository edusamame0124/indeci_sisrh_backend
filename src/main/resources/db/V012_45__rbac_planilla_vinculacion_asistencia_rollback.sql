-- ============================================================================
-- V012_45 ROLLBACK — Restaura el estado previo DESDE EL SNAPSHOT.
--
-- No reconstruye permisos "según los seeds": lee las tablas BKP_V01245_* que
-- V012_45 pobló antes de modificar nada. Así el estado restaurado es el que
-- realmente tenía la BD, no el que los seeds históricos sugerían (V001 no
-- coincidía con la realidad: RRHH_ADMIN no tenía PLA_APPROVE ni REP_*, y sí
-- tenía CAT_WRITE, PLA_CTS_* y SUB_*).
--
--   1. Restaura SS_ROL (nombre y ACTIVO) de los 6 roles afectados.
--   2. Restaura SS_ROL_PERMISO exactamente como estaba para esos roles.
--   3. Restaura SS_USUARIO_ROL (sistema SISRH) de esos roles.
--   4. Retira los 3 roles nuevos (PLANILLA / VINCULACION / ASISTENCIA).
--
-- NO borra los permisos ASI_* / PER_READ / PLA_LBS_* de SS_PERMISO: quedan
-- inertes (ningún rol los tiene) y borrarlos rompería los @PreAuthorize del
-- backend si este no se revierte a la vez.
--
-- Requiere que V012_45 se haya ejecutado al menos una vez (las tablas de
-- respaldo deben existir y tener datos). Idempotente.
-- ============================================================================

SET SERVEROUTPUT ON;
SET DEFINE OFF;

DECLARE
    v_bkp NUMBER;

    PROCEDURE eliminar_rol_nuevo(p_rol VARCHAR2) IS
    BEGIN
        DELETE FROM GESTIONRRHH.SS_USUARIO_ROL
         WHERE ID_ROL IN (SELECT ID_ROL FROM GESTIONRRHH.SS_ROL WHERE UPPER(CODIGO) = UPPER(p_rol));
        DELETE FROM GESTIONRRHH.SS_ROL_PERMISO
         WHERE ID_ROL IN (SELECT ID_ROL FROM GESTIONRRHH.SS_ROL WHERE UPPER(CODIGO) = UPPER(p_rol));
        UPDATE GESTIONRRHH.SS_ROL SET ACTIVO = 'N' WHERE UPPER(CODIGO) = UPPER(p_rol);
        DBMS_OUTPUT.PUT_LINE('  Rol nuevo retirado: ' || p_rol);
    END;

BEGIN
    SELECT COUNT(*) INTO v_bkp FROM GESTIONRRHH.BKP_V01245_ROL;
    IF v_bkp = 0 THEN
        DBMS_OUTPUT.PUT_LINE('ABORTADO: BKP_V01245_ROL está vacía. '
            || 'V012_45 no llegó a tomar el snapshot; no hay estado que restaurar.');
        RETURN;
    END IF;

    -- ── 1. Restaurar metadatos de los roles afectados ───────────────────────
    UPDATE GESTIONRRHH.SS_ROL r
       SET (r.NOMBRE, r.ACTIVO) = (
             SELECT b.NOMBRE, b.ACTIVO
               FROM GESTIONRRHH.BKP_V01245_ROL b
              WHERE b.ID_ROL = r.ID_ROL)
     WHERE EXISTS (SELECT 1 FROM GESTIONRRHH.BKP_V01245_ROL b WHERE b.ID_ROL = r.ID_ROL);
    DBMS_OUTPUT.PUT_LINE('1. SS_ROL restaurado: ' || SQL%ROWCOUNT || ' rol(es).');

    -- ── 2. Restaurar SS_ROL_PERMISO de esos roles ───────────────────────────
    DELETE FROM GESTIONRRHH.SS_ROL_PERMISO
     WHERE ID_ROL IN (SELECT ID_ROL FROM GESTIONRRHH.BKP_V01245_ROL);

    INSERT INTO GESTIONRRHH.SS_ROL_PERMISO (ID_ROL, ID_PERMISO)
    SELECT ID_ROL, ID_PERMISO FROM GESTIONRRHH.BKP_V01245_ROL_PERM;
    DBMS_OUTPUT.PUT_LINE('2. SS_ROL_PERMISO restaurado: ' || SQL%ROWCOUNT || ' fila(s).');

    -- ── 3. Restaurar SS_USUARIO_ROL de esos roles ───────────────────────────
    DELETE FROM GESTIONRRHH.SS_USUARIO_ROL
     WHERE ID_ROL IN (SELECT ID_ROL FROM GESTIONRRHH.BKP_V01245_ROL)
       AND UPPER(TRIM(SISTEMA)) = 'SISRH';

    INSERT INTO GESTIONRRHH.SS_USUARIO_ROL (ID_USER, ID_ROL, SISTEMA)
    SELECT b.ID_USER, b.ID_ROL, b.SISTEMA
      FROM GESTIONRRHH.BKP_V01245_USR_ROL b
     WHERE NOT EXISTS (
           SELECT 1 FROM GESTIONRRHH.SS_USUARIO_ROL ur
            WHERE ur.ID_USER = b.ID_USER AND ur.ID_ROL = b.ID_ROL
              AND UPPER(TRIM(ur.SISTEMA)) = UPPER(TRIM(b.SISTEMA)));
    DBMS_OUTPUT.PUT_LINE('3. SS_USUARIO_ROL restaurado: ' || SQL%ROWCOUNT || ' fila(s).');

    -- ── 4. Retirar los 3 roles nuevos ───────────────────────────────────────
    eliminar_rol_nuevo('PLANILLA');
    eliminar_rol_nuevo('VINCULACION');
    eliminar_rol_nuevo('ASISTENCIA');

    COMMIT;
    DBMS_OUTPUT.PUT_LINE('V012_45 rollback finalizado desde snapshot.');
END;
/

PROMPT
PROMPT === Estado restaurado ===
SET LINESIZE 250
COLUMN rol      FORMAT A20
COLUMN permisos FORMAT A190

SELECT r.CODIGO AS rol, r.ACTIVO,
       LISTAGG(p.CODIGO, ', ') WITHIN GROUP (ORDER BY p.CODIGO) AS permisos
  FROM GESTIONRRHH.SS_ROL r
  LEFT JOIN GESTIONRRHH.SS_ROL_PERMISO rp ON rp.ID_ROL = r.ID_ROL
  LEFT JOIN GESTIONRRHH.SS_PERMISO p      ON p.ID_PERMISO = rp.ID_PERMISO
 WHERE r.ID_ROL IN (SELECT ID_ROL FROM GESTIONRRHH.BKP_V01245_ROL)
    OR UPPER(r.CODIGO) IN ('PLANILLA','VINCULACION','ASISTENCIA')
 GROUP BY r.CODIGO, r.ACTIVO
 ORDER BY r.CODIGO;

PROMPT V012_45 rollback — estado RBAC previo restaurado desde snapshot.
