-- ============================================================
-- V012_08 — Sustento del vínculo (F4b)
--
-- Documento de origen del vínculo (contrato/resolución/adenda/designación).
-- Los cambios posteriores (cese, cambio remunerativo) ya tienen su propio
-- sustento en sus tablas/campos; esto captura el documento INICIAL del vínculo.
--
-- Idempotente: solo agrega columnas si faltan.
-- ============================================================

SET SERVEROUTPUT ON;

DECLARE
    PROCEDURE add_col_si_falta(p_col VARCHAR2, p_ddl VARCHAR2) IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count
          FROM USER_TAB_COLUMNS
         WHERE TABLE_NAME = 'INDECI_EMPLEADO_PLANILLA'
           AND COLUMN_NAME = UPPER(p_col);
        IF v_count = 0 THEN
            EXECUTE IMMEDIATE p_ddl;
            DBMS_OUTPUT.PUT_LINE(p_col || ' -> columna agregada.');
        ELSE
            DBMS_OUTPUT.PUT_LINE(p_col || ' ya existe. Sin cambios.');
        END IF;
    END;
BEGIN
    add_col_si_falta('DOCUMENTO_ORIGEN_TIPO',
        'ALTER TABLE GESTIONRRHH.INDECI_EMPLEADO_PLANILLA ADD (DOCUMENTO_ORIGEN_TIPO VARCHAR2(40))');
    add_col_si_falta('DOCUMENTO_ORIGEN_NUMERO',
        'ALTER TABLE GESTIONRRHH.INDECI_EMPLEADO_PLANILLA ADD (DOCUMENTO_ORIGEN_NUMERO VARCHAR2(60))');
    add_col_si_falta('DOCUMENTO_ORIGEN_FECHA',
        'ALTER TABLE GESTIONRRHH.INDECI_EMPLEADO_PLANILLA ADD (DOCUMENTO_ORIGEN_FECHA DATE)');

    DBMS_OUTPUT.PUT_LINE('V012_08 finalizado correctamente.');
EXCEPTION
    WHEN OTHERS THEN
        DBMS_OUTPUT.PUT_LINE('ERROR en V012_08: ' || SQLERRM);
        RAISE;
END;
/
