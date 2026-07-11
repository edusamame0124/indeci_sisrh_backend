-- ============================================================================
-- SPEC_VACACIONES F9.1 — Jornada (días/semana) para el récord vacacional (210/260).
--
-- Patrón herencia/override (decisión de arquitectura RR.HH.):
--   INDECI_JORNADA_REGIMEN.DIAS_SEMANA          — base por régimen. DEFAULT 5 (umbral 210),
--                                                  que es el estándar administrativo del Estado.
--   INDECI_EMPLEADO_PLANILLA.DIAS_SEMANA_OPERATIVO — override por empleado (NULLABLE). Solo se
--                                                  marca para operativos (COEN/DDI/choferes = 6 → 260).
--
-- Lectura del motor: empleado.DIAS_SEMANA_OPERATIVO ?? jornadaRegimen.DIAS_SEMANA ?? 5.
--
-- Solo DDL (ALTER ADD), idempotente. Oracle 19c+ / GESTIONRRHH.
-- ============================================================================

SET SERVEROUTPUT ON;

DECLARE
    PROCEDURE add_column_if_missing(
        p_table VARCHAR2, p_column VARCHAR2, p_ddl VARCHAR2
    ) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_exists FROM ALL_TAB_COLUMNS
         WHERE OWNER='GESTIONRRHH' AND TABLE_NAME=p_table AND COLUMN_NAME=p_column;
        IF v_exists = 0 THEN
            EXECUTE IMMEDIATE p_ddl;
            DBMS_OUTPUT.PUT_LINE(p_table||'.'||p_column||' -> agregada.');
        ELSE
            DBMS_OUTPUT.PUT_LINE(p_table||'.'||p_column||' ya existe.');
        END IF;
    END;
BEGIN
    add_column_if_missing('INDECI_JORNADA_REGIMEN', 'DIAS_SEMANA',
        'ALTER TABLE GESTIONRRHH.INDECI_JORNADA_REGIMEN ADD (DIAS_SEMANA NUMBER(1) DEFAULT 5)');

    add_column_if_missing('INDECI_EMPLEADO_PLANILLA', 'DIAS_SEMANA_OPERATIVO',
        'ALTER TABLE GESTIONRRHH.INDECI_EMPLEADO_PLANILLA ADD (DIAS_SEMANA_OPERATIVO NUMBER(1))');

    EXECUTE IMMEDIATE 'COMMENT ON COLUMN GESTIONRRHH.INDECI_JORNADA_REGIMEN.DIAS_SEMANA IS '
        || '''Días/semana del régimen para récord vacacional (5=210 / 6=260). Default 5.''';
    EXECUTE IMMEDIATE 'COMMENT ON COLUMN GESTIONRRHH.INDECI_EMPLEADO_PLANILLA.DIAS_SEMANA_OPERATIVO IS '
        || '''Override por empleado (NULL=hereda del régimen). 6=operativo COEN/DDI (umbral 260).''';

    DBMS_OUTPUT.PUT_LINE('V012_26 finalizado.');
END;
/
