-- ============================================================================
-- V012_46 ROLLBACK — Quita la columna FORMATO_ORIGEN.
--
-- Idempotente. Ejecutar en GESTIONRRHH.
-- ============================================================================

SET SERVEROUTPUT ON;
SET DEFINE OFF;

DECLARE
    v_existe NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_existe
      FROM all_tab_columns
     WHERE owner = 'GESTIONRRHH'
       AND table_name = 'INDECI_ASISTENCIA_IMPORTACION'
       AND column_name = 'FORMATO_ORIGEN';

    IF v_existe > 0 THEN
        EXECUTE IMMEDIATE '
            ALTER TABLE GESTIONRRHH.INDECI_ASISTENCIA_IMPORTACION
            DROP COLUMN FORMATO_ORIGEN';
        DBMS_OUTPUT.PUT_LINE('V012_46 rollback: columna FORMATO_ORIGEN eliminada.');
    ELSE
        DBMS_OUTPUT.PUT_LINE('V012_46 rollback: la columna FORMATO_ORIGEN no existía.');
    END IF;
END;
/

PROMPT V012_46 rollback — columna FORMATO_ORIGEN eliminada.
