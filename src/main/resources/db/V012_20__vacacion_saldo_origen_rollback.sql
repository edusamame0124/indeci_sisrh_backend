-- Rollback V012_20 — elimina ORIGEN y FECHA_CORTE de INDECI_VACACION_SALDO.
SET SERVEROUTPUT ON;
DECLARE
    PROCEDURE drop_column_if_exists(p_table VARCHAR2, p_column VARCHAR2) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_exists
          FROM ALL_TAB_COLUMNS
         WHERE OWNER = 'GESTIONRRHH' AND TABLE_NAME = p_table AND COLUMN_NAME = p_column;
        IF v_exists > 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE GESTIONRRHH.' || p_table || ' DROP COLUMN ' || p_column;
            DBMS_OUTPUT.PUT_LINE(p_table || '.' || p_column || ' -> eliminada.');
        END IF;
    END;
BEGIN
    drop_column_if_exists('INDECI_VACACION_SALDO', 'ORIGEN');
    drop_column_if_exists('INDECI_VACACION_SALDO', 'FECHA_CORTE');
END;
/
