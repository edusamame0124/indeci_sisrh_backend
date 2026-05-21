-- ============================================================================
-- Spec 010 / V010_18 — Monto AIRHSP del empleado (SPEC §10.2)
--
-- Habilita el PASO 16 del motor (conciliación AIRHSP automática) y la
-- PANTALLA-06: el motor compara el monto calculado por el sistema contra el
-- AIRHSP_MONTO registrado en el MEF.
--
-- COLUMNA:
--   INDECI_EMPLEADO.AIRHSP_MONTO  NUMBER(12,2)
--     → monto de remuneración vigente del empleado en AIRHSP (MEF).
--     → NULL = sin registro AIRHSP cargado; el motor lo trata como 0.
--
-- El SPEC §10.2 ya preveía esta columna; V010_07 solo agregó REGISTRO_AIRHSP
-- (el código). Aquí se completa con el monto.
--
-- DEFENSA EN PROFUNDIDAD: idempotente (add_column_if_missing).
-- Ejecutar en esquema GESTIONRRHH / Oracle 19c+.
-- ============================================================================

SET SERVEROUTPUT ON;

DECLARE
    PROCEDURE add_column_if_missing(
        p_col_name VARCHAR2,
        p_col_ddl  VARCHAR2
    ) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*)
          INTO v_exists
          FROM ALL_TAB_COLUMNS
         WHERE OWNER       = 'GESTIONRRHH'
           AND TABLE_NAME  = 'INDECI_EMPLEADO'
           AND COLUMN_NAME = p_col_name;

        IF v_exists = 0 THEN
            EXECUTE IMMEDIATE
                'ALTER TABLE GESTIONRRHH.INDECI_EMPLEADO ADD ('
                || p_col_ddl || ')';
            DBMS_OUTPUT.PUT_LINE(p_col_name || ' -> agregada.');
        ELSE
            DBMS_OUTPUT.PUT_LINE(p_col_name || ' ya existe. Sin cambios.');
        END IF;
    END;
BEGIN
    add_column_if_missing(
        'AIRHSP_MONTO',
        'AIRHSP_MONTO NUMBER(12,2)');

    EXECUTE IMMEDIATE
        'COMMENT ON COLUMN GESTIONRRHH.INDECI_EMPLEADO.AIRHSP_MONTO IS ' ||
        '''SPEC §10.2 — Monto de remuneración vigente del empleado en AIRHSP (MEF). ' ||
        'Base de la conciliación M13 / PASO 16. NULL = sin registro; el motor lo trata como 0.''';

    DBMS_OUTPUT.PUT_LINE('V010_18 finalizado.');
END;
/
