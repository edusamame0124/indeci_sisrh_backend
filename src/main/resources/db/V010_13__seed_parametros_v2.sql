-- ============================================================================
-- Spec 010 / V010_13 — Seed parámetros remunerativos v2 (SPEC §16.1)
--
-- Agrega los 6 parámetros marcados "NUEVO v2" que no sembró V010_03:
--   - TOPE_SEGURO_AFP             12209.11  PEN  → tope base prima seguro AFP (§5.6)
--   - PRIMA_AFP                   0.013700  PCT  → tasa prima seguro AFP (Excel BX5)
--   - DS_265_2024_EF              50.00     PEN  → incremento remunerativo D.S. 265-2024-EF
--   - DS_279_2024_EF              100.00    PEN  → incremento remunerativo D.S. 279-2024-EF
--   - DS_327_2025_EF              53.00     PEN  → incremento remunerativo D.S. 327-2025-EF
--   - ESSALUD_MINIMO              101.70    PEN  → piso ESSALUD empleador (§5.5)
--   - TASA_ESSALUD_EPS_EMPLEADOR  0.067500  PCT  → ESSALUD empleador con EPS (§5.5)
--   - TASA_ESSALUD_EPS_COPAGO     0.022500  PCT  → copago EPS del trabajador (§5.5)
--
-- También siembra la ESCALA DE 5TA CATEGORÍA (SPEC §16.2) como 9 filas-
-- parámetro (5 tasas + 4 límites en UIT). El motor las lee y reconstruye la
-- escala progresiva del TUO LIR en memoria:
--   IR5TA_TRAMO1_LIM_UIT=5   IR5TA_TRAMO1_TASA=0.08
--   IR5TA_TRAMO2_LIM_UIT=20  IR5TA_TRAMO2_TASA=0.14
--   IR5TA_TRAMO3_LIM_UIT=35  IR5TA_TRAMO3_TASA=0.17
--   IR5TA_TRAMO4_LIM_UIT=45  IR5TA_TRAMO4_TASA=0.20
--                            IR5TA_TRAMO5_TASA=0.30  (último tramo, sin tope)
--
-- DECISIONES DE DISEÑO:
--   - REGIMEN_LABORAL_ID = NULL (global) en todas. El SPEC rotula algunos
--     "AFP" o "SERVIR/728/CAS", pero REGIMEN_LABORAL_ID es FK a régimen
--     LABORAL (276/728/1057/SERVIR) — "AFP" no es uno, y "SERVIR/728/CAS"
--     son varios. La granularidad correcta es global; el motor decide a
--     quién aplica según el concepto MEF. Coherente con TASA_AFP_APORTE
--     que V010_03 sembró global.
--   - Límites de tramo (IR5TA_TRAMO*_LIM_UIT): VALOR_NUMERICO en múltiplos
--     de UIT (5/20/35/45), UNIDAD = NULL. Los múltiplos son constantes
--     legales del TUO LIR; el motor convierte a soles con el parámetro UIT.
--     Así, si cambia la UIT, la escala NO se re-siembra.
--   - Idempotente vía MERGE WHEN NOT MATCHED (no pisa valores ya cargados).
--
-- Ejecutar en esquema GESTIONRRHH / Oracle 19c+.
-- ============================================================================

