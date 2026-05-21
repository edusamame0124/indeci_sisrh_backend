-- ============================================================================
-- Spec 010 / V010_09 — Columnas v2 en INDECI_MOVIMIENTO_PLANILLA_DET (SPEC §10.2)
--
-- Habilita en BD las fórmulas operativas del Excel:
--   - §5.4 (fórmulas Excel)  : pago diferencial, reintegro, validación neto 50%
--   - §5.5 (ESSALUD + EPS)   : split 6.75% empleador + 2.25% copago trabajador
--   - §5.7 (IR 5ta dos bases): BW (remuneración) + BX (aguinaldo) = BY (total)
--
-- DECISIONES DE DISEÑO:
--   - Las 8 columnas numéricas reciben DEFAULT 0 → Oracle propaga el default
--     a todas las filas existentes (filas legacy quedan en cero coherente).
--   - NETO_50PCT_MINIMO y ESTADO_NETO sin DEFAULT: nacen NULL hasta que el
--     motor refactor de §5.4 las calcule. Eso permite distinguir "no calculado"
--     vs "calculado y aprobado".
--   - CHECK en ESTADO_NETO: solo 'BIEN' o 'NETO_NO_VA' (literales del Excel).
--   - Sin índices: las columnas son outputs del motor, no llaves de búsqueda.
--   - Sin tocar columnas existentes (MONTO, CANTIDAD, OBSERVACION) — los
--     conceptos manuales del motor v1 siguen escribiendo en esas mismas filas.
--
-- DEFENSA EN PROFUNDIDAD:
--   - Idempotente: add_column_if_missing / add_check_if_missing.
--   - Solo metadata: ninguna fila se modifica (las legacy reciben default
--     automáticamente por el motor de Oracle al re-leer).
--   - Re-ejecución segura.
--
-- Ejecutar en esquema GESTIONRRHH / Oracle 19c+.
-- ============================================================================

SET SERVEROUTPUT ON;

DECLARE
    v_count NUMBER;

    PROCEDURE add_column_if_missing(
        p_col_name  VARCHAR2,
        p_col_ddl   VARCHAR2
    ) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*)
          INTO v_exists
          FROM ALL_TAB_COLUMNS
         WHERE OWNER       = 'GESTIONRRHH'
           AND TABLE_NAME  = 'INDECI_MOVIMIENTO_PLANILLA_DET'
           AND COLUMN_NAME = p_col_name;

        IF v_exists = 0 THEN
            EXECUTE IMMEDIATE
                'ALTER TABLE GESTIONRRHH.INDECI_MOVIMIENTO_PLANILLA_DET ADD ('
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
           AND TABLE_NAME      = 'INDECI_MOVIMIENTO_PLANILLA_DET'
           AND CONSTRAINT_NAME = p_constraint_name;

        IF v_exists = 0 THEN
            EXECUTE IMMEDIATE
                'ALTER TABLE GESTIONRRHH.INDECI_MOVIMIENTO_PLANILLA_DET ADD CONSTRAINT '
                || p_constraint_name || ' CHECK (' || p_check_ddl || ')';
            DBMS_OUTPUT.PUT_LINE(p_constraint_name || ' -> agregado.');
        ELSE
            DBMS_OUTPUT.PUT_LINE(p_constraint_name || ' ya existe. Sin cambios.');
        END IF;
    END;
