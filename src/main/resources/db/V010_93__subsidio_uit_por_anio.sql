-- ============================================================================
-- V010_93 subsidio_uit_por_anio (P0-F2 / Base histórica — tope por año)
--
-- La base histórica topa CADA mes con el tope BIM (UIT × %) de SU propio año.
-- Para un descanso con contingencia 2026, la ventana de 12 meses abarca meses
-- de 2025 y 2026, así que el sistema necesita resolver la UIT de AMBOS años.
-- Hasta ahora solo existía la UIT 2026 → los meses 2025 no podían topar a 2407.50.
--
-- Este seed agrega, en el namespace del módulo (INDECI_SUBSIDIO_PARAMETRO_VERSION):
--   • UIT_REF por año (valores del Excel LD): 2026=5500, 2025=5350, 2024=5150,
--     2023=4950, 2022=4600. (Todas al 45%; 2021 al 55% se omite: las ventanas
--     de casos 2026 no lo alcanzan.)
--   • Parámetros constantes (BIM 45%, divisor 360, días entidad enf/mat) con
--     vigencia que cubre los años previos a 2026 (no cambian por año).
--
-- Tope resultante por año: UIT × 0.45 → 2026=2475.00, 2025=2407.50, 2024=2317.50,
-- 2023=2227.50, 2022=2070.00. (Coincide con la tabla UIT del Excel.)
--
-- Idempotente (MERGE por PARAMETRO_ID + FECHA_VIG_INI). Oracle 19c+ / GESTIONRRHH.
-- ============================================================================

SET SERVEROUTPUT ON;
SET DEFINE OFF;

MERGE INTO INDECI_SUBSIDIO_PARAMETRO_VERSION d
USING (
    -- ── UIT de referencia por año (Excel LD) ──────────────────────────────
    SELECT p.ID AS PARAMETRO_ID, 5500 AS VALOR_NUMERICO,
           CAST(NULL AS VARCHAR2(500)) AS VALOR_TEXTO,
           DATE '2026-01-01' AS FECHA_VIG_INI, DATE '2026-12-31' AS FECHA_VIG_FIN,
           'VIGENTE' AS ESTADO, 'UIT 2026 D.S. 301-2025-EF' AS FUENTE_NORMATIVA
      FROM INDECI_SUBSIDIO_PARAMETRO p WHERE p.CODIGO = 'UIT_REF'
    UNION ALL
    SELECT p.ID, 5350, NULL, DATE '2025-01-01', DATE '2025-12-31', 'VIGENTE', 'UIT 2025'
      FROM INDECI_SUBSIDIO_PARAMETRO p WHERE p.CODIGO = 'UIT_REF'
    UNION ALL
    SELECT p.ID, 5150, NULL, DATE '2024-01-01', DATE '2024-12-31', 'VIGENTE', 'UIT 2024'
      FROM INDECI_SUBSIDIO_PARAMETRO p WHERE p.CODIGO = 'UIT_REF'
    UNION ALL
    SELECT p.ID, 4950, NULL, DATE '2023-01-01', DATE '2023-12-31', 'VIGENTE', 'UIT 2023'
      FROM INDECI_SUBSIDIO_PARAMETRO p WHERE p.CODIGO = 'UIT_REF'
    UNION ALL
    SELECT p.ID, 4600, NULL, DATE '2022-01-01', DATE '2022-12-31', 'VIGENTE', 'UIT 2022'
      FROM INDECI_SUBSIDIO_PARAMETRO p WHERE p.CODIGO = 'UIT_REF'
    -- ── Constantes: vigencia para años previos a 2026 (no cambian por año) ─
    UNION ALL
    SELECT p.ID, 0.45, NULL, DATE '2020-01-01', DATE '2025-12-31', 'VIGENTE',
           'Tope BIM 45% (años previos)'
      FROM INDECI_SUBSIDIO_PARAMETRO p WHERE p.CODIGO = 'BIM_PCT_CAS'
    UNION ALL
    SELECT p.ID, 360, NULL, DATE '2020-01-01', DATE '2025-12-31', 'VIGENTE',
           'Divisor 360 (años previos)'
      FROM INDECI_SUBSIDIO_PARAMETRO p WHERE p.CODIGO = 'DIVISOR_PROMEDIO'
    UNION ALL
    SELECT p.ID, 20, NULL, DATE '2020-01-01', DATE '2025-12-31', 'VIGENTE',
           'Días entidad enfermedad (años previos)'
      FROM INDECI_SUBSIDIO_PARAMETRO p WHERE p.CODIGO = 'DIAS_ENTIDAD_ENF'
    UNION ALL
    SELECT p.ID, 0, NULL, DATE '2020-01-01', DATE '2025-12-31', 'VIGENTE',
           'Días entidad maternidad (años previos)'
      FROM INDECI_SUBSIDIO_PARAMETRO p WHERE p.CODIGO = 'DIAS_ENTIDAD_MAT'
) s ON (d.PARAMETRO_ID = s.PARAMETRO_ID AND d.ESTADO = 'VIGENTE'
        AND d.FECHA_VIG_INI = s.FECHA_VIG_INI)
WHEN NOT MATCHED THEN
    INSERT (PARAMETRO_ID, VALOR_NUMERICO, VALOR_TEXTO, FECHA_VIG_INI, FECHA_VIG_FIN,
            ESTADO, FUENTE_NORMATIVA, CREATED_BY)
    VALUES (s.PARAMETRO_ID, s.VALOR_NUMERICO, s.VALOR_TEXTO, s.FECHA_VIG_INI, s.FECHA_VIG_FIN,
            s.ESTADO, s.FUENTE_NORMATIVA, 'SEED_V010_93');

COMMIT;

BEGIN
    DBMS_OUTPUT.PUT_LINE('V010_93 UIT por año y constantes previas sembradas.');
END;
/
