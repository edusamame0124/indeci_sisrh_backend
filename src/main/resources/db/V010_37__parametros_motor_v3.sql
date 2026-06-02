-- ============================================================================
-- F1.2 / V010_37 — Parámetros Motor v3 (Decisiones RRHH C2/C3/C4 / 2026-05-31)
--
-- DECISIONES RRHH QUE APLICA ESTE SCRIPT:
--   C2 — Tope 45% UIT en EsSalud (sólo CAS). Se siembra el coeficiente
--        TOPE_ESSALUD_PCT_UIT = 0.45 (en PCT). El motor PASO 12 multiplicará
--        por UIT en runtime, así que si cambia la UIT, NO hay que re-seedear.
--        El régimen aplicable es CAS — pero la fila va con REGIMEN_LABORAL_ID
--        NULL siguiendo la convención de V010_03/V010_13 (parámetro global);
--        el motor decide aplicar el tope sólo cuando el régimen calculado
--        es CAS. Esto evita atar el parámetro al ID de un régimen concreto
--        (que puede cambiar entre entornos).
--
--   C3 — RMV 2026 = S/ 1 130 (antes S/ 1 025 sembrado en V010_03). Esto
--        recalcula automáticamente: mínimo EsSalud (101.70 ya estaba en
--        V010_13 — coincide); asignación familiar 10% RMV = 113.00 (antes
--        102.50 en V010_03).
--
--   C4 — Tope prima seguro AFP por período de devengue (no anual). Mayo
--        2026 = S/ 12 598.91 (Excel del cliente). V010_13 sembró un valor
--        anual 12 209.11 con FECHA_VIG_INI = 2026-01-01 sin FECHA_VIG_FIN.
--        Estrategia: cerrar (FECHA_VIG_FIN + ACTIVO=0) la fila anterior y
--        crear una fila nueva con vigencia 2026-05-01 → NULL.
--        El motor en F1.3 actualizará ParametroRemunerativoService para
--        buscar la fila vigente al período consultado.
--
--   IR 4ta (C1) — Nuevos parámetros para PASO 9bis (F1.7):
--        TASA_IR4TA              = 0.08    PCT (8% sobre base)
--        BASE_INAFECTA_IR4TA     = 1500.00 PEN (base ≤ 1500 → IR4TA = 0)
--        DEDUCCION_IR4TA         = 0.20    PCT (20% deducción 4ta)
--        Estos sólo se aplicarán cuando F1.7 implemente el cálculo;
--        siembrarlos ahora deja todo listo.
--
-- ESTRUCTURA DEL SCRIPT:
--   1) UPDATE forzado RMV (1025→1130) + ASIG_FAMILIAR (102.50→113.00).
--      No es WHEN MATCHED del MERGE original porque ese estaba diseñado para
--      "no pisar valores cargados" — aquí explícitamente corregimos un seed
--      previo equivocado.
--   2) MERGE idempotente con los nuevos parámetros motor v3 (tope EsSalud,
--      IR 4ta tasa, IR 4ta base inafecta, IR 4ta deducción).
--   3) Manejo de vigencia mensual del TOPE_SEGURO_AFP: cerrar fila anterior
--      y crear la nueva mayo 2026.
--
-- NO MODIFICA: ESSALUD_MINIMO (V010_13 ya tiene 101.70, coincide con C3).
--              TASAS_AFP_*, PRIMA_AFP, escala 5ta, UIT (siguen igual).
--
-- IDEMPOTENTE: ejecutar varias veces deja el estado consistente. No hardcodea
-- schema. Verificar con SELECTs al final.
-- ============================================================================

SET SERVEROUTPUT ON;

-- ----------------------------------------------------------------------------
-- 1) Corregir RMV 2026: 1025 → 1130 (C3 RRHH).
--    Si no existe (caso raro), insertar.
-- ----------------------------------------------------------------------------
UPDATE INDECI_PARAMETRO_REMUNERATIVO
   SET VALOR_NUMERICO = 1130.00
 WHERE CODIGO_PARAMETRO    = 'RMV'
   AND ANIO_FISCAL          = 2026
   AND REGIMEN_LABORAL_ID IS NULL
   AND VALOR_NUMERICO       <> 1130.00;

