-- ============================================================================
-- M04 / V010_83 — Marcas 3 y 4 en el detalle de asistencia
--
-- OBJETIVO: el detalle final (INDECI_ASISTENCIA_DETALLE) debe guardar las 4
--   marcas reales para que el recálculo de tardanza (ingreso + regreso de
--   almuerzo) sea autosuficiente al VALIDAR cabeceras, sin depender del staging.
--   - MARCA_ENTRADA  = Marca1 (ingreso)
--   - MARCA_SALIDA   = Marca2 (2da marca)
--   - MARCA3 (nueva) = Marca3 (regreso de refrigerio)
--   - MARCA4 (nueva) = Marca4 (salida final)
--
-- Idempotente; TABLESPACE ancla de INDECI_EMPLEADO.
-- ============================================================================

SET SERVEROUTPUT ON;

DECLARE
    v_count NUMBER;
    v_ts    VARCHAR2(30);

    PROCEDURE add_column_if_missing(p_table_name VARCHAR2, p_col_name VARCHAR2, p_col_ddl VARCHAR2) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_exists FROM ALL_TAB_COLUMNS
         WHERE OWNER = 'GESTIONRRHH' AND TABLE_NAME = p_table_name AND COLUMN_NAME = p_col_name;
        IF v_exists = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE GESTIONRRHH.' || p_table_name || ' ADD (' || p_col_ddl || ')';
            DBMS_OUTPUT.PUT_LINE(p_table_name || '.' || p_col_name || ' -> agregada.');
        ELSE
            DBMS_OUTPUT.PUT_LINE(p_table_name || '.' || p_col_name || ' ya existe. Sin cambios.');
        END IF;
    END;
BEGIN
    SELECT TABLESPACE_NAME INTO v_ts FROM USER_TABLES WHERE TABLE_NAME = 'INDECI_EMPLEADO';
    DBMS_OUTPUT.PUT_LINE('Tablespace ancla (INDECI_EMPLEADO): ' || v_ts);

    add_column_if_missing('INDECI_ASISTENCIA_DETALLE', 'MARCA3', 'MARCA3 VARCHAR2(16 CHAR)');
    add_column_if_missing('INDECI_ASISTENCIA_DETALLE', 'MARCA4', 'MARCA4 VARCHAR2(16 CHAR)');

    EXECUTE IMMEDIATE 'COMMENT ON COLUMN GESTIONRRHH.INDECI_ASISTENCIA_DETALLE.MARCA3 IS '
        || '''Marca 3 (regreso de refrigerio). Usada en el recalculo de tardanza de almuerzo.''';
    EXECUTE IMMEDIATE 'COMMENT ON COLUMN GESTIONRRHH.INDECI_ASISTENCIA_DETALLE.MARCA4 IS '
        || '''Marca 4 (salida final).''';

    DBMS_OUTPUT.PUT_LINE('V010_83 finalizado.');
    SELECT COUNT(*) INTO v_count FROM ALL_TAB_COLUMNS
     WHERE OWNER = 'GESTIONRRHH' AND TABLE_NAME = 'INDECI_ASISTENCIA_DETALLE'
       AND COLUMN_NAME IN ('MARCA3', 'MARCA4');
    DBMS_OUTPUT.PUT_LINE('Columnas detalle marcas: ' || v_count || ' / 2.');
END;
/
