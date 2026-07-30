-- ============================================================================
-- V012_47 ROLLBACK — Restaura el estado previo DESDE EL SNAPSHOT.
--
-- Para cada usuario respaldado (cuentas-DNI y sus cuentas antiguas):
--   1. Restaura USERS (EMPLEADO_ID, STATUS).
--   2. Restaura INDECI_PERSONA.USER_ID.
--   3. Restaura SS_USUARIO_ROL / SS_USUARIO_PERMISO / SS_USUARIO_PERMISO_DENY
--      exactamente como estaban (borra lo actual de esos usuarios e inserta
--      lo respaldado).
--   4. Marca los candidatos como no procesados, por si se decide reintentar
--      la migración más adelante.
--
-- Requiere que V012_47 se haya ejecutado al menos una vez (tablas de respaldo
-- deben existir y tener datos). Idempotente.
-- ============================================================================

SET SERVEROUTPUT ON;
SET DEFINE OFF;

DECLARE
    v_bkp NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_bkp FROM GESTIONRRHH.BKP_V01247_USERS;
    IF v_bkp = 0 THEN
        DBMS_OUTPUT.PUT_LINE('ABORTADO: BKP_V01247_USERS está vacía. '
            || 'V012_47 no llegó a tomar el respaldo; no hay estado que restaurar.');
        RETURN;
    END IF;

    -- ── 1. Restaurar USERS ──────────────────────────────────────────────────
    UPDATE GESTIONRRHH.USERS u
       SET (u.EMPLEADO_ID, u.STATUS) = (
             SELECT b.EMPLEADO_ID, b.STATUS
               FROM GESTIONRRHH.BKP_V01247_USERS b
              WHERE b.ID = u.ID)
     WHERE EXISTS (SELECT 1 FROM GESTIONRRHH.BKP_V01247_USERS b WHERE b.ID = u.ID);
    DBMS_OUTPUT.PUT_LINE('1. USERS restaurado: ' || SQL%ROWCOUNT || ' cuenta(s).');

    -- ── 2. Restaurar INDECI_PERSONA.USER_ID ─────────────────────────────────
    UPDATE GESTIONRRHH.INDECI_PERSONA p
       SET p.USER_ID = (
             SELECT b.USER_ID FROM GESTIONRRHH.BKP_V01247_PERSONA b WHERE b.ID = p.ID)
     WHERE EXISTS (SELECT 1 FROM GESTIONRRHH.BKP_V01247_PERSONA b WHERE b.ID = p.ID);
    DBMS_OUTPUT.PUT_LINE('2. INDECI_PERSONA restaurado: ' || SQL%ROWCOUNT || ' fila(s).');

    -- ── 3. Restaurar roles/permisos/denies de esos usuarios ─────────────────
    DELETE FROM GESTIONRRHH.SS_USUARIO_ROL
     WHERE ID_USER IN (SELECT ID FROM GESTIONRRHH.BKP_V01247_USERS);
    INSERT INTO GESTIONRRHH.SS_USUARIO_ROL (ID_USER, ID_ROL, SISTEMA)
    SELECT ID_USER, ID_ROL, SISTEMA FROM GESTIONRRHH.BKP_V01247_USR_ROL;
    DBMS_OUTPUT.PUT_LINE('3a. SS_USUARIO_ROL restaurado: ' || SQL%ROWCOUNT || ' fila(s).');

    DELETE FROM GESTIONRRHH.SS_USUARIO_PERMISO
     WHERE ID_USER IN (SELECT ID FROM GESTIONRRHH.BKP_V01247_USERS);
    INSERT INTO GESTIONRRHH.SS_USUARIO_PERMISO (ID_USER, ID_PERMISO)
    SELECT ID_USER, ID_PERMISO FROM GESTIONRRHH.BKP_V01247_USR_PERM;
    DBMS_OUTPUT.PUT_LINE('3b. SS_USUARIO_PERMISO restaurado: ' || SQL%ROWCOUNT || ' fila(s).');

    DELETE FROM GESTIONRRHH.SS_USUARIO_PERMISO_DENY
     WHERE ID_USER IN (SELECT ID FROM GESTIONRRHH.BKP_V01247_USERS);
    INSERT INTO GESTIONRRHH.SS_USUARIO_PERMISO_DENY (ID_USER, ID_PERMISO)
    SELECT ID_USER, ID_PERMISO FROM GESTIONRRHH.BKP_V01247_USR_PERM_DENY;
    DBMS_OUTPUT.PUT_LINE('3c. SS_USUARIO_PERMISO_DENY restaurado: ' || SQL%ROWCOUNT || ' fila(s).');

    -- ── 4. Reabrir los candidatos por si se reintenta la migración ──────────
    UPDATE GESTIONRRHH.BKP_V01247_CANDIDATOS SET PROCESADO = 'N';
    DBMS_OUTPUT.PUT_LINE('4. Candidatos reabiertos: ' || SQL%ROWCOUNT || ' fila(s).');

    COMMIT;
    DBMS_OUTPUT.PUT_LINE('V012_47 rollback finalizado desde snapshot.');
END;
/

PROMPT V012_47 rollback — estado previo a la consolidación por DNI restaurado.
