-- ============================================================================
-- Config remunerativa / V010_92 — INDECI_EMPLEADO_PLANILLA: AIRHSP + MONTO_CONTRATO
--                         + seeds INCREMENTO_DS_* (negociación colectiva)
--
-- CONTEXTO (plan config-remunerativa-ds):
--   - CODIGO_AIRHSP: código MEF de 6 dígitos (conciliación / registro planilla).
--   - MONTO_CONTRATO: sueldo pactado en contrato (base antes de incrementos DS).
--     La columna puede existir desde V010_36; aquí se refuerza con CHECK positivo.
--   - SUELDO_BASICO sigue siendo remuneración mensual total (contrato + Σ DS).
--
-- INCREMENTO_DS_*: parámetros globales (REGIMEN_LABORAL_ID NULL) leídos por
-- IncrementosDsCalculoService vía ParametroRemunerativoService (REGLA-02).
-- Montos referencia 2026 según DS 311/313/265/279/327 (total acumulado 364.19).
--
-- IDEMPOTENTE: columnas, constraints, índice y seeds defensivos.
-- Ejecutar conectado como GESTIONRRHH / Oracle 19c+.
-- Rollback: V010_92__empleado_planilla_remuneracion_airhsp_rollback.sql
-- ============================================================================

SET SERVEROUTPUT ON;

-- ----------------------------------------------------------------------------
-- 1) Columnas INDECI_EMPLEADO_PLANILLA (defensivo)
-- ----------------------------------------------------------------------------
DECLARE
    v_null_airhsp   NUMBER;
    v_nullable      VARCHAR2(1);

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
           AND TABLE_NAME  = 'INDECI_EMPLEADO_PLANILLA'
           AND COLUMN_NAME = p_col_name;

        IF v_exists = 0 THEN
            EXECUTE IMMEDIATE
                'ALTER TABLE GESTIONRRHH.INDECI_EMPLEADO_PLANILLA ADD ('
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
           AND TABLE_NAME      = 'INDECI_EMPLEADO_PLANILLA'
           AND CONSTRAINT_NAME = p_constraint_name;

        IF v_exists = 0 THEN
            EXECUTE IMMEDIATE
                'ALTER TABLE GESTIONRRHH.INDECI_EMPLEADO_PLANILLA ADD CONSTRAINT '
                || p_constraint_name || ' CHECK (' || p_check_ddl || ')';
            DBMS_OUTPUT.PUT_LINE(p_constraint_name || ' -> agregado.');
        ELSE
            DBMS_OUTPUT.PUT_LINE(p_constraint_name || ' ya existe. Sin cambios.');
        END IF;
    END;

    PROCEDURE crear_indice_si_falta(
        p_index_name VARCHAR2,
        p_columns    VARCHAR2
    ) IS
        v_exists     NUMBER;
        v_tablespace VARCHAR2(128);
        v_ts_clause  VARCHAR2(160) := '';
    BEGIN
        SELECT COUNT(*)
          INTO v_exists
          FROM ALL_INDEXES
         WHERE OWNER      = 'GESTIONRRHH'
           AND INDEX_NAME = p_index_name;

        IF v_exists > 0 THEN
            DBMS_OUTPUT.PUT_LINE(p_index_name || ' ya existe. Sin cambios.');
            RETURN;
        END IF;

        -- El índice debe nacer en el MISMO tablespace de la tabla (existente),
        -- no en el tablespace por defecto del usuario (que puede no existir
        -- en esta instancia -> ORA-00959). Si es null (p.ej. tabla particionada)
        -- se omite la cláusula y se usa el default.
        BEGIN
            SELECT TABLESPACE_NAME
              INTO v_tablespace
              FROM ALL_TABLES
             WHERE OWNER      = 'GESTIONRRHH'
               AND TABLE_NAME = 'INDECI_EMPLEADO_PLANILLA';
            IF v_tablespace IS NOT NULL THEN
                v_ts_clause := ' TABLESPACE ' || v_tablespace;
            END IF;
        EXCEPTION
            WHEN NO_DATA_FOUND THEN
                v_ts_clause := '';
        END;

        BEGIN
            EXECUTE IMMEDIATE
                'CREATE INDEX GESTIONRRHH.' || p_index_name
                || ' ON GESTIONRRHH.INDECI_EMPLEADO_PLANILLA '
                || p_columns
                || v_ts_clause;
            DBMS_OUTPUT.PUT_LINE(
                p_index_name || ' -> creado'
                || CASE WHEN v_tablespace IS NOT NULL
                        THEN ' (TABLESPACE ' || v_tablespace || ').'
                        ELSE '.' END);
        EXCEPTION
            WHEN OTHERS THEN
                -- Índice no crítico (solo conciliación AIRHSP): no abortar la
                -- migración si falla su creación.
                DBMS_OUTPUT.PUT_LINE(
                    'AVISO: no se pudo crear ' || p_index_name
                    || ' (' || SQLERRM || '). Migración continúa.');
        END;
    END;