INSERT INTO INDECI_PARAMETRO_REMUNERATIVO
       (CODIGO_PARAMETRO, ANIO_FISCAL, REGIMEN_LABORAL_ID, VALOR_NUMERICO, UNIDAD, FECHA_VIG_INI, ACTIVO, CREATED_AT)
SELECT 'RMV', 2026, NULL, 1130.00, 'PEN', DATE '2026-01-01', 1, SYSTIMESTAMP
  FROM DUAL
 WHERE NOT EXISTS (
        SELECT 1 FROM INDECI_PARAMETRO_REMUNERATIVO
         WHERE CODIGO_PARAMETRO = 'RMV'
           AND ANIO_FISCAL       = 2026
           AND REGIMEN_LABORAL_ID IS NULL
       );

-- ----------------------------------------------------------------------------
-- 2) Corregir ASIG_FAMILIAR 2026: 102.50 → 113.00 (10% RMV con RMV=1130, C3).
-- ----------------------------------------------------------------------------
UPDATE INDECI_PARAMETRO_REMUNERATIVO
   SET VALOR_NUMERICO = 113.00
 WHERE CODIGO_PARAMETRO    = 'ASIG_FAMILIAR'
   AND ANIO_FISCAL          = 2026
   AND REGIMEN_LABORAL_ID IS NULL
   AND VALOR_NUMERICO       <> 113.00;

INSERT INTO INDECI_PARAMETRO_REMUNERATIVO
       (CODIGO_PARAMETRO, ANIO_FISCAL, REGIMEN_LABORAL_ID, VALOR_NUMERICO, UNIDAD, FECHA_VIG_INI, ACTIVO, CREATED_AT)
SELECT 'ASIG_FAMILIAR', 2026, NULL, 113.00, 'PEN', DATE '2026-01-01', 1, SYSTIMESTAMP
  FROM DUAL
 WHERE NOT EXISTS (
        SELECT 1 FROM INDECI_PARAMETRO_REMUNERATIVO
         WHERE CODIGO_PARAMETRO = 'ASIG_FAMILIAR'
           AND ANIO_FISCAL       = 2026
           AND REGIMEN_LABORAL_ID IS NULL
       );

-- ----------------------------------------------------------------------------
-- 3) Parámetros NUEVOS del Motor v3 (tope EsSalud 45% UIT + IR 4ta).
--    REGIMEN_LABORAL_ID NULL → parámetros globales; el motor aplica por código.
--    Idempotente (MERGE WHEN NOT MATCHED).
-- ----------------------------------------------------------------------------
MERGE INTO INDECI_PARAMETRO_REMUNERATIVO d
USING (
    -- C2 RRHH: tope EsSalud 45% UIT (motor lo aplica SOLO si régimen = CAS).
    SELECT 'TOPE_ESSALUD_PCT_UIT' AS CODIGO_PARAMETRO, 2026 AS ANIO_FISCAL, 0.450000 AS VALOR_NUMERICO, 'PCT' AS UNIDAD FROM DUAL UNION ALL
    -- C1 RRHH: parámetros IR 4ta categoría (motor PASO 9bis F1.7).
    SELECT 'TASA_IR4TA',                                2026,                0.080000,                   'PCT'             FROM DUAL UNION ALL
    SELECT 'BASE_INAFECTA_IR4TA',                       2026,                1500.000000,                'PEN'             FROM DUAL UNION ALL
    SELECT 'DEDUCCION_IR4TA',                           2026,                0.200000,                   'PCT'             FROM DUAL
) s
ON (
    d.CODIGO_PARAMETRO   = s.CODIGO_PARAMETRO
    AND d.ANIO_FISCAL    = s.ANIO_FISCAL
    AND d.REGIMEN_LABORAL_ID IS NULL
)
WHEN NOT MATCHED THEN INSERT (
    CODIGO_PARAMETRO, ANIO_FISCAL, REGIMEN_LABORAL_ID, VALOR_NUMERICO, UNIDAD, FECHA_VIG_INI, ACTIVO, CREATED_AT
) VALUES (
    s.CODIGO_PARAMETRO, s.ANIO_FISCAL, NULL, s.VALOR_NUMERICO, s.UNIDAD, DATE '2026-01-01', 1, SYSTIMESTAMP
);

