-- ============================================================================
-- Rollback V012_36 — elimina la columna JUSTIFICA_ASISTENCIA de
-- INDECI_TIPO_SOLICITUD_RRHH. Idempotente. Oracle 19c+ / GESTIONRRHH.
-- ============================================================================

SET SERVEROUTPUT ON

DECLARE
    l_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO l_cnt
    FROM all_tab_columns
    WHERE owner = 'GESTIONRRHH'
      AND table_name = 'INDECI_TIPO_SOLICITUD_RRHH'
      AND column_name = 'JUSTIFICA_ASISTENCIA';

    IF l_cnt > 0 THEN
        EXECUTE IMMEDIATE
            'ALTER TABLE GESTIONRRHH.INDECI_TIPO_SOLICITUD_RRHH DROP COLUMN JUSTIFICA_ASISTENCIA';
        DBMS_OUTPUT.PUT_LINE('Columna JUSTIFICA_ASISTENCIA eliminada.');
    ELSE
        DBMS_OUTPUT.PUT_LINE('Columna JUSTIFICA_ASISTENCIA no existe (omitida).');
    END IF;
END;
/