BEGIN
    -- ==================================================================
    -- §5.4 — Fórmulas Excel: pago diferencial + reintegro
    -- ==================================================================
    add_column_if_missing(
        'PAGO_DIFERENCIAL',
        'PAGO_DIFERENCIAL NUMBER(12,2) DEFAULT 0');
    add_column_if_missing(
        'DIAS_REINTEGRO',
        'DIAS_REINTEGRO NUMBER(3) DEFAULT 0');
    add_column_if_missing(
        'MONTO_REINTEGRO',
        'MONTO_REINTEGRO NUMBER(12,2) DEFAULT 0');

    -- ==================================================================
    -- §5.7 — IR 5ta categoría: BW + BX = BY
    -- ==================================================================
    add_column_if_missing(
        'IR_5TA_REMUNERACION',
        'IR_5TA_REMUNERACION NUMBER(12,2) DEFAULT 0');
    add_column_if_missing(
        'IR_5TA_AGUINALDO',
        'IR_5TA_AGUINALDO NUMBER(12,2) DEFAULT 0');
    add_column_if_missing(
        'IR_5TA_TOTAL',
        'IR_5TA_TOTAL NUMBER(12,2) DEFAULT 0');

    -- ==================================================================
    -- §5.5 — ESSALUD con EPS: split 6.75% empleador + 2.25% copago
    -- ==================================================================
    add_column_if_missing(
        'ESSALUD_6_75',
        'ESSALUD_6_75 NUMBER(12,2) DEFAULT 0');
    add_column_if_missing(
        'COPAGO_EPS',
        'COPAGO_EPS NUMBER(12,2) DEFAULT 0');

    -- ==================================================================
    -- §5.4 — Validación neto 50% (REGLA SERVIR-07)
    -- ==================================================================
    add_column_if_missing(
        'NETO_50PCT_MINIMO',
        'NETO_50PCT_MINIMO NUMBER(12,2)');
    add_column_if_missing(
        'ESTADO_NETO',
        'ESTADO_NETO VARCHAR2(10 CHAR)');
    add_check_if_missing(
        'INDECI_MOV_DET_ESTADO_NETO_CK',
        'ESTADO_NETO IS NULL OR ESTADO_NETO IN (''BIEN'', ''NETO_NO_VA'')');

    -- ==================================================================
    -- COMMENTS — referencian la sección exacta del SPEC y, donde aplica,
    -- el código SISPER del Excel.
    -- ==================================================================
    EXECUTE IMMEDIATE
        'COMMENT ON COLUMN GESTIONRRHH.INDECI_MOVIMIENTO_PLANILLA_DET.PAGO_DIFERENCIAL IS ' ||
        '''SPEC §5.4 (TOTAL REMUNERACIÓN): diferencial pagado adicional a la remuneración por días + reintegro. SISPER-059/060.''';

    EXECUTE IMMEDIATE
        'COMMENT ON COLUMN GESTIONRRHH.INDECI_MOVIMIENTO_PLANILLA_DET.DIAS_REINTEGRO IS ' ||
        '''SPEC §5.4 (REINTEGRO): días reintegrados al trabajador (base de MONTO_REINTEGRO).''';

    EXECUTE IMMEDIATE
        'COMMENT ON COLUMN GESTIONRRHH.INDECI_MOVIMIENTO_PLANILLA_DET.MONTO_REINTEGRO IS ' ||
        '''SPEC §5.4 (REINTEGRO): =ROUND((remun/30) * dias_reintegro, 2). SISPER-041.''';

    EXECUTE IMMEDIATE
        'COMMENT ON COLUMN GESTIONRRHH.INDECI_MOVIMIENTO_PLANILLA_DET.IR_5TA_REMUNERACION IS ' ||
        '''SPEC §5.7 — BW: IR 5ta sobre remuneración mensual (proyección anual). Aplica solo 728 y SERVIR.''';

    EXECUTE IMMEDIATE
        'COMMENT ON COLUMN GESTIONRRHH.INDECI_MOVIMIENTO_PLANILLA_DET.IR_5TA_AGUINALDO IS ' ||
        '''SPEC §5.7 — BX: IR 5ta sobre aguinaldo (solo julio/diciembre). 0 en los otros meses.''';

    EXECUTE IMMEDIATE
        'COMMENT ON COLUMN GESTIONRRHH.INDECI_MOVIMIENTO_PLANILLA_DET.IR_5TA_TOTAL IS ' ||
        '''SPEC §5.7 — BY = BW + BX: total IR 5ta del período. SISPER-820.''';

    EXECUTE IMMEDIATE
        'COMMENT ON COLUMN GESTIONRRHH.INDECI_MOVIMIENTO_PLANILLA_DET.ESSALUD_6_75 IS ' ||
        '''SPEC §5.5 (ESSALUD con EPS): aporte empleador 6.75% cuando empleado tiene EPS (HAS_EPS=S). SISPER-907.''';

    EXECUTE IMMEDIATE
        'COMMENT ON COLUMN GESTIONRRHH.INDECI_MOVIMIENTO_PLANILLA_DET.COPAGO_EPS IS ' ||
        '''SPEC §5.5 (ESSALUD con EPS): copago trabajador 2.25% cuando empleado tiene EPS. SISPER-725 (descuento).''';

    EXECUTE IMMEDIATE
        'COMMENT ON COLUMN GESTIONRRHH.INDECI_MOVIMIENTO_PLANILLA_DET.NETO_50PCT_MINIMO IS ' ||
        '''SPEC §5.4 (VALIDACIÓN NETO 50%): umbral mínimo = (remun - ir5ta - afp_onp - judicial) * 0.5. Base para comparar contra el neto y decidir ESTADO_NETO.''';

    EXECUTE IMMEDIATE
        'COMMENT ON COLUMN GESTIONRRHH.INDECI_MOVIMIENTO_PLANILLA_DET.ESTADO_NETO IS ' ||
        '''SPEC §5.4 (REGLA SERVIR-07): BIEN si neto >= NETO_50PCT_MINIMO; NETO_NO_VA si no. NULL hasta que el motor lo calcule.''';

    DBMS_OUTPUT.PUT_LINE('V010_09 finalizado.');

    -- Reporte final
    SELECT COUNT(*) INTO v_count
      FROM ALL_TAB_COLUMNS
     WHERE OWNER       = 'GESTIONRRHH'
       AND TABLE_NAME  = 'INDECI_MOVIMIENTO_PLANILLA_DET'
       AND COLUMN_NAME IN (
           'PAGO_DIFERENCIAL', 'DIAS_REINTEGRO', 'MONTO_REINTEGRO',
           'IR_5TA_REMUNERACION', 'IR_5TA_AGUINALDO', 'IR_5TA_TOTAL',
           'ESSALUD_6_75', 'COPAGO_EPS',
           'NETO_50PCT_MINIMO', 'ESTADO_NETO');
    DBMS_OUTPUT.PUT_LINE('Columnas presentes ahora en INDECI_MOVIMIENTO_PLANILLA_DET: '
        || v_count || ' / 10.');
END;
/
