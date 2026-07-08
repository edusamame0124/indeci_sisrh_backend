-- ============================================================================
-- V012_19 ROLLBACK — elimina INDECI_EMPLEADO_MARCADOR_ALIAS.
-- Idempotente. Ejecutar en GESTIONRRHH / Oracle 19c+.
-- ============================================================================

SET SERVEROUTPUT ON;

DECLARE
    v NUMBER;
BEGIN
    SELECT COUNT(*) INTO v FROM USER_TABLES
     WHERE TABLE_NAME = 'INDECI_EMPLEADO_MARCADOR_ALIAS';
    IF v > 0 THEN
        EXECUTE IMMEDIATE 'DROP TABLE INDECI_EMPLEADO_MARCADOR_ALIAS '
            || 'CASCADE CONSTRAINTS PURGE';
        DBMS_OUTPUT.PUT_LINE('INDECI_EMPLEADO_MARCADOR_ALIAS -> eliminada.');
    ELSE
        DBMS_OUTPUT.PUT_LINE('INDECI_EMPLEADO_MARCADOR_ALIAS no existe. Sin cambios.');
    END IF;
    DBMS_OUTPUT.PUT_LINE('V012_19 rollback finalizado.');
END;
/
