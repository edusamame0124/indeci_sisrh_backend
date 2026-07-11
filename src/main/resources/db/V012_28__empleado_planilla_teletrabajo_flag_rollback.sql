-- ============================================================================
-- V012_28 ROLLBACK — elimina el flag de teletrabajo del vínculo remunerativo.
-- Idempotente. Oracle 19c+ / GESTIONRRHH.
-- ============================================================================

SET SERVEROUTPUT ON;

DECLARE
    v NUMBER;
BEGIN
    SELECT COUNT(*) INTO v
      FROM USER_TAB_COLUMNS
     WHERE TABLE_NAME = 'INDECI_EMPLEADO_PLANILLA'
       AND COLUMN_NAME = 'ES_TELETRABAJADOR';

    IF v > 0 THEN
        EXECUTE IMMEDIATE
            'ALTER TABLE INDECI_EMPLEADO_PLANILLA DROP COLUMN ES_TELETRABAJADOR';
        DBMS_OUTPUT.PUT_LINE('ES_TELETRABAJADOR -> eliminada.');
    ELSE
        DBMS_OUTPUT.PUT_LINE('ES_TELETRABAJADOR no existe. Sin cambios.');
    END IF;

    COMMIT;
    DBMS_OUTPUT.PUT_LINE('V012_28 rollback finalizado.');
END;
/
