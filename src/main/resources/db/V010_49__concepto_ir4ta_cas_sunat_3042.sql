-- ============================================================================
-- FASE 1 / V010_49 — Concepto IR4TA_CAS (retención tributaria SUNAT 3042)
--
-- CRITERIO NORMATIVO (Directiva 0001-2021-EF/53.01 + TUO LIR / SUNAT):
--   - El Catálogo Único MEF/AIRHSP regula CONCEPTOS DE INGRESOS, no retenciones
--     tributarias. La retención de 4ta categoría NO tiene código MEF/AIRHSP.
--   - Código tributario SUNAT correcto: 3042 (Renta 4ta Categoría - Retenciones).
--     NO 3041 (Cuenta Propia). El 3042 va en CODIGO_TRIBUTO_SUNAT, NUNCA en
--     CODIGO_MEF.
--   - Como CODIGO_MEF es NOT NULL (V010_06), se usa el valor técnico 'NO_APLICA'
--     documentado. 'NO_APLICA' NO es un código MEF oficial.
--   - El motor resuelve este concepto por CODIGO = 'IR4TA_CAS' (no por MEF) y
--     NUNCA bloquea la planilla por ausencia de CODIGO_MEF en esta retención.
--
-- 1) Agrega columna CODIGO_TRIBUTO_SUNAT (nullable, aditiva) — Opción A.
-- 2) Siembra (MERGE idempotente por CODIGO) el concepto IR4TA_CAS con 3042.
--
-- DEFENSA EN PROFUNDIDAD: idempotente. NO crea tablas ni índices (ALTER ADD
-- columna + MERGE), por lo que NO requiere cláusula TABLESPACE (no aplica
-- ORA-00959). Identificadores sin esquema + USER_* para correr en el contexto
-- del esquema GESTIONRRHH, igual que V010_48. Ejecutar en GESTIONRRHH / Oracle 19c+.
-- ============================================================================

SET SERVEROUTPUT ON;

-- ----------------------------------------------------------------------------
-- 1) Columna CODIGO_TRIBUTO_SUNAT (idempotente)
-- ----------------------------------------------------------------------------
DECLARE
    v_exists NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_exists
      FROM USER_TAB_COLUMNS
     WHERE TABLE_NAME = 'INDECI_CONCEPTO_PLANILLA'
       AND COLUMN_NAME = 'CODIGO_TRIBUTO_SUNAT';

    IF v_exists = 0 THEN
        EXECUTE IMMEDIATE
            'ALTER TABLE INDECI_CONCEPTO_PLANILLA '
            || 'ADD (CODIGO_TRIBUTO_SUNAT VARCHAR2(10 CHAR))';
        EXECUTE IMMEDIATE
            'COMMENT ON COLUMN INDECI_CONCEPTO_PLANILLA.CODIGO_TRIBUTO_SUNAT IS '
            || '''Código de tributo SUNAT (ej. 3042 = Retención Renta 4ta Categoría). '
            || 'Independiente de CODIGO_MEF (catálogo de ingresos MEF/AIRHSP).''';
        DBMS_OUTPUT.PUT_LINE('CODIGO_TRIBUTO_SUNAT -> agregada.');
    ELSE
        DBMS_OUTPUT.PUT_LINE('CODIGO_TRIBUTO_SUNAT ya existe. Sin cambios.');
    END IF;
END;
/

-- ----------------------------------------------------------------------------
-- 2) Concepto IR4TA_CAS (MERGE idempotente por CODIGO)
-- ----------------------------------------------------------------------------
MERGE INTO INDECI_CONCEPTO_PLANILLA d
USING (
    SELECT
        'IR4TA_CAS'                         AS CODIGO,
        'NO_APLICA'                         AS CODIGO_MEF,        -- valor técnico (no es MEF oficial)
        'Retención IR 4ta categoría CAS'    AS NOMBRE,
        'DESCUENTO'                         AS TIPO,              -- legacy
        'DESCUENTO'                         AS NATURALEZA,
        'DESCUENTO'                         AS TIPO_CONCEPTO,     -- CK no admite RETENCION_TRIBUTARIA
        '3042'                              AS CODIGO_TRIBUTO_SUNAT,
        'N' AS AFECTO_IR_5TA, 'N' AS AFECTO_APORTE_PENS, 'N' AS AFECTO_ESSALUD,
        'N' AS ES_MUC, 'N' AS ES_CUC,
        '1057'                              AS REGIMEN_APLICABLE  -- CAS = 1057 (CK)
    FROM DUAL
) s
ON ( d.CODIGO = s.CODIGO )
WHEN MATCHED THEN UPDATE SET
    d.NOMBRE               = s.NOMBRE,
    d.CODIGO_MEF           = s.CODIGO_MEF,
    d.TIPO                 = s.TIPO,
    d.NATURALEZA           = s.NATURALEZA,
    d.TIPO_CONCEPTO        = s.TIPO_CONCEPTO,
    d.CODIGO_TRIBUTO_SUNAT = s.CODIGO_TRIBUTO_SUNAT,
    d.AFECTO_IR_5TA        = s.AFECTO_IR_5TA,
    d.AFECTO_APORTE_PENS   = s.AFECTO_APORTE_PENS,
    d.AFECTO_ESSALUD       = s.AFECTO_ESSALUD,
    d.ES_MUC               = s.ES_MUC,
    d.ES_CUC               = s.ES_CUC,
    d.REGIMEN_APLICABLE    = s.REGIMEN_APLICABLE,
    d.ACTIVO               = 1
WHEN NOT MATCHED THEN INSERT (
    CODIGO, CODIGO_MEF, NOMBRE, TIPO, NATURALEZA,
    TIPO_CONCEPTO, CODIGO_TRIBUTO_SUNAT,
    AFECTO_IR_5TA, AFECTO_APORTE_PENS, AFECTO_ESSALUD,
    ES_MUC, ES_CUC, REGIMEN_APLICABLE,
    ACTIVO, CREATED_AT, FECHA_VIG_INI
) VALUES (
    s.CODIGO, s.CODIGO_MEF, s.NOMBRE, s.TIPO, s.NATURALEZA,
    s.TIPO_CONCEPTO, s.CODIGO_TRIBUTO_SUNAT,
    s.AFECTO_IR_5TA, s.AFECTO_APORTE_PENS, s.AFECTO_ESSALUD,
    s.ES_MUC, s.ES_CUC, s.REGIMEN_APLICABLE,
    1, CURRENT_TIMESTAMP, DATE '2026-01-01'
);

COMMIT;
