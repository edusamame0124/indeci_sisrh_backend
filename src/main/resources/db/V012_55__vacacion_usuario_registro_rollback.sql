-- Rollback V012_55 — elimina USUARIO_REGISTRO de INDECI_VACACIONES (idempotente).
SET SERVEROUTPUT ON;
DECLARE
    PROCEDURE drop_column_if_exists(p_column VARCHAR2) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_exists
          FROM ALL_TAB_COLUMNS
         WHERE OWNER = 'GESTIONRRHH' AND TABLE_NAME = 'INDECI_VACACIONES' AND COLUMN_NAME = p_column;
        IF v_exists > 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE GESTIONRRHH.INDECI_VACACIONES DROP COLUMN ' || p_column;
            DBMS_OUTPUT.PUT_LINE('INDECI_VACACIONES.' || p_column || ' -> eliminada.');
        END IF;
    END;
BEGIN
    drop_column_if_exists('USUARIO_REGISTRO');
END;
/
