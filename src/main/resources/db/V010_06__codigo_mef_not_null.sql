-- ============================================================================
-- Spec 010 / V010_06 — NOT NULL en columnas MEF de INDECI_CONCEPTO_PLANILLA
--
-- Cierra el ciclo iniciado por V010_02 (que dejó CODIGO_MEF y TIPO_CONCEPTO
-- como NULLABLE) tras completar el backfill de V010_05. Cumple LEY-01
-- (Ley 32448) a nivel de schema: la base de datos rechaza cualquier intento
-- de insertar un concepto sin CODIGO_MEF / TIPO_CONCEPTO.
--
-- DEFENSA EN PROFUNDIDAD:
--   - Verifica explícitamente que no haya filas con NULL antes de aplicar
--     el ALTER (aborta con mensaje claro si las hay).
--   - Idempotente: si la columna ya es NOT NULL, no falla — sólo informa.
--   - Solo metadata: no modifica una sola fila de datos.
--
-- Ejecutar en esquema GESTIONRRHH / Oracle 19c+.
-- ============================================================================

SET SERVEROUTPUT ON;

DECLARE
    v_nulls_codigo    NUMBER;
    v_nulls_tipo      NUMBER;
    v_nullable_codigo VARCHAR2(1);
    v_nullable_tipo   VARCHAR2(1);
BEGIN
    -- ------------------------------------------------------------------
    -- GUARD 1 — No debe haber NULL en CODIGO_MEF (LEY-01).
    -- ------------------------------------------------------------------
    SELECT COUNT(*)
      INTO v_nulls_codigo
      FROM GESTIONRRHH.INDECI_CONCEPTO_PLANILLA
     WHERE CODIGO_MEF IS NULL;

    IF v_nulls_codigo > 0 THEN
        RAISE_APPLICATION_ERROR(
            -20010,
            'V010_06 ABORTADO: ' || v_nulls_codigo
            || ' fila(s) en INDECI_CONCEPTO_PLANILLA tienen CODIGO_MEF NULL. '
            || 'Completar el backfill de V010_05 antes de reintentar.');
    END IF;

    -- ------------------------------------------------------------------
    -- GUARD 2 — No debe haber NULL en TIPO_CONCEPTO.
    -- ------------------------------------------------------------------
    SELECT COUNT(*)
      INTO v_nulls_tipo
      FROM GESTIONRRHH.INDECI_CONCEPTO_PLANILLA
     WHERE TIPO_CONCEPTO IS NULL;

    IF v_nulls_tipo > 0 THEN
        RAISE_APPLICATION_ERROR(
            -20011,
            'V010_06 ABORTADO: ' || v_nulls_tipo
            || ' fila(s) en INDECI_CONCEPTO_PLANILLA tienen TIPO_CONCEPTO NULL. '
            || 'Completar el backfill de V010_05 antes de reintentar.');
    END IF;

    DBMS_OUTPUT.PUT_LINE('Guards OK: 0 filas con NULL en CODIGO_MEF / TIPO_CONCEPTO.');

    -- ------------------------------------------------------------------
    -- APLICAR NOT NULL — CODIGO_MEF (idempotente).
    -- ------------------------------------------------------------------
    SELECT NULLABLE
      INTO v_nullable_codigo
      FROM ALL_TAB_COLUMNS
     WHERE OWNER       = 'GESTIONRRHH'
       AND TABLE_NAME  = 'INDECI_CONCEPTO_PLANILLA'
       AND COLUMN_NAME = 'CODIGO_MEF';

    IF v_nullable_codigo = 'Y' THEN
        EXECUTE IMMEDIATE
            'ALTER TABLE GESTIONRRHH.INDECI_CONCEPTO_PLANILLA '
            || 'MODIFY (CODIGO_MEF NOT NULL)';
        DBMS_OUTPUT.PUT_LINE('CODIGO_MEF -> NOT NULL aplicado.');
    ELSE
        DBMS_OUTPUT.PUT_LINE('CODIGO_MEF ya era NOT NULL. Sin cambios.');
    END IF;

    -- ------------------------------------------------------------------
    -- APLICAR NOT NULL — TIPO_CONCEPTO (idempotente).
    -- ------------------------------------------------------------------
    SELECT NULLABLE
      INTO v_nullable_tipo
      FROM ALL_TAB_COLUMNS
     WHERE OWNER       = 'GESTIONRRHH'
       AND TABLE_NAME  = 'INDECI_CONCEPTO_PLANILLA'
       AND COLUMN_NAME = 'TIPO_CONCEPTO';

    IF v_nullable_tipo = 'Y' THEN
        EXECUTE IMMEDIATE
            'ALTER TABLE GESTIONRRHH.INDECI_CONCEPTO_PLANILLA '
            || 'MODIFY (TIPO_CONCEPTO NOT NULL)';
        DBMS_OUTPUT.PUT_LINE('TIPO_CONCEPTO -> NOT NULL aplicado.');
    ELSE
        DBMS_OUTPUT.PUT_LINE('TIPO_CONCEPTO ya era NOT NULL. Sin cambios.');
    END IF;
END;
/
