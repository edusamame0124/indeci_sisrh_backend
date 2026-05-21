-- ============================================================================
-- Spec 010 / V010_14 — Validación neto 50% en INDECI_MOVIMIENTO_PLANILLA
--
-- Agrega a la CABECERA del movimiento (fila por empleado/período) las dos
-- columnas de la REGLA SERVIR-07 / SPEC §5.4:
--   - NETO_50PCT_MINIMO  → umbral = (remun − ir5ta − aporte_pension − judicial) × 0.5
--   - ESTADO_NETO        → 'BIEN' si neto >= umbral; 'NETO_NO_VA' en caso contrario
--
-- POR QUÉ EN LA CABECERA Y NO EN _DET:
--   La validación neto 50% es por empleado/período, no por concepto. Su sitio
--   natural es INDECI_MOVIMIENTO_PLANILLA (junto a TOTAL_INGRESOS / NETO_PAGAR).
--   V010_09 había agregado columnas equivalentes a INDECI_MOVIMIENTO_PLANILLA_DET
--   por un mismatch de nombres con el SPEC (TBL_PLANILLA_DETALLE del SPEC = la
--   fila del empleado = esta cabecera). Las de _DET quedan sin uso.
--
-- ESTADO_NETO es un eje INDEPENDIENTE del campo ESTADO (flujo GENERADO/REVISAR):
-- uno es validación regulatoria, el otro es ciclo de vida del movimiento.
--
-- DEFENSA EN PROFUNDIDAD: idempotente (add_column_if_missing / add_check_if_missing).
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
           AND TABLE_NAME  = 'INDECI_MOVIMIENTO_PLANILLA'
           AND COLUMN_NAME = p_col_name;

        IF v_exists = 0 THEN
            EXECUTE IMMEDIATE
                'ALTER TABLE GESTIONRRHH.INDECI_MOVIMIENTO_PLANILLA ADD ('
                || p_col_ddl || ')';
            DBMS_OUTPUT.PUT_LINE(p_col_name || ' -> agregada.');
        ELSE
            DBMS_OUTPUT.PUT_LINE(p_col_name || ' ya existe. Sin cambios.');
        END IF;
    END;

    PROCEDURE add_check_if_missing(
        p_constraint_name VARCHAR2,
        p_check_ddl       VARCHAR2
    ) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*)
          INTO v_exists
          FROM ALL_CONSTRAINTS
         WHERE OWNER           = 'GESTIONRRHH'
           AND TABLE_NAME      = 'INDECI_MOVIMIENTO_PLANILLA'
           AND CONSTRAINT_NAME = p_constraint_name;

        IF v_exists = 0 THEN
            EXECUTE IMMEDIATE
                'ALTER TABLE GESTIONRRHH.INDECI_MOVIMIENTO_PLANILLA ADD CONSTRAINT '
                || p_constraint_name || ' CHECK (' || p_check_ddl || ')';
            DBMS_OUTPUT.PUT_LINE(p_constraint_name || ' -> agregado.');
        ELSE
            DBMS_OUTPUT.PUT_LINE(p_constraint_name || ' ya existe. Sin cambios.');
        END IF;
    END;
BEGIN
    add_column_if_missing(
        'NETO_50PCT_MINIMO',
        'NETO_50PCT_MINIMO NUMBER(12,2)');
    add_column_if_missing(
        'ESTADO_NETO',
        'ESTADO_NETO VARCHAR2(10 CHAR)');
    add_check_if_missing(
        'INDECI_MOV_ESTADO_NETO_CK',
        'ESTADO_NETO IS NULL OR ESTADO_NETO IN (''BIEN'', ''NETO_NO_VA'')');

    EXECUTE IMMEDIATE
        'COMMENT ON COLUMN GESTIONRRHH.INDECI_MOVIMIENTO_PLANILLA.NETO_50PCT_MINIMO IS ' ||
        '''SPEC §5.4 (REGLA SERVIR-07): umbral = (TOTAL_INGRESOS - IR5ta - aporte_pensionario - judicial) * 0.5.''';
    EXECUTE IMMEDIATE
        'COMMENT ON COLUMN GESTIONRRHH.INDECI_MOVIMIENTO_PLANILLA.ESTADO_NETO IS ' ||
        '''SPEC §5.4: BIEN si NETO_PAGAR >= NETO_50PCT_MINIMO; NETO_NO_VA si no. Eje independiente del campo ESTADO (flujo del movimiento).''';

    DBMS_OUTPUT.PUT_LINE('V010_14 finalizado.');
END;
/
