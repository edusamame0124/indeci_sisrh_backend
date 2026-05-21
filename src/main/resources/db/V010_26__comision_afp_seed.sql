-- ============================================================================
-- Spec 013 / C1 / V010_26 — Comisiones AFP por (AFP × tipo) — SPEC §16.3
--
-- El formulario "Registrar pensión" autocompletará el % Comisión al elegir
-- AFP + Tipo de comisión. Hoy esa tasa no está parametrizada — el motor
-- usa lo que carga el operador en EmpleadoPension.porcentajeComision (o 0).
--
-- DECISIÓN DE MODELADO: INDECI_PARAMETRO_REMUNERATIVO.REGIMEN_LABORAL_ID es
-- FK a régimen LABORAL (276/728/1057/SERVIR) — no aplica a AFPs. Para
-- discriminar por AFP+tipo de comisión, se compone el CODIGO_PARAMETRO con
-- el formato: COMISION_AFP_<CODIGO_REGIMEN_PENSIONARIO>_<CODIGO_TIPO_COMISION>.
-- REGIMEN_LABORAL_ID = NULL (global). El endpoint /tasas-vigentes compone el
-- código en runtime a partir de las FK del EmpleadoPension.
--
-- TASAS (SPEC §16.3 — referencia: PLANILLA_SERVIR_FEB26.xlsx):
--   | AFP       | Flujo  | Mixta |
--   | Integra   | 1.60%  | 0.82% |
--   | Prima     | 1.60%  | 0.82% |
--   | Hábitat   | 1.47%  | 0.82% |
--   | Profuturo | 1.69%  | 1.20% |
--
-- AVISO IMPORTANTE: SPEC §16.3 marca las tasas con "~" (aproximadas) y dice
-- "Verificar SBS antes de producción". Estos valores son referencia 2026;
-- antes de la primera planilla productiva el equipo debe confirmar las tasas
-- vigentes con la circular SBS del año y actualizar si difieren.
--
-- IDEMPOTENTE: MERGE WHEN NOT MATCHED — no pisa filas existentes.
-- ASUMIDO: los CODIGO de INDECI_REGIMEN_PENSIONARIO son 'INTEGRA', 'PRIMA',
-- 'HABITAT', 'PROFUTURO' (V010_15 sembró ONP con CODIGO='ONP'); los CODIGO
-- de INDECI_TIPO_COMISION_AFP son 'FLUJO' y 'MIXTA'. Si tu BD usa otros
-- valores, ajusta los CODIGO_PARAMETRO de abajo para que el endpoint
-- /tasas-vigentes los encuentre.
-- ============================================================================

MERGE INTO GESTIONRRHH.INDECI_PARAMETRO_REMUNERATIVO d
USING (
    SELECT 'COMISION_AFP_INTEGRA_FLUJO'   AS CODIGO_PARAMETRO, 2026 AS ANIO_FISCAL, 0.015500 AS VALOR_NUMERICO, 'PCT' AS UNIDAD FROM DUAL UNION ALL
    SELECT 'COMISION_AFP_INTEGRA_MIXTA',                        2026,                0.007800,                  'PCT'             FROM DUAL UNION ALL
    SELECT 'COMISION_AFP_PRIMA_FLUJO',                          2026,                0.016000,                  'PCT'             FROM DUAL UNION ALL
    SELECT 'COMISION_AFP_PRIMA_MIXTA',                          2026,                0.012500,                  'PCT'             FROM DUAL UNION ALL
    SELECT 'COMISION_AFP_HABITAT_FLUJO',                        2026,                0.014700,                  'PCT'             FROM DUAL UNION ALL
    SELECT 'COMISION_AFP_HABITAT_MIXTA',                        2026,                0.012500,                  'PCT'             FROM DUAL UNION ALL
    SELECT 'COMISION_AFP_PROFUTURO_FLUJO',                      2026,                0.016900,                  'PCT'             FROM DUAL UNION ALL
    SELECT 'COMISION_AFP_PROFUTURO_MIXTA',                      2026,                0.006800,                  'PCT'             FROM DUAL
) s
ON (
    d.CODIGO_PARAMETRO = s.CODIGO_PARAMETRO
    AND d.ANIO_FISCAL  = s.ANIO_FISCAL
    AND d.REGIMEN_LABORAL_ID IS NULL
)
WHEN MATCHED THEN
    UPDATE SET d.VALOR_NUMERICO = s.VALOR_NUMERICO -- Cláusula añadida para actualizar si difieren
WHEN NOT MATCHED THEN 
    INSERT (
        CODIGO_PARAMETRO, ANIO_FISCAL, REGIMEN_LABORAL_ID, VALOR_NUMERICO, UNIDAD,
        FECHA_VIG_INI, ACTIVO, CREATED_AT
    ) VALUES (
        s.CODIGO_PARAMETRO, s.ANIO_FISCAL, NULL, s.VALOR_NUMERICO, s.UNIDAD,
        DATE '2026-01-01', 1, CURRENT_TIMESTAMP
    );

COMMIT;

-- Verificación rápida:
-- SELECT CODIGO_PARAMETRO, VALOR_NUMERICO, UNIDAD
--   FROM GESTIONRRHH.INDECI_PARAMETRO_REMUNERATIVO
--  WHERE CODIGO_PARAMETRO LIKE 'COMISION_AFP_%'
--  ORDER BY CODIGO_PARAMETRO;
