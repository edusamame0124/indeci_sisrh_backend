-- ============================================================================
-- P0 / V010_55 - Parametros de subsidios EsSalud
--
-- Objetivo:
--   Parametrizar la formula de subsidios para no hardcodear tope UIT, divisor
--   ni dias asumidos por la entidad.
--
-- Parametros:
--   SUBSIDIO_TOPE_PCT_UIT                    = 0.45
--   SUBSIDIO_DIVISOR_PROMEDIO                = 360
--   SUBSIDIO_DIAS_ASUME_ENTIDAD_ENFERMEDAD   = 20
--   SUBSIDIO_DIAS_ASUME_ENTIDAD_MATERNIDAD   = 0
--
-- Idempotente. Ejecutar en esquema GESTIONRRHH / Oracle 19c+.
-- ============================================================================

SET SERVEROUTPUT ON;

MERGE INTO INDECI_PARAMETRO_REMUNERATIVO d
USING (
    SELECT 'SUBSIDIO_TOPE_PCT_UIT' AS CODIGO_PARAMETRO,
           2026 AS ANIO_FISCAL,
           CAST(NULL AS NUMBER) AS REGIMEN_LABORAL_ID,
           0.450000 AS VALOR_NUMERICO,
           'PCT' AS UNIDAD,
           DATE '2026-01-01' AS FECHA_VIG_INI
      FROM DUAL UNION ALL
    SELECT 'SUBSIDIO_DIVISOR_PROMEDIO',
           2026,
           CAST(NULL AS NUMBER),
           360.000000,
           'DIAS',
           DATE '2026-01-01'
      FROM DUAL UNION ALL
    SELECT 'SUBSIDIO_DIAS_ASUME_ENTIDAD_ENFERMEDAD',
           2026,
           CAST(NULL AS NUMBER),
           20.000000,
           'DIAS',
           DATE '2026-01-01'
      FROM DUAL UNION ALL
    SELECT 'SUBSIDIO_DIAS_ASUME_ENTIDAD_MATERNIDAD',
           2026,
           CAST(NULL AS NUMBER),
           0.000000,
           'DIAS',
           DATE '2026-01-01'
      FROM DUAL
) s
ON (
    d.CODIGO_PARAMETRO = s.CODIGO_PARAMETRO
    AND d.ANIO_FISCAL = s.ANIO_FISCAL
    AND d.REGIMEN_LABORAL_ID IS NULL
)
WHEN MATCHED THEN UPDATE SET
    d.VALOR_NUMERICO = s.VALOR_NUMERICO,
    d.UNIDAD         = s.UNIDAD,
    d.FECHA_VIG_INI  = s.FECHA_VIG_INI,
    d.FECHA_VIG_FIN  = NULL,
    d.ACTIVO         = 1
WHEN NOT MATCHED THEN INSERT (
    CODIGO_PARAMETRO,
    ANIO_FISCAL,
    REGIMEN_LABORAL_ID,
    VALOR_NUMERICO,
    UNIDAD,
    FECHA_VIG_INI,
    FECHA_VIG_FIN,
    ACTIVO,
    CREATED_AT
) VALUES (
    s.CODIGO_PARAMETRO,
    s.ANIO_FISCAL,
    s.REGIMEN_LABORAL_ID,
    s.VALOR_NUMERICO,
    s.UNIDAD,
    s.FECHA_VIG_INI,
    NULL,
    1,
    CURRENT_TIMESTAMP
);

COMMIT;

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count
      FROM INDECI_PARAMETRO_REMUNERATIVO
     WHERE CODIGO_PARAMETRO IN (
           'SUBSIDIO_TOPE_PCT_UIT',
           'SUBSIDIO_DIVISOR_PROMEDIO',
           'SUBSIDIO_DIAS_ASUME_ENTIDAD_ENFERMEDAD',
           'SUBSIDIO_DIAS_ASUME_ENTIDAD_MATERNIDAD')
       AND ANIO_FISCAL = 2026
       AND ACTIVO = 1;

    DBMS_OUTPUT.PUT_LINE('Parametros subsidios activos 2026: ' || v_count || ' (esperado 4)');
END;
/