BEGIN
    add_column_if_missing(
        'CODIGO_AIRHSP',
        'CODIGO_AIRHSP VARCHAR2(10 CHAR)');

    add_column_if_missing(
        'MONTO_CONTRATO',
        'MONTO_CONTRATO NUMBER(12,2)');

    EXECUTE IMMEDIATE
        'COMMENT ON COLUMN GESTIONRRHH.INDECI_EMPLEADO_PLANILLA.CODIGO_AIRHSP IS '
        || '''Código MEF AIRHSP del registro (6 dígitos). Conciliación planilla / M13.''';

    EXECUTE IMMEDIATE
        'COMMENT ON COLUMN GESTIONRRHH.INDECI_EMPLEADO_PLANILLA.MONTO_CONTRATO IS '
        || '''Monto base mensual pactado en contrato (S/). Sin incrementos DS.''';

    -- Backfill legacy: placeholder temporal hasta que RR.HH. cargue códigos reales.
    SELECT COUNT(*)
      INTO v_null_airhsp
      FROM GESTIONRRHH.INDECI_EMPLEADO_PLANILLA
     WHERE CODIGO_AIRHSP IS NULL;

    IF v_null_airhsp > 0 THEN
        UPDATE GESTIONRRHH.INDECI_EMPLEADO_PLANILLA
           SET CODIGO_AIRHSP = '000000'
         WHERE CODIGO_AIRHSP IS NULL;
        DBMS_OUTPUT.PUT_LINE(
            'Backfill CODIGO_AIRHSP: ' || v_null_airhsp || ' fila(s) -> 000000 (placeholder).');
    ELSE
        DBMS_OUTPUT.PUT_LINE('Backfill CODIGO_AIRHSP: no hay NULL. Sin cambios.');
    END IF;

    BEGIN
        SELECT NULLABLE
          INTO v_nullable
          FROM ALL_TAB_COLUMNS
         WHERE OWNER       = 'GESTIONRRHH'
           AND TABLE_NAME  = 'INDECI_EMPLEADO_PLANILLA'
           AND COLUMN_NAME = 'CODIGO_AIRHSP';

        IF v_nullable = 'Y' THEN
            EXECUTE IMMEDIATE
                'ALTER TABLE GESTIONRRHH.INDECI_EMPLEADO_PLANILLA '
                || 'MODIFY (CODIGO_AIRHSP VARCHAR2(10 CHAR) NOT NULL)';
            DBMS_OUTPUT.PUT_LINE('CODIGO_AIRHSP -> NOT NULL aplicado.');
        ELSE
            DBMS_OUTPUT.PUT_LINE('CODIGO_AIRHSP ya era NOT NULL. Sin cambios.');
        END IF;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            DBMS_OUTPUT.PUT_LINE('Aviso: columna CODIGO_AIRHSP no encontrada para NOT NULL.');
    END;

    add_check_if_missing(
        'CHK_AIRHSP_FORMATO',
        'REGEXP_LIKE(CODIGO_AIRHSP, ''^[0-9]{6}$'')');

    add_check_if_missing(
        'CHK_MONTO_CONTRATO_POS',
        'MONTO_CONTRATO IS NULL OR MONTO_CONTRATO > 0');

    crear_indice_si_falta(
        'IDX_EMP_PLANILLA_AIRHSP',
        '(CODIGO_AIRHSP)');

    DBMS_OUTPUT.PUT_LINE('V010_92 (DDL planilla) finalizado.');
END;
/

-- ----------------------------------------------------------------------------
-- 2) Seeds INCREMENTO_DS_* — global, ANIO_FISCAL 2026, UNIDAD PEN
--    Vigencia FECHA_VIG_INI alineada al DS de origen; FECHA_VIG_FIN NULL.
-- ----------------------------------------------------------------------------
MERGE INTO GESTIONRRHH.INDECI_PARAMETRO_REMUNERATIVO d
USING (
    SELECT 'INCREMENTO_DS_311_2022' AS CODIGO_PARAMETRO, 2026 AS ANIO_FISCAL,
           64.190000 AS VALOR_NUMERICO, 'PEN' AS UNIDAD, DATE '2023-01-01' AS FECHA_VIG_INI
      FROM DUAL UNION ALL
    SELECT 'INCREMENTO_DS_313_2023', 2026, 50.000000, 'PEN', DATE '2024-01-01' FROM DUAL UNION ALL
    SELECT 'INCREMENTO_DS_265_2024', 2026, 50.000000, 'PEN', DATE '2024-01-01' FROM DUAL UNION ALL
    SELECT 'INCREMENTO_DS_279_2024', 2026, 100.000000, 'PEN', DATE '2024-07-01' FROM DUAL UNION ALL
    SELECT 'INCREMENTO_DS_327_2025', 2026, 100.000000, 'PEN', DATE '2025-01-01' FROM DUAL
) s
ON (
    d.CODIGO_PARAMETRO = s.CODIGO_PARAMETRO
    AND d.ANIO_FISCAL  = s.ANIO_FISCAL
    AND d.REGIMEN_LABORAL_ID IS NULL
    AND d.FECHA_VIG_INI  = s.FECHA_VIG_INI
)
WHEN NOT MATCHED THEN INSERT (
    CODIGO_PARAMETRO, ANIO_FISCAL, REGIMEN_LABORAL_ID,
    VALOR_NUMERICO, UNIDAD, FECHA_VIG_INI, FECHA_VIG_FIN, ACTIVO
) VALUES (
    s.CODIGO_PARAMETRO, s.ANIO_FISCAL, NULL,
    s.VALOR_NUMERICO, s.UNIDAD, s.FECHA_VIG_INI, NULL, 1
);

COMMIT;

-- Verificación rápida
SELECT CODIGO_PARAMETRO, ANIO_FISCAL, VALOR_NUMERICO, UNIDAD, FECHA_VIG_INI, ACTIVO
  FROM GESTIONRRHH.INDECI_PARAMETRO_REMUNERATIVO
 WHERE CODIGO_PARAMETRO LIKE 'INCREMENTO_DS_%'
 ORDER BY CODIGO_PARAMETRO;