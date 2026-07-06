-- ============================================================
-- V012_10 — Track B: AGUINALDO por régimen (proceso APARTE)
--
-- Reemplaza el enfoque F4 (gratificación CAS en la planilla regular, revertido).
-- El AGUINALDO se genera como proceso aparte (tipo AGUINALDO) con reglas por
-- régimen en AguinaldoCalculator (código): SERVIR 100% / CAS %manual (piso) / 276 fijo.
--
-- Esta migración solo siembra:
--   1) Parámetros: AGUINALDO_CAS_PISO (300.00) y AGUINALDO_276_MONTO (300.00).
--      (El % de CAS NO se persiste: es input manual del proceso.)
--   2) Conceptos de aguinaldo por régimen con la AFECTACIÓN correcta (pto 3):
--      AFECTO_IR_5TA='S' (suma a proyección renta 5ta), AFECTO_APORTE_PENS='N',
--      AFECTO_ESSALUD='N'. 276=00201/00202 (se alinean flags; no los usa código),
--      CAS=0077/0025, SERVIR=provisional PENDIENTE_VALIDACION (código MEF a
--      confirmar por RR.HH.; no bloquea el cálculo).
--
-- Prerrequisito: rodar el rollback de V012_09 (tabla INDECI_REGLA_BENEFICIO_CAS
-- ya no se usa). Idempotente (MERGE).
-- ============================================================

-- 1) Parámetros del aguinaldo (piso CAS + monto fijo 276).
MERGE INTO GESTIONRRHH.INDECI_PARAMETRO_REMUNERATIVO d
USING (
    SELECT 'AGUINALDO_CAS_PISO'  AS CODIGO_PARAMETRO, 2026 AS ANIO_FISCAL, NULL AS REGIMEN_LABORAL_ID, 300.000000 AS VALOR_NUMERICO, 'PEN' AS UNIDAD FROM DUAL UNION ALL
    SELECT 'AGUINALDO_276_MONTO',                     2026,                NULL,                       300.000000,                 'PEN' FROM DUAL
) s
ON ( d.CODIGO_PARAMETRO = s.CODIGO_PARAMETRO AND d.ANIO_FISCAL = s.ANIO_FISCAL AND d.REGIMEN_LABORAL_ID IS NULL )
WHEN NOT MATCHED THEN INSERT (
    CODIGO_PARAMETRO, ANIO_FISCAL, REGIMEN_LABORAL_ID, VALOR_NUMERICO, UNIDAD, FECHA_VIG_INI, ACTIVO
) VALUES (
    s.CODIGO_PARAMETRO, s.ANIO_FISCAL, s.REGIMEN_LABORAL_ID, s.VALOR_NUMERICO, s.UNIDAD, DATE '2026-01-01', 1
);

-- 2) Conceptos de aguinaldo por régimen. Afectación (pto 3): renta 5ta SÍ,
--    pensión y EsSalud NO. MERGE actualiza flags de los existentes (00201/00202
--    no los usa el motor) y crea CAS/SERVIR si faltan.
MERGE INTO GESTIONRRHH.INDECI_CONCEPTO_PLANILLA d
USING (
    SELECT '00201' AS CODIGO_MEF, 'Aguinaldo Julio'                          AS NOMBRE, 'NO_REMUNERATIVO' AS TIPO_CONCEPTO, 'S' AS AFECTO_IR_5TA, 'N' AS AFECTO_APORTE_PENS, 'N' AS AFECTO_ESSALUD, 'N' AS ES_MUC, 'N' AS ES_CUC, '276'    AS REGIMEN_APLICABLE FROM DUAL UNION ALL
    SELECT '00202', 'Aguinaldo Diciembre',                                          'NO_REMUNERATIVO', 'S', 'N', 'N', 'N', 'N', '276'    FROM DUAL UNION ALL
    SELECT '0077',  'Aguinaldo Julio CAS',                                          'NO_REMUNERATIVO', 'S', 'N', 'N', 'N', 'N', '1057'   FROM DUAL UNION ALL
    SELECT '0025',  'Aguinaldo Diciembre CAS',                                      'NO_REMUNERATIVO', 'S', 'N', 'N', 'N', 'N', '1057'   FROM DUAL UNION ALL
    SELECT 'AGUISRVPV', 'Aguinaldo SERVIR (PENDIENTE VALIDACION MEF)',              'NO_REMUNERATIVO', 'S', 'N', 'N', 'N', 'N', 'SERVIR' FROM DUAL
) s
ON ( d.CODIGO_MEF = s.CODIGO_MEF )
WHEN MATCHED THEN UPDATE SET
    d.NOMBRE = s.NOMBRE, d.TIPO_CONCEPTO = s.TIPO_CONCEPTO,
    d.AFECTO_IR_5TA = s.AFECTO_IR_5TA, d.AFECTO_APORTE_PENS = s.AFECTO_APORTE_PENS,
    d.AFECTO_ESSALUD = s.AFECTO_ESSALUD, d.ES_MUC = s.ES_MUC, d.ES_CUC = s.ES_CUC,
    d.REGIMEN_APLICABLE = s.REGIMEN_APLICABLE
WHEN NOT MATCHED THEN INSERT (
    CODIGO, CODIGO_MEF, NOMBRE, TIPO, NATURALEZA,
    TIPO_CONCEPTO, AFECTO_IR_5TA, AFECTO_APORTE_PENS, AFECTO_ESSALUD,
    ES_MUC, ES_CUC, REGIMEN_APLICABLE, ACTIVO, CREATED_AT, FECHA_VIG_INI
) VALUES (
    s.CODIGO_MEF, s.CODIGO_MEF, s.NOMBRE, 'INGRESO', s.TIPO_CONCEPTO,
    s.TIPO_CONCEPTO, s.AFECTO_IR_5TA, s.AFECTO_APORTE_PENS, s.AFECTO_ESSALUD,
    s.ES_MUC, s.ES_CUC, s.REGIMEN_APLICABLE, 1, CURRENT_TIMESTAMP, DATE '2026-01-01'
);

COMMIT;
