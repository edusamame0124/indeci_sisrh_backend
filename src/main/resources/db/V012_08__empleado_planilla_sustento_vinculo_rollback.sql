-- ROLLBACK V012_08 — Elimina las columnas de sustento del vínculo.
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
        END IF;
    END;
BEGIN
    drop_col('DOCUMENTO_ORIGEN_TIPO');
    drop_col('DOCUMENTO_ORIGEN_NUMERO');
    drop_col('DOCUMENTO_ORIGEN_FECHA');
    DBMS_OUTPUT.PUT_LINE('Rollback V012_08 finalizado.');
EXCEPTION
    WHEN OTHERS THEN
        DBMS_OUTPUT.PUT_LINE('ERROR rollback V012_08: ' || SQLERRM);
        RAISE;
END;
/
