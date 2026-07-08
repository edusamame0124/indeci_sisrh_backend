-- ============================================================================
-- V012_18 ROLLBACK — elimina el calendario laboral (FERIADOS y DESCANSOS).
-- Idempotente: no falla si las tablas ya no existen.
-- Ejecutar en GESTIONRRHH / Oracle 19c+.
-- ============================================================================

SET SERVEROUTPUT ON;

DECLARE
    PROCEDURE drop_table_if_exists(p_table VARCHAR2) IS
        v NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v FROM USER_TABLES WHERE TABLE_NAME = p_table;
        IF v > 0 THEN
            EXECUTE IMMEDIATE 'DROP TABLE ' || p_table || ' CASCADE CONSTRAINTS PURGE';
            DBMS_OUTPUT.PUT_LINE(p_table || ' -> eliminada.');
        ELSE
            DBMS_OUTPUT.PUT_LINE(p_table || ' no existe. Sin cambios.');
        END IF;
    END;
BEGIN
    drop_table_if_exists('INDECI_DESCANSO_SEMANAL');
    drop_table_if_exists('INDECI_FERIADO');
    DBMS_OUTPUT.PUT_LINE('V012_18 rollback finalizado.');
END;
/
