-- ============================================================================
-- SPEC_VACACIONES F8 / D7 — Backfill de saldos vacacionales desde el Excel del
-- especialista (MATRIZ_VACACIONES, hoja DATOS).
--
-- Añade a INDECI_VACACION_SALDO la trazabilidad del origen del dato:
--   ORIGEN      — 'MIGRACION_INICIAL_2026' (línea base congelada) | 'MOTOR' (automatizado)
--   FECHA_CORTE — fecha de corte del cálculo del Excel (col K), para auditoría
--
-- Idempotente (add_column_if_missing). Ejecutar en GESTIONRRHH / Oracle 19c+.
-- ============================================================================

SET SERVEROUTPUT ON;

DECLARE
    PROCEDURE add_column_if_missing(
        p_table  VARCHAR2,
        p_column VARCHAR2,
        p_ddl    VARCHAR2
    ) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_exists
          FROM ALL_TAB_COLUMNS
         WHERE OWNER = 'GESTIONRRHH'
           AND TABLE_NAME = p_table
           AND COLUMN_NAME = p_column;
        IF v_exists = 0 THEN
            EXECUTE IMMEDIATE p_ddl;
            DBMS_OUTPUT.PUT_LINE(p_table || '.' || p_column || ' -> agregada.');
        ELSE
            DBMS_OUTPUT.PUT_LINE(p_table || '.' || p_column || ' ya existe. Sin cambios.');
        END IF;
    END;
BEGIN
    add_column_if_missing(
        'INDECI_VACACION_SALDO', 'ORIGEN',
        'ALTER TABLE GESTIONRRHH.INDECI_VACACION_SALDO ADD (ORIGEN VARCHAR2(30 CHAR) DEFAULT ''MOTOR'')');

    add_column_if_missing(
        'INDECI_VACACION_SALDO', 'FECHA_CORTE',
        'ALTER TABLE GESTIONRRHH.INDECI_VACACION_SALDO ADD (FECHA_CORTE DATE)');

    EXECUTE IMMEDIATE 'COMMENT ON COLUMN GESTIONRRHH.INDECI_VACACION_SALDO.ORIGEN IS '
        || '''MIGRACION_INICIAL_2026 = línea base importada del Excel (congelada) | MOTOR = generado por el sistema''';
    EXECUTE IMMEDIATE 'COMMENT ON COLUMN GESTIONRRHH.INDECI_VACACION_SALDO.FECHA_CORTE IS '
        || '''Fecha de corte del cálculo (col K del Excel) — trazabilidad de la migración''';

    DBMS_OUTPUT.PUT_LINE('V012_20 finalizado.');
END;
/
