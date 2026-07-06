-- ============================================================
-- ROLLBACK V012_04 — Elimina MOTIVO_CESE y DOCUMENTO_CESE.
-- Idempotente: solo elimina si existen.
-- ============================================================

SET SERVEROUTPUT ON;

DECLARE
    PROCEDURE drop_col_si_existe(p_col VARCHAR2) IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count
          FROM USER_TAB_COLUMNS
         WHERE TABLE_NAME = 'INDECI_EMPLEADO_PLANILLA'
           AND COLUMN_NAME = UPPER(p_col);
        IF v_count > 0 THEN
            EXECUTE IMMEDIATE
                'ALTER TABLE GESTIONRRHH.INDECI_EMPLEADO_PLANILLA DROP COLUMN ' || p_col;
            DBMS_OUTPUT.PUT_LINE(p_col || ' -> columna eliminada.');
        ELSE
            DBMS_OUTPUT.PUT_LINE(p_col || ' no existe. Sin cambios.');
        END IF;
    END;
BEGIN
    drop_col_si_existe('MOTIVO_CESE');
    drop_col_si_existe('DOCUMENTO_CESE');
    DBMS_OUTPUT.PUT_LINE('Rollback V012_04 finalizado.');
EXCEPTION
    WHEN OTHERS THEN
        DBMS_OUTPUT.PUT_LINE('ERROR en rollback V012_04: ' || SQLERRM);
        RAISE;
END;
/
