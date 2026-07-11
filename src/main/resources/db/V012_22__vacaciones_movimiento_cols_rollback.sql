-- Rollback V012_22 — elimina las columnas de movimiento de INDECI_VACACIONES (idempotente).
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
    drop_column_if_exists('ANIO_PERIODO');
    drop_column_if_exists('TIPO_GOCE');
    drop_column_if_exists('DIAS');
    drop_column_if_exists('ESTADO');
    drop_column_if_exists('ES_ADELANTO');
    drop_column_if_exists('DOCUMENTO_SUSTENTO');
    drop_column_if_exists('MOTIVO_EXCEPCION');
    drop_column_if_exists('FECHA_REINCORPORACION');
    drop_column_if_exists('ORIGEN');
    drop_column_if_exists('SOLICITUD_RRHH_ID');
END;
/
