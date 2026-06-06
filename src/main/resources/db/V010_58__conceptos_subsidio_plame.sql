-- ============================================================================
-- P0 / V010_58 - Conceptos SUNAT/PLAME para subsidios CAS
--
-- Confirmado por RR. HH.:
--   0915 = Subsidios por maternidad
--   0916 = Subsidios de incapacidad por enfermedad
--   2073 = Diferencial de subsidio CAS pagado por la entidad publica
--
-- Estos codigos NO son MEF/AIRHSP: el clasificador real es CODIGO_PLAME_SUNAT
-- (0915/0916/2073). Como CODIGO_MEF es NOT NULL y UNIQUE (INDECI_CONCEPTO_MEF_UK,
-- V010_12), NO se puede reusar 'NO_APLICA' (ya lo ocupa IR4TA_CAS, V010_49): se
-- usa un placeholder distinto por concepto (NA_0915/NA_0916/NA_2073). El motor
-- no resuelve estos conceptos por CODIGO_MEF, asi que el placeholder es inocuo.
--
-- Idempotente. Ejecutar en esquema GESTIONRRHH / Oracle 19c+.
-- ============================================================================

SET SERVEROUTPUT ON;

MERGE INTO INDECI_CONCEPTO_PLANILLA d
USING (
    SELECT
        'SUBSIDIO_MATERNIDAD' AS CODIGO,
        'NA_0915' AS CODIGO_MEF,
        CAST(NULL AS VARCHAR2(10 CHAR)) AS CODIGO_SISPER,
        'Subsidio por maternidad' AS NOMBRE,
        'INGRESO' AS TIPO,
        'SUBSIDIO' AS NATURALEZA,
        'NO_REMUNERATIVO' AS TIPO_CONCEPTO,
        '0915' AS CODIGO_PLAME_SUNAT,
        'N' AS AFECTO_IR_5TA,
        'N' AS AFECTO_APORTE_PENS,
        'N' AS AFECTO_ESSALUD,
        'N' AS ES_MUC,
        'N' AS ES_CUC,
        '1057' AS REGIMEN_APLICABLE
      FROM DUAL UNION ALL
    SELECT
        'SUBSIDIO_ENFERMEDAD',
        'NA_0916',
        CAST(NULL AS VARCHAR2(10 CHAR)),
        'Subsidio de incapacidad por enfermedad',
        'INGRESO',
        'SUBSIDIO',
        'NO_REMUNERATIVO',
        '0916',
        'N', 'N', 'N', 'N', 'N',
        '1057'
      FROM DUAL UNION ALL
    SELECT
        'SUBSIDIO_DIF_CAS',
        'NA_2073',
        '059-060',
        'Diferencial de subsidio CAS asumido por entidad',
        'INGRESO',
        'DIF_SUBSIDIO',
        'NO_REMUNERATIVO',
        '2073',
        'N', 'N', 'N', 'N', 'N',
        '1057'
      FROM DUAL
) s
ON (d.CODIGO = s.CODIGO)
WHEN MATCHED THEN UPDATE SET
    d.CODIGO_MEF         = s.CODIGO_MEF,
    d.CODIGO_SISPER      = s.CODIGO_SISPER,
    d.NOMBRE             = s.NOMBRE,
    d.TIPO               = s.TIPO,
    d.NATURALEZA         = s.NATURALEZA,
    d.TIPO_CONCEPTO      = s.TIPO_CONCEPTO,
    d.CODIGO_PLAME_SUNAT = s.CODIGO_PLAME_SUNAT,
    d.AFECTO_IR_5TA      = s.AFECTO_IR_5TA,
    d.AFECTO_APORTE_PENS = s.AFECTO_APORTE_PENS,
    d.AFECTO_ESSALUD     = s.AFECTO_ESSALUD,
    d.ES_MUC             = s.ES_MUC,
    d.ES_CUC             = s.ES_CUC,
    d.REGIMEN_APLICABLE  = s.REGIMEN_APLICABLE,
    d.ACTIVO             = 1
WHEN NOT MATCHED THEN INSERT (
    CODIGO, CODIGO_MEF, CODIGO_SISPER, NOMBRE, TIPO, NATURALEZA,
    TIPO_CONCEPTO, CODIGO_PLAME_SUNAT,
    AFECTO_IR_5TA, AFECTO_APORTE_PENS, AFECTO_ESSALUD,
    ES_MUC, ES_CUC, REGIMEN_APLICABLE,
    ACTIVO, CREATED_AT, FECHA_VIG_INI
) VALUES (
    s.CODIGO, s.CODIGO_MEF, s.CODIGO_SISPER, s.NOMBRE, s.TIPO, s.NATURALEZA,
    s.TIPO_CONCEPTO, s.CODIGO_PLAME_SUNAT,
    s.AFECTO_IR_5TA, s.AFECTO_APORTE_PENS, s.AFECTO_ESSALUD,
    s.ES_MUC, s.ES_CUC, s.REGIMEN_APLICABLE,
    1, CURRENT_TIMESTAMP, DATE '2026-01-01'
);

COMMIT;

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count
      FROM INDECI_CONCEPTO_PLANILLA
     WHERE CODIGO IN (
           'SUBSIDIO_MATERNIDAD',
           'SUBSIDIO_ENFERMEDAD',
           'SUBSIDIO_DIF_CAS')
       AND CODIGO_PLAME_SUNAT IN ('0915', '0916', '2073')
       AND ACTIVO = 1;

    DBMS_OUTPUT.PUT_LINE('Conceptos subsidio PLAME activos: ' || v_count || ' (esperado 3)');
END;
/
