-- ROLLBACK V012_07 — Elimina GRUPO_SERVIDOR_CIVIL y ES_CONFIANZA.
SET SERVEROUTPUT ON;
DECLARE
    PROCEDURE drop_col(p_col VARCHAR2) IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count FROM USER_TAB_COLUMNS
         WHERE TABLE_NAME='INDECI_EMPLEADO_PLANILLA' AND COLUMN_NAME=UPPER(p_col);
        IF v_count > 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE GESTIONRRHH.INDECI_EMPLEADO_PLANILLA DROP COLUMN ' || p_col;
            DBMS_OUTPUT.PUT_LINE(p_col || ' -> eliminada.');
        ELSE
            DBMS_OUTPUT.PUT_LINE(p_col || ' no existe. Sin cambios.');
        END IF;
    END;
BEGIN
    drop_col('GRUPO_SERVIDOR_CIVIL');
    drop_col('ES_CONFIANZA');
    DBMS_OUTPUT.PUT_LINE('Rollback V012_07 finalizado.');
EXCEPTION
    WHEN OTHERS THEN
        DBMS_OUTPUT.PUT_LINE('ERROR rollback V012_07: ' || SQLERRM);
        RAISE;
END;
/
