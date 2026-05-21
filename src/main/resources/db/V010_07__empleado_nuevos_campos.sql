-- ============================================================================
-- Spec 010 / V010_07 — Campos permanentes nuevos en INDECI_EMPLEADO (SPEC §10.2)
--
-- Atributos propios del trabajador (NO de su configuración por período):
--   - HAS_EPS         → habilita split ESSALUD 6.75% / 2.25% (SPEC §5.5)
--   - CODIGO_SISPER   → código del sistema SISPER del empleado
--   - DNI_REEMPLAZADO → DNI del titular cuando el empleado está en encargatura
--                       (alimenta TBL_EMPLEADO_ENCARGATURA en V010_10)
--   - REGISTRO_AIRHSP → código MEF del registro del empleado en AIRHSP
--                       (base para conciliación M13 / TBL_CONCILIACION_AIRHSP)
--
-- NOTA — Campos que NO se mueven (decisión 2026-05-15):
--   META_PRESUPUESTAL, CENTRO_COSTO, FUENTE_FINANCIAMIENTO,
--   AIRHSP_VIGENCIA_ID, TIENE_AIRHSP, CODIGO_AIRHSP, META
--   quedan en INDECI_EMPLEADO_PLANILLA (configuración por período).
--
-- NOTA — CCI ya existe en INDECI_EMPLEADO_BANCO (mapeado por la entidad
-- EmpleadoBanco.java). No requiere alteración aquí.
--
-- DEFENSA EN PROFUNDIDAD:
--   - Idempotente: cada ALTER se ejecuta sólo si la columna no existe en
--     ALL_TAB_COLUMNS para OWNER='GESTIONRRHH'.
--   - Solo metadata: ninguna fila de datos se modifica.
--   - Re-ejecución segura: si todo está aplicado, imprime "Sin cambios".
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
           AND TABLE_NAME  = 'INDECI_EMPLEADO'
           AND COLUMN_NAME = p_col_name;

        IF v_exists = 0 THEN
            EXECUTE IMMEDIATE
                'ALTER TABLE GESTIONRRHH.INDECI_EMPLEADO ADD (' || p_col_ddl || ')';
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
           AND TABLE_NAME      = 'INDECI_EMPLEADO'
           AND CONSTRAINT_NAME = p_constraint_name;

        IF v_exists = 0 THEN
            EXECUTE IMMEDIATE
                'ALTER TABLE GESTIONRRHH.INDECI_EMPLEADO ADD CONSTRAINT '
                || p_constraint_name || ' CHECK (' || p_check_ddl || ')';
            DBMS_OUTPUT.PUT_LINE(p_constraint_name || ' -> agregado.');
        ELSE
            DBMS_OUTPUT.PUT_LINE(p_constraint_name || ' ya existe. Sin cambios.');
        END IF;
    END;
BEGIN
    -- ------------------------------------------------------------------
    -- 1. HAS_EPS — split ESSALUD (SPEC §5.5)
    -- ------------------------------------------------------------------
    add_column_if_missing(
        'HAS_EPS',
        'HAS_EPS VARCHAR2(1 CHAR) DEFAULT ''N'' NOT NULL');
    add_check_if_missing(
        'INDECI_EMPLEADO_HAS_EPS_CK',
        'HAS_EPS IN (''S'', ''N'')');

    -- ------------------------------------------------------------------
    -- 2. CODIGO_SISPER — identificador del empleado en SISPER
    -- ------------------------------------------------------------------
    add_column_if_missing(
        'CODIGO_SISPER',
        'CODIGO_SISPER VARCHAR2(10 CHAR)');

    -- ------------------------------------------------------------------
    -- 3. DNI_REEMPLAZADO — encargatura (alimenta TBL_EMPLEADO_ENCARGATURA)
    -- ------------------------------------------------------------------
    add_column_if_missing(
        'DNI_REEMPLAZADO',
        'DNI_REEMPLAZADO VARCHAR2(8 CHAR)');

    -- ------------------------------------------------------------------
    -- 4. REGISTRO_AIRHSP — código MEF del empleado en AIRHSP
    -- ------------------------------------------------------------------
    add_column_if_missing(
        'REGISTRO_AIRHSP',
        'REGISTRO_AIRHSP VARCHAR2(10 CHAR)');

    -- ------------------------------------------------------------------
    -- COMMENTS — ejecutables de forma idempotente (Oracle los sobreescribe)
    -- ------------------------------------------------------------------
    EXECUTE IMMEDIATE
        'COMMENT ON COLUMN GESTIONRRHH.INDECI_EMPLEADO.HAS_EPS IS ' ||
        '''SPEC §5.5: S=trabajador tiene EPS (split ESSALUD 6.75% empleador / 2.25% trabajador). N=ESSALUD 9% completo al empleador.''';

    EXECUTE IMMEDIATE
        'COMMENT ON COLUMN GESTIONRRHH.INDECI_EMPLEADO.CODIGO_SISPER IS ' ||
        '''Código del empleado en el sistema SISPER (ej: ''''SISPER-001234'''').''';

    EXECUTE IMMEDIATE
        'COMMENT ON COLUMN GESTIONRRHH.INDECI_EMPLEADO.DNI_REEMPLAZADO IS ' ||
        '''DNI del trabajador titular cuando el empleado está en encargatura (Excel columnas E/F). Alimenta TBL_EMPLEADO_ENCARGATURA.''';

    EXECUTE IMMEDIATE
        'COMMENT ON COLUMN GESTIONRRHH.INDECI_EMPLEADO.REGISTRO_AIRHSP IS ' ||
        '''Código MEF del registro del empleado en AIRHSP (ej: ''''000139''''). Base para conciliación M13 / TBL_CONCILIACION_AIRHSP.''';

    DBMS_OUTPUT.PUT_LINE('V010_07 finalizado.');

    -- Reporte final
    SELECT COUNT(*) INTO v_count
      FROM ALL_TAB_COLUMNS
     WHERE OWNER       = 'GESTIONRRHH'
       AND TABLE_NAME  = 'INDECI_EMPLEADO'
       AND COLUMN_NAME IN ('HAS_EPS', 'CODIGO_SISPER', 'DNI_REEMPLAZADO', 'REGISTRO_AIRHSP');
    DBMS_OUTPUT.PUT_LINE('Columnas presentes ahora en INDECI_EMPLEADO: ' || v_count || ' / 4.');
END;
/
