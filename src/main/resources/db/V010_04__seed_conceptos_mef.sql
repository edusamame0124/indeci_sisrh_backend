-- ============================================================================
-- Spec 010 / V010_04 — Seeds catálogo MEF (Ley 32448 — SPEC §6.2)
-- Inserta los ~20 conceptos MEF mínimos necesarios para que el motor
-- regulatorio funcione. Usa MERGE por CODIGO_MEF (idempotente).
-- Se crean con TIPO_CONCEPTO y flags AFECTO_* correctos; el motor se basa
-- en estas filas para calcular aporte pensionario, ESSALUD, asig. familiar.
-- ============================================================================

MERGE INTO GESTIONRRHH.INDECI_CONCEPTO_PLANILLA d
USING (
    -- ============ RÉGIMEN 276 ============
    SELECT '00101' AS CODIGO_MEF, 'Remuneración Principal'                  AS NOMBRE, 'REMUNERATIVO'     AS TIPO_CONCEPTO, 'N' AS AFECTO_IR_5TA, 'S' AS AFECTO_APORTE_PENS, 'S' AS AFECTO_ESSALUD, 'N' AS ES_MUC, 'N' AS ES_CUC, '276'    AS REGIMEN_APLICABLE FROM DUAL UNION ALL
    SELECT '00102', 'Monto Único Consolidado (MUC)',                              'REMUNERATIVO',     'N', 'S', 'S', 'S', 'N', '276'    FROM DUAL UNION ALL
    SELECT '00103', 'Bonificación Personal (5% por quinquenio)',                  'REMUNERATIVO',     'N', 'S', 'S', 'N', 'N', '276'    FROM DUAL UNION ALL
    SELECT '00201', 'Aguinaldo Julio',                                            'NO_REMUNERATIVO',  'N', 'S', 'N', 'N', 'N', '276'    FROM DUAL UNION ALL
    SELECT '00202', 'Aguinaldo Diciembre',                                        'NO_REMUNERATIVO',  'N', 'S', 'N', 'N', 'N', '276'    FROM DUAL UNION ALL
    -- ============ RÉGIMEN 728 ============
    SELECT '00301', 'Sueldo Básico',                                              'REMUNERATIVO',     'S', 'S', 'S', 'N', 'N', '728'    FROM DUAL UNION ALL
    SELECT '00302', 'Asignación Familiar',                                        'REMUNERATIVO',     'S', 'S', 'N', 'N', 'N', '728'    FROM DUAL UNION ALL
    SELECT '00303', 'Horas Extras',                                               'REMUNERATIVO',     'S', 'S', 'S', 'N', 'N', '728'    FROM DUAL UNION ALL
    SELECT '00401', 'Gratificación Julio',                                        'NO_REMUNERATIVO',  'S', 'S', 'N', 'N', 'N', '728'    FROM DUAL UNION ALL
    SELECT '00402', 'Gratificación Diciembre',                                    'NO_REMUNERATIVO',  'S', 'S', 'N', 'N', 'N', '728'    FROM DUAL UNION ALL
    SELECT '00403', 'Bonificación Extraordinaria 9%',                             'NO_REMUNERATIVO',  'S', 'N', 'N', 'N', 'N', '728'    FROM DUAL UNION ALL
    -- ============ RÉGIMEN CAS (1057) ============
    SELECT '00501', 'Remuneración CAS',                                           'REMUNERATIVO',     'N', 'S', 'S', 'N', 'N', '1057'   FROM DUAL UNION ALL
    SELECT '00502', 'Asignación Familiar CAS',                                    'REMUNERATIVO',     'N', 'S', 'N', 'N', 'N', '1057'   FROM DUAL UNION ALL
    -- ============ DESCUENTOS (todos los regímenes) ============
    SELECT '05001', 'Aporte ONP 13%',                                             'APORTE_TRABAJADOR','N', 'N', 'N', 'N', 'N', 'TODOS'  FROM DUAL UNION ALL
    SELECT '05002', 'Aporte AFP 10%',                                             'APORTE_TRABAJADOR','N', 'N', 'N', 'N', 'N', 'TODOS'  FROM DUAL UNION ALL
    SELECT '05003', 'Comisión AFP',                                               'APORTE_TRABAJADOR','N', 'N', 'N', 'N', 'N', 'TODOS'  FROM DUAL UNION ALL
    SELECT '05004', 'Prima Seguro AFP',                                           'APORTE_TRABAJADOR','N', 'N', 'N', 'N', 'N', 'TODOS'  FROM DUAL UNION ALL
    SELECT '05101', 'Retención 5ta Categoría',                                    'DESCUENTO',        'N', 'N', 'N', 'N', 'N', '728'    FROM DUAL UNION ALL
    SELECT '05201', 'Pensión Alimentaria',                                        'DESCUENTO',        'N', 'N', 'N', 'N', 'N', 'TODOS'  FROM DUAL UNION ALL
    SELECT '05202', 'Embargo Judicial',                                           'DESCUENTO',        'N', 'N', 'N', 'N', 'N', 'TODOS'  FROM DUAL UNION ALL
    SELECT '05301', 'Cuota Préstamo Interno',                                     'DESCUENTO',        'N', 'N', 'N', 'N', 'N', 'TODOS'  FROM DUAL UNION ALL
    SELECT '05401', 'Descuento por Tardanza',                                     'DESCUENTO',        'N', 'N', 'N', 'N', 'N', 'TODOS'  FROM DUAL UNION ALL
    SELECT '05402', 'Descuento por Falta',                                        'DESCUENTO',        'N', 'N', 'N', 'N', 'N', 'TODOS'  FROM DUAL UNION ALL
    -- ============ APORTES EMPLEADOR ============
    SELECT '06001', 'ESSALUD 9% (Empleador)',                                     'APORTE_EMPLEADOR', 'N', 'N', 'N', 'N', 'N', 'TODOS'  FROM DUAL UNION ALL
    SELECT '06002', 'Costo Único Consolidado (CUC)',                              'APORTE_EMPLEADOR', 'N', 'N', 'N', 'N', 'S', 'TODOS'  FROM DUAL
) s
ON ( d.CODIGO_MEF = s.CODIGO_MEF )
WHEN MATCHED THEN UPDATE SET
    d.NOMBRE             = s.NOMBRE,
    d.TIPO_CONCEPTO      = s.TIPO_CONCEPTO,
    d.AFECTO_IR_5TA      = s.AFECTO_IR_5TA,
    d.AFECTO_APORTE_PENS = s.AFECTO_APORTE_PENS,
    d.AFECTO_ESSALUD     = s.AFECTO_ESSALUD,
    d.ES_MUC             = s.ES_MUC,
    d.ES_CUC             = s.ES_CUC,
    d.REGIMEN_APLICABLE  = s.REGIMEN_APLICABLE
