-- ============================================================================
-- SPEC_CONCEPTOS_PLANILLA §14 / P4 / V010_101 — Modo de cálculo del concepto
--
-- OBJETIVO: persistir el campo "Modo de cálculo" que el wizard ya captura pero
-- que hoy NO se guardaba. Es METADATA / intención del operador: documenta cómo
-- se origina el monto del concepto. El MOTOR NO se ramifica por este campo
-- (no consume MODO_CALCULO); sigue valorizando como hoy (P4 — aditivo).
--
-- CAMBIOS:
--   INDECI_CONCEPTO_PLANILLA — ADD MODO_CALCULO VARCHAR2(20 CHAR)
--     DEFAULT 'RESULTADO_MOTOR' NOT NULL + CHECK con el dominio cerrado del
--     front (ConceptoModoCalculo).
--   Backfill MODO_CALCULO = 'RESULTADO_MOTOR' donde quedó NULL (defensivo).
--
-- DECISIONES DE DISEÑO:
--   - Naming INDECI_*; idempotente; tablespace-safe (patrón V010_97).
--   - Valores EXACTOS del front: MONTO_FIJO | MONTO_INDIVIDUAL | PORCENTAJE |
--     RESULTADO_MOTOR | IMPORTACION.
--   - SQL DINÁMICO para el UPDATE: la columna MODO_CALCULO se crea en runtime
--     (EXECUTE IMMEDIATE dentro de add_column_if_missing); un UPDATE estático
--     referenciándola no compilaría el bloque (ORA-00904 en parseo) — lección
--     V010_97.
--
-- Ejecutar en esquema GESTIONRRHH / Oracle 19c+. (NO ejecutar aquí.)
-- ============================================================================

SET SERVEROUTPUT ON;

DECLARE
    v_count NUMBER;

    PROCEDURE add_column_if_missing(
        p_table_name  VARCHAR2,
        p_column_name VARCHAR2,
        p_alter_ddl   VARCHAR2
    ) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_exists
          FROM ALL_TAB_COLUMNS
         WHERE OWNER = 'GESTIONRRHH'
           AND TABLE_NAME = p_table_name
           AND COLUMN_NAME = p_column_name;
        IF v_exists = 0 THEN
            EXECUTE IMMEDIATE p_alter_ddl;
            DBMS_OUTPUT.PUT_LINE(p_table_name || '.' || p_column_name || ' -> añadida.');
        ELSE
            DBMS_OUTPUT.PUT_LINE(p_table_name || '.' || p_column_name
                || ' ya existe. Sin cambios.');
        END IF;
    END;

    PROCEDURE add_constraint_if_missing(
        p_constraint_name VARCHAR2,
        p_alter_ddl       VARCHAR2
    ) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_exists
          FROM ALL_CONSTRAINTS
         WHERE OWNER = 'GESTIONRRHH' AND CONSTRAINT_NAME = p_constraint_name;
        IF v_exists = 0 THEN
            EXECUTE IMMEDIATE p_alter_ddl;
            DBMS_OUTPUT.PUT_LINE(p_constraint_name || ' -> creada.');
        ELSE
            DBMS_OUTPUT.PUT_LINE(p_constraint_name || ' ya existe. Sin cambios.');
        END IF;
    END;
BEGIN
    -- ==================================================================
    -- 1) MODO_CALCULO — columna (DEFAULT 'RESULTADO_MOTOR' NOT NULL)
    --    El DEFAULT cubre las filas existentes al añadirla (Oracle aplica el
    --    DEFAULT a las filas previas para una columna NOT NULL con DEFAULT).
    -- ==================================================================
    add_column_if_missing(
        'INDECI_CONCEPTO_PLANILLA', 'MODO_CALCULO',
        'ALTER TABLE GESTIONRRHH.INDECI_CONCEPTO_PLANILLA '
        || 'ADD (MODO_CALCULO VARCHAR2(20 CHAR) DEFAULT ''RESULTADO_MOTOR'' NOT NULL)'
    );

    -- ==================================================================
    -- 2) CHECK — dominio cerrado (alineado con el type ConceptoModoCalculo)
    -- ==================================================================
    add_constraint_if_missing(
        'INDECI_CONCEPTO_MODO_CALC_CK',
        'ALTER TABLE GESTIONRRHH.INDECI_CONCEPTO_PLANILLA '
        || 'ADD CONSTRAINT INDECI_CONCEPTO_MODO_CALC_CK '
        || 'CHECK (MODO_CALCULO IN ('
        || '''MONTO_FIJO'', ''MONTO_INDIVIDUAL'', ''PORCENTAJE'', '
        || '''RESULTADO_MOTOR'', ''IMPORTACION''))'
    );

    -- ==================================================================
    -- 3) BACKFILL defensivo (misma migración).
    --    El DEFAULT NOT NULL ya impide NULLs en filas nuevas/previas; el UPDATE
    --    es una red de seguridad por si la columna preexistía como NULLABLE.
    --    SQL DINÁMICO obligatorio: la columna puede crearse en este mismo bloque.
    -- ==================================================================
    EXECUTE IMMEDIATE
        'UPDATE GESTIONRRHH.INDECI_CONCEPTO_PLANILLA '
        || 'SET MODO_CALCULO = ''RESULTADO_MOTOR'' '
        || 'WHERE MODO_CALCULO IS NULL';
    DBMS_OUTPUT.PUT_LINE('Backfill MODO_CALCULO: ' || SQL%ROWCOUNT || ' fila(s) actualizada(s).');

    -- ==================================================================
    -- COMMENT
    -- ==================================================================
    EXECUTE IMMEDIATE 'COMMENT ON COLUMN GESTIONRRHH.INDECI_CONCEPTO_PLANILLA.MODO_CALCULO IS '
        || '''SPEC_CONCEPTOS_PLANILLA P4 — Modo de cálculo (metadata/intencion): '
        || 'MONTO_FIJO | MONTO_INDIVIDUAL | PORCENTAJE | RESULTADO_MOTOR | IMPORTACION. '
        || 'El motor NO se ramifica por este campo; sigue valorizando como hoy.''';

    DBMS_OUTPUT.PUT_LINE('V010_101 finalizado.');

    SELECT COUNT(*) INTO v_count
      FROM ALL_TAB_COLUMNS
     WHERE OWNER = 'GESTIONRRHH'
       AND TABLE_NAME = 'INDECI_CONCEPTO_PLANILLA'
       AND COLUMN_NAME = 'MODO_CALCULO';
    DBMS_OUTPUT.PUT_LINE('Columna MODO_CALCULO en INDECI_CONCEPTO_PLANILLA: ' || v_count || ' / 1.');
END;
/
