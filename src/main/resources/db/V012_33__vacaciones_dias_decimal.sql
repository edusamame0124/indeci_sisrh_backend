-- ============================================================================
-- V012_33 — Art. 35 (fraccionamiento con media jornada): la columna DIAS de
-- INDECI_VACACIONES debe admitir 0.5. Se amplía de entero a NUMBER(4,1).
-- Idempotente: si ya es NUMBER(4,1) no hace nada; MODIFY es reejecutable.
-- ============================================================================
SET SERVEROUTPUT ON

DECLARE
    l_scale NUMBER;
BEGIN
    SELECT data_scale INTO l_scale
    FROM all_tab_columns
    WHERE owner = 'GESTIONRRHH'
      AND table_name = 'INDECI_VACACIONES'
      AND column_name = 'DIAS';

    IF l_scale IS NULL OR l_scale < 1 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE GESTIONRRHH.INDECI_VACACIONES MODIFY (DIAS NUMBER(4,1))';
        DBMS_OUTPUT.PUT_LINE('INDECI_VACACIONES.DIAS ampliada a NUMBER(4,1).');
    ELSE
        DBMS_OUTPUT.PUT_LINE('INDECI_VACACIONES.DIAS ya admite decimales (omitido).');
    END IF;
EXCEPTION
    WHEN NO_DATA_FOUND THEN
        DBMS_OUTPUT.PUT_LINE('Columna INDECI_VACACIONES.DIAS no encontrada — revisar esquema.');
END;
/