WHEN NOT MATCHED THEN INSERT (
    CODIGO, CODIGO_MEF, NOMBRE, TIPO, NATURALEZA,
    TIPO_CONCEPTO, AFECTO_IR_5TA, AFECTO_APORTE_PENS, AFECTO_ESSALUD,
    ES_MUC, ES_CUC, REGIMEN_APLICABLE,
    ACTIVO, CREATED_AT, FECHA_VIG_INI
) VALUES (
    s.CODIGO_MEF, s.CODIGO_MEF, s.NOMBRE,
    -- TIPO legacy (compatibilidad con frontend existente): INGRESO|DESCUENTO derivado
    CASE
        WHEN s.TIPO_CONCEPTO IN ('REMUNERATIVO','NO_REMUNERATIVO') THEN 'INGRESO'
        WHEN s.TIPO_CONCEPTO IN ('DESCUENTO','APORTE_TRABAJADOR')   THEN 'DESCUENTO'
        ELSE 'APORTE'
    END,
    s.TIPO_CONCEPTO,
    s.TIPO_CONCEPTO, s.AFECTO_IR_5TA, s.AFECTO_APORTE_PENS, s.AFECTO_ESSALUD,
    s.ES_MUC, s.ES_CUC, s.REGIMEN_APLICABLE,
    1, CURRENT_TIMESTAMP, DATE '2026-01-01'
);

COMMIT;
