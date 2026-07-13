-- ============================================================================
-- V012_34 — Papeleta de Teletrabajo: modalidad seleccionable (PARCIAL/COMPLETA).
-- Se agrega la columna MODALIDAD_TELETRABAJO a INDECI_SOLICITUD_RRHH. Idempotente.
-- ============================================================================
SET SERVEROUTPUT ON

DECLARE
    l_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO l_cnt
    FROM all_tab_columns
    WHERE owner = 'GESTIONRRHH'
      AND table_name = 'INDECI_SOLICITUD_RRHH'
      AND column_name = 'MODALIDAD_TELETRABAJO';

    IF l_cnt = 0 THEN
        EXECUTE IMMEDIATE
            'ALTER TABLE GESTIONRRHH.INDECI_SOLICITUD_RRHH ADD (MODALIDAD_TELETRABAJO VARCHAR2(20))';
        DBMS_OUTPUT.PUT_LINE('Columna MODALIDAD_TELETRABAJO agregada.');
    ELSE
        DBMS_OUTPUT.PUT_LINE('Columna MODALIDAD_TELETRABAJO ya existe (omitida).');
    END IF;
END;
/