MERGE INTO GESTIONRRHH.INDECI_PARAMETRO_REMUNERATIVO d
USING (
    SELECT 'TOPE_SEGURO_AFP' AS CODIGO_PARAMETRO, 2026 AS ANIO_FISCAL, 12209.110000 AS VALOR_NUMERICO, 'PEN' AS UNIDAD FROM DUAL UNION ALL
    SELECT 'PRIMA_AFP',                            2026,                0.013700,                     'PCT'             FROM DUAL UNION ALL
    SELECT 'DS_265_2024_EF',                       2026,                50.000000,                    'PEN'             FROM DUAL UNION ALL
    SELECT 'DS_279_2024_EF',                       2026,                100.000000,                   'PEN'             FROM DUAL UNION ALL
    SELECT 'DS_327_2025_EF',                       2026,                53.000000,                    'PEN'             FROM DUAL UNION ALL
    SELECT 'ESSALUD_MINIMO',                       2026,                101.700000,                   'PEN'             FROM DUAL UNION ALL
    -- Split ESSALUD cuando el trabajador tiene EPS (SPEC §5.5): 6.75% empleador + 2.25% copago.
    SELECT 'TASA_ESSALUD_EPS_EMPLEADOR',           2026,                0.067500,                     'PCT'             FROM DUAL UNION ALL
    SELECT 'TASA_ESSALUD_EPS_COPAGO',              2026,                0.022500,                     'PCT'             FROM DUAL
) s
ON (
    d.CODIGO_PARAMETRO = s.CODIGO_PARAMETRO
    AND d.ANIO_FISCAL  = s.ANIO_FISCAL
    AND d.REGIMEN_LABORAL_ID IS NULL
)
WHEN NOT MATCHED THEN INSERT (
    CODIGO_PARAMETRO, ANIO_FISCAL, REGIMEN_LABORAL_ID, VALOR_NUMERICO, UNIDAD, FECHA_VIG_INI, ACTIVO
) VALUES (
    s.CODIGO_PARAMETRO, s.ANIO_FISCAL, NULL, s.VALOR_NUMERICO, s.UNIDAD, DATE '2026-01-01', 1
);

-- ----------------------------------------------------------------------------
-- Escala 5ta categoría 2026 (SPEC §16.2) — 9 filas-parámetro.
-- Límites en múltiplos de UIT (UNIDAD NULL); tasas en PCT.
-- ----------------------------------------------------------------------------
MERGE INTO GESTIONRRHH.INDECI_PARAMETRO_REMUNERATIVO d
USING (
    SELECT 'IR5TA_TRAMO1_LIM_UIT' AS CODIGO_PARAMETRO, 2026 AS ANIO_FISCAL, 5.000000  AS VALOR_NUMERICO, CAST(NULL AS VARCHAR2(20)) AS UNIDAD FROM DUAL UNION ALL
    SELECT 'IR5TA_TRAMO2_LIM_UIT',                      2026,                20.000000,                  CAST(NULL AS VARCHAR2(20))            FROM DUAL UNION ALL
    SELECT 'IR5TA_TRAMO3_LIM_UIT',                      2026,                35.000000,                  CAST(NULL AS VARCHAR2(20))            FROM DUAL UNION ALL
    SELECT 'IR5TA_TRAMO4_LIM_UIT',                      2026,                45.000000,                  CAST(NULL AS VARCHAR2(20))            FROM DUAL UNION ALL
    SELECT 'IR5TA_TRAMO1_TASA',                         2026,                0.080000,                   'PCT'                                 FROM DUAL UNION ALL
    SELECT 'IR5TA_TRAMO2_TASA',                         2026,                0.140000,                   'PCT'                                 FROM DUAL UNION ALL
    SELECT 'IR5TA_TRAMO3_TASA',                         2026,                0.170000,                   'PCT'                                 FROM DUAL UNION ALL
    SELECT 'IR5TA_TRAMO4_TASA',                         2026,                0.200000,                   'PCT'                                 FROM DUAL UNION ALL
    SELECT 'IR5TA_TRAMO5_TASA',                         2026,                0.300000,                   'PCT'                                 FROM DUAL
) s
ON (
    d.CODIGO_PARAMETRO = s.CODIGO_PARAMETRO
    AND d.ANIO_FISCAL  = s.ANIO_FISCAL
    AND d.REGIMEN_LABORAL_ID IS NULL
)
WHEN NOT MATCHED THEN INSERT (
    CODIGO_PARAMETRO, ANIO_FISCAL, REGIMEN_LABORAL_ID, VALOR_NUMERICO, UNIDAD, FECHA_VIG_INI, ACTIVO
) VALUES (
    s.CODIGO_PARAMETRO, s.ANIO_FISCAL, NULL, s.VALOR_NUMERICO, s.UNIDAD, DATE '2026-01-01', 1
);

COMMIT;
