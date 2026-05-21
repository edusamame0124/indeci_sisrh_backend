-- ============================================================================
-- Spec 010 / V010_03 — Seeds parámetros remunerativos 2026
-- Valores de SPEC §15.1. Verificar con fuentes oficiales (MEF/SBS/BCR) antes
-- de producción. Las columnas REGIMEN_LABORAL_ID se setean NULL si aplica a
-- todos los regímenes; para 276 y CAS se busca el ID correspondiente.
-- Idempotente vía MERGE (evita duplicar si se reaplica).
-- ============================================================================

-- Helper: obtenemos IDs de regímenes 276 y 1057 si existen. Si no existen,
-- el MERGE de los parámetros específicos por régimen no insertará nada.
-- Ajustar manualmente si los códigos difieren del estándar.

MERGE INTO GESTIONRRHH.INDECI_PARAMETRO_REMUNERATIVO d
USING (
    SELECT 'UIT'              AS CODIGO_PARAMETRO, 2026 AS ANIO_FISCAL, NULL AS REGIMEN_LABORAL_ID, 5350.000000 AS VALOR_NUMERICO, 'PEN' AS UNIDAD FROM DUAL UNION ALL
    SELECT 'RMV',                                  2026,                NULL,                       1025.000000,                 'PEN' FROM DUAL UNION ALL
    SELECT 'ASIG_FAMILIAR',                        2026,                NULL,                       102.500000,                  'PEN' FROM DUAL UNION ALL
    SELECT 'TASA_ONP',                             2026,                NULL,                       0.130000,                    'PCT' FROM DUAL UNION ALL
    SELECT 'TASA_ESSALUD',                         2026,                NULL,                       0.090000,                    'PCT' FROM DUAL UNION ALL
    SELECT 'TASA_AFP_APORTE',                      2026,                NULL,                       0.100000,                    'PCT' FROM DUAL
) s
ON (
    d.CODIGO_PARAMETRO = s.CODIGO_PARAMETRO
    AND d.ANIO_FISCAL  = s.ANIO_FISCAL
    AND d.REGIMEN_LABORAL_ID IS NULL
)
WHEN NOT MATCHED THEN INSERT (
    CODIGO_PARAMETRO, ANIO_FISCAL, REGIMEN_LABORAL_ID, VALOR_NUMERICO, UNIDAD, FECHA_VIG_INI, ACTIVO
) VALUES (
    s.CODIGO_PARAMETRO, s.ANIO_FISCAL, s.REGIMEN_LABORAL_ID, s.VALOR_NUMERICO, s.UNIDAD, DATE '2026-01-01', 1
);

-- Parámetros específicos por régimen (resuelven el REGIMEN_LABORAL_ID dinámicamente)
MERGE INTO GESTIONRRHH.INDECI_PARAMETRO_REMUNERATIVO d
USING (
    SELECT 'MUC_INCREMENTO_2026' AS CODIGO_PARAMETRO,
           2026                  AS ANIO_FISCAL,
           rl.ID                 AS REGIMEN_LABORAL_ID,
           215.000000            AS VALOR_NUMERICO,
           'PEN'                 AS UNIDAD
      FROM GESTIONRRHH.INDECI_REGIMEN_LABORAL rl
     WHERE rl.CODIGO = '276'
       AND rl.ACTIVO = 1
    UNION ALL
    SELECT 'INCREMENTO_CAS_2026',
           2026,
           rl.ID,
           100.000000,
           'PEN'
      FROM GESTIONRRHH.INDECI_REGIMEN_LABORAL rl
     WHERE rl.CODIGO = '1057'
       AND rl.ACTIVO = 1
    UNION ALL
    SELECT 'TOLERANCIA_TARDANZA_MIN',
           2026,
           rl.ID,
           5.000000,
           'MIN'
      FROM GESTIONRRHH.INDECI_REGIMEN_LABORAL rl
     WHERE rl.CODIGO = '276'
       AND rl.ACTIVO = 1
) s
ON (
    d.CODIGO_PARAMETRO   = s.CODIGO_PARAMETRO
    AND d.ANIO_FISCAL    = s.ANIO_FISCAL
    AND d.REGIMEN_LABORAL_ID = s.REGIMEN_LABORAL_ID
)
WHEN NOT MATCHED THEN INSERT (
    CODIGO_PARAMETRO, ANIO_FISCAL, REGIMEN_LABORAL_ID, VALOR_NUMERICO, UNIDAD, FECHA_VIG_INI, ACTIVO
) VALUES (
    s.CODIGO_PARAMETRO, s.ANIO_FISCAL, s.REGIMEN_LABORAL_ID, s.VALOR_NUMERICO, s.UNIDAD, DATE '2026-01-01', 1
);

COMMIT;
