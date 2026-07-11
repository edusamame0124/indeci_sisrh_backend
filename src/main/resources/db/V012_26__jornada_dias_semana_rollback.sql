-- Rollback V012_26 — elimina las columnas de jornada días/semana (idempotente).
SET SERVEROUTPUT ON;
DECLARE
    PROCEDURE drop_col(p_table VARCHAR2, p_column VARCHAR2) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_exists FROM ALL_TAB_COLUMNS
         WHERE OWNER='GESTIONRRHH' AND TABLE_NAME=p_table AND COLUMN_NAME=p_column;
        IF v_exists > 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE GESTIONRRHH.'||p_table||' DROP COLUMN '||p_column;
        END IF;
    END;
BEGIN
    drop_col('INDECI_JORNADA_REGIMEN', 'DIAS_SEMANA');
    drop_col('INDECI_EMPLEADO_PLANILLA', 'DIAS_SEMANA_OPERATIVO');
END;
/