-- ----------------------------------------------------------------------------
-- 4) C4 RRHH — Tope prima seguro AFP parametrizable por período.
--    Cerrar fila vieja (12209.11 con vigencia ene→sin fin) y abrir nueva
--    para mayo 2026 = 12598.91.
--
--    Estrategia: marcar la vieja FECHA_VIG_FIN = 2026-04-30 + ACTIVO = 0
--    (así el service actual sin lógica de fecha sólo ve la fila activa nueva).
--    F1.3 actualizará el service para leer por fecha de devengue real, y
--    se podrá reactivar la fila vieja como histórico (ACTIVO = 1 con vigencia
--    cerrada).
--
--    Idempotente:
--      - Si la fila vieja ya está cerrada → UPDATE no hace nada.
--      - Si la fila nueva ya existe → INSERT con NOT EXISTS la salta.
-- ----------------------------------------------------------------------------
UPDATE INDECI_PARAMETRO_REMUNERATIVO
   SET FECHA_VIG_FIN = DATE '2026-04-30',
       ACTIVO        = 0
 WHERE CODIGO_PARAMETRO     = 'TOPE_SEGURO_AFP'
   AND ANIO_FISCAL          = 2026
   AND REGIMEN_LABORAL_ID  IS NULL
   AND FECHA_VIG_INI        = DATE '2026-01-01'
   AND FECHA_VIG_FIN       IS NULL;

INSERT INTO INDECI_PARAMETRO_REMUNERATIVO
       (CODIGO_PARAMETRO, ANIO_FISCAL, REGIMEN_LABORAL_ID, VALOR_NUMERICO, UNIDAD, FECHA_VIG_INI, FECHA_VIG_FIN, ACTIVO, CREATED_AT)
SELECT 'TOPE_SEGURO_AFP', 2026, NULL, 12598.91, 'PEN', DATE '2026-05-01', NULL, 1, SYSTIMESTAMP
  FROM DUAL
 WHERE NOT EXISTS (
        SELECT 1 FROM INDECI_PARAMETRO_REMUNERATIVO
         WHERE CODIGO_PARAMETRO   = 'TOPE_SEGURO_AFP'
           AND ANIO_FISCAL        = 2026
           AND REGIMEN_LABORAL_ID IS NULL
           AND FECHA_VIG_INI      = DATE '2026-05-01'
       );

COMMIT;

-- ----------------------------------------------------------------------------
-- 5) Verificación rápida — útil para revisar manualmente tras ejecución.
--    No falla nada si las filas no están: sólo imprime el estado.
-- ----------------------------------------------------------------------------
BEGIN
    FOR r IN (
        SELECT CODIGO_PARAMETRO, VALOR_NUMERICO, UNIDAD, FECHA_VIG_INI, FECHA_VIG_FIN, ACTIVO
          FROM INDECI_PARAMETRO_REMUNERATIVO
         WHERE CODIGO_PARAMETRO IN (
                'RMV', 'ASIG_FAMILIAR',
                'TOPE_ESSALUD_PCT_UIT',
                'TASA_IR4TA', 'BASE_INAFECTA_IR4TA', 'DEDUCCION_IR4TA',
                'TOPE_SEGURO_AFP'
               )
           AND ANIO_FISCAL = 2026
         ORDER BY CODIGO_PARAMETRO, FECHA_VIG_INI
    ) LOOP
        DBMS_OUTPUT.PUT_LINE(
            RPAD(r.CODIGO_PARAMETRO, 25) ||
            ' | ' || RPAD(TO_CHAR(r.VALOR_NUMERICO, 'FM999999990.000000'), 18) ||
            ' | ' || RPAD(NVL(r.UNIDAD, '-'), 4) ||
            ' | ' || TO_CHAR(r.FECHA_VIG_INI, 'YYYY-MM-DD') ||
            ' → ' || NVL(TO_CHAR(r.FECHA_VIG_FIN, 'YYYY-MM-DD'), '   abierto ') ||
            ' | ACTIVO=' || r.ACTIVO
        );
    END LOOP;
    DBMS_OUTPUT.PUT_LINE('V010_37 listo.');
END;
/
