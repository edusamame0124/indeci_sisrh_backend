-- ============================================================================
-- Spec 010 / V010_11 — Seed conceptos v2 (camino D)
--
-- CONTENIDO (en este orden):
--   1. DIAGNÓSTICO  — reporta uso de los 7 CODIGO_MEF en conflicto en
--                      INDECI_MOVIMIENTO_PLANILLA_DET e INDECI_EMPLEADO_CONCEPTO.
--                      Te dice si los UPDATEs sensibles del bloque final son
--                      seguros de descomentar o no.
--   2. MERGE INSERT — 20 conceptos NUEVOS del SPEC v2 §6.2 sin conflicto:
--                       • SERVIR (7): 00503..00509
--                       • Voluntarios (11): 05302..05312
--                       • Empleador (2): 06003, 06004
--                      Idempotente vía MERGE WHEN NOT MATCHED (sin WHEN MATCHED).
--   3. UPDATEs SEGUROS — sólo CODIGO_SISPER, sólo si actualmente es NULL.
--                       No toca NOMBRE, TIPO_CONCEPTO, REGIMEN ni AFECTO_*.
--   4. BLOQUE COMENTADO — UPDATEs propuestos para los 7 conceptos en conflicto.
--                          Descomentar SÓLO si el diagnóstico (paso 1) reporta
--                          0 referencias en movimientos y empleado_concepto.
--
-- LEY-01 (Ley 32448): CODIGO_MEF NOT NULL desde V010_06. Los nuevos vienen
-- todos con su código asignado.
-- LEY-03 (TUO LIR): SERVIR sí está afecto a 5ta categoría. Los conceptos
-- remunerativos SERVIR 00503..00507 llevan AFECTO_IR_5TA='S'. Los aguinaldos
-- SERVIR 00508/00509 quedan en 'N' (su 5ta se calcula como base BX, §5.7).
--
-- DEFENSA EN PROFUNDIDAD:
--   - Diagnóstico no modifica datos; sólo imprime conteo.
--   - MERGE-NOT-MATCHED: re-ejecutable; no pisa filas existentes.
--   - UPDATEs seguros condicionados a `CODIGO_SISPER IS NULL` (no pisan valores
--     manuales que el operador haya cargado entre ejecuciones).
--   - Bloque sensible está comentado (estilo V010_05).
--
-- Ejecutar en esquema GESTIONRRHH / Oracle 19c+.
-- ============================================================================

SET SERVEROUTPUT ON;

-- ============================================================================
-- BLOQUE PL/SQL: diagnóstico + inserts + updates seguros + reporte
-- ============================================================================
DECLARE
    v_insertados   NUMBER := 0;
    v_actualizados NUMBER := 0;
    v_total        NUMBER;
BEGIN
    -- ------------------------------------------------------------------
    -- 1. DIAGNÓSTICO DE USO — los 7 CODIGO_MEF en conflicto
    -- ------------------------------------------------------------------
    DBMS_OUTPUT.PUT_LINE('==================================================================');
    DBMS_OUTPUT.PUT_LINE('DIAGNÓSTICO — uso actual de los 7 CODIGO_MEF en conflicto');
    DBMS_OUTPUT.PUT_LINE('Si EN_MOVIMIENTO=0 y EN_EMPLEADO_CONCEPTO=0 para todos →');
    DBMS_OUTPUT.PUT_LINE('los UPDATEs del bloque comentado al final son seguros de descomentar.');
    DBMS_OUTPUT.PUT_LINE('==================================================================');
    DBMS_OUTPUT.PUT_LINE(RPAD('CODIGO_MEF', 12)
        || RPAD('NOMBRE', 42)
        || RPAD('EN_MOVIMIENTO', 16)
        || RPAD('EN_EMPLEADO_CONCEPTO', 22));
    DBMS_OUTPUT.PUT_LINE(RPAD('-', 92, '-'));

    FOR r IN (
        SELECT cp.CODIGO_MEF,
               cp.NOMBRE,
               -- Subqueries correlacionadas para evitar producto cartesiano
               (SELECT COUNT(*)
                  FROM GESTIONRRHH.INDECI_MOVIMIENTO_PLANILLA_DET d
                 WHERE d.CONCEPTO_PLANILLA_ID = cp.ID) AS EN_MOVIMIENTO,
               (SELECT COUNT(*)
                  FROM GESTIONRRHH.INDECI_EMPLEADO_CONCEPTO ec
                 WHERE ec.CONCEPTO_PLANILLA_ID = cp.ID) AS EN_EMPLEADO_CONCEPTO
          FROM GESTIONRRHH.INDECI_CONCEPTO_PLANILLA cp
         WHERE cp.CODIGO_MEF IN
               ('00501', '00502', '05201', '05301', '05401', '05402', '06002')
         ORDER BY cp.CODIGO_MEF
    ) LOOP
        DBMS_OUTPUT.PUT_LINE(
            RPAD(r.CODIGO_MEF, 12)
            || RPAD(SUBSTR(r.NOMBRE, 1, 40), 42)
            || RPAD(TO_CHAR(r.EN_MOVIMIENTO), 16)
            || RPAD(TO_CHAR(r.EN_EMPLEADO_CONCEPTO), 22));
    END LOOP;

    DBMS_OUTPUT.PUT_LINE(RPAD('-', 92, '-'));
    DBMS_OUTPUT.PUT_LINE('');

    -- ------------------------------------------------------------------
    -- 2. MERGE INSERT — 20 conceptos nuevos del SPEC v2 §6.2
    -- ------------------------------------------------------------------
    -- Patrón: SELECT ... FROM DUAL UNION ALL ... + WHEN NOT MATCHED THEN INSERT.
    -- Sin cláusula WHEN MATCHED → idempotente y NO toca conceptos existentes.
    -- Columnas del SELECT (en orden):
    --   CODIGO_MEF, CODIGO_SISPER, NOMBRE, TIPO_CONCEPTO,
    --   AFECTO_IR_5TA, AFECTO_APORTE_PENS, AFECTO_ESSALUD,
    --   ES_MUC, ES_CUC, REGIMEN_APLICABLE

    MERGE INTO GESTIONRRHH.INDECI_CONCEPTO_PLANILLA d
    USING (
        -- ============ SERVIR (7) — remunerativos afectos a 5ta (LEY-03) ============
        SELECT '00503' AS CODIGO_MEF, '072' AS CODIGO_SISPER, 'Incremento DS 279-2024-EF'   AS NOMBRE, 'REMUNERATIVO'    AS TIPO_CONCEPTO, 'S' AS AFECTO_IR_5TA, 'S' AS AFECTO_APORTE_PENS, 'S' AS AFECTO_ESSALUD, 'N' AS ES_MUC, 'N' AS ES_CUC, 'SERVIR' AS REGIMEN_APLICABLE FROM DUAL UNION ALL
        SELECT '00504', 'xxx', 'Incremento DS 327-2025-EF',     'REMUNERATIVO',    'S', 'S', 'S', 'N', 'N', 'SERVIR' FROM DUAL UNION ALL
        SELECT '00505', '059', 'Pago Diferencial (059)',        'REMUNERATIVO',    'S', 'S', 'S', 'N', 'N', 'SERVIR' FROM DUAL UNION ALL
        SELECT '00506', '060', 'Pago Diferencial (060)',        'REMUNERATIVO',    'S', 'S', 'S', 'N', 'N', 'SERVIR' FROM DUAL UNION ALL
        SELECT '00507', '041', 'Reintegro SISPER-041',          'REMUNERATIVO',    'S', 'S', 'S', 'N', 'N', 'SERVIR' FROM DUAL UNION ALL
        SELECT '00508', NULL,  'Aguinaldo Julio SERVIR',        'NO_REMUNERATIVO', 'N', 'S', 'N', 'N', 'N', 'SERVIR' FROM DUAL UNION ALL
        SELECT '00509', NULL,  'Aguinaldo Diciembre SERVIR',    'NO_REMUNERATIVO', 'N', 'S', 'N', 'N', 'N', 'SERVIR' FROM DUAL UNION ALL
        -- ============ DESCUENTOS VOLUNTARIOS (11) — 05301 queda en conflicto ============
        SELECT '05302', '722', 'Campaña Navarrete',             'DESCUENTO', 'N', 'N', 'N', 'N', 'N', 'TODOS' FROM DUAL UNION ALL
        SELECT '05303', '734', 'FESALUD S.A.',                  'DESCUENTO', 'N', 'N', 'N', 'N', 'N', 'TODOS' FROM DUAL UNION ALL
        SELECT '05304', '703', 'Inter/Sura Seguro',             'DESCUENTO', 'N', 'N', 'N', 'N', 'N', 'TODOS' FROM DUAL UNION ALL
        SELECT '05305', '715', '+Vida EsSalud',                 'DESCUENTO', 'N', 'N', 'N', 'N', 'N', 'TODOS' FROM DUAL UNION ALL
        SELECT '05306', '727', 'Coop. San Miguel',              'DESCUENTO', 'N', 'N', 'N', 'N', 'N', 'TODOS' FROM DUAL UNION ALL
        SELECT '05307', '726', 'Coop. SERFINCO',                'DESCUENTO', 'N', 'N', 'N', 'N', 'N', 'TODOS' FROM DUAL UNION ALL
        SELECT '05308', '704', 'Rimac Seguros',                 'DESCUENTO', 'N', 'N', 'N', 'N', 'N', 'TODOS' FROM DUAL UNION ALL
        SELECT '05309', '725', 'Copago EPS (trabajador)',       'DESCUENTO', 'N', 'N', 'N', 'N', 'N', 'TODOS' FROM DUAL UNION ALL
        SELECT '05310', '717', 'Otros Descuentos',              'DESCUENTO', 'N', 'N', 'N', 'N', 'N', 'TODOS' FROM DUAL UNION ALL
        SELECT '05311', '613', 'Aporte Facultativo 0.5%',       'DESCUENTO', 'N', 'N', 'N', 'N', 'N', 'TODOS' FROM DUAL UNION ALL
        SELECT '05312', '731', 'Campaña Tai Loy',               'DESCUENTO', 'N', 'N', 'N', 'N', 'N', 'TODOS' FROM DUAL UNION ALL
        -- ============ APORTE EMPLEADOR (2) ============
        SELECT '06003', '906', 'EPS 2.25% (empleador, con EPS)','APORTE_EMPLEADOR', 'N', 'N', 'N', 'N', 'N', 'TODOS' FROM DUAL UNION ALL
        SELECT '06004', NULL,  'CUC Total',                     'APORTE_EMPLEADOR', 'N', 'N', 'N', 'N', 'S', 'TODOS' FROM DUAL
    ) s
    ON ( d.CODIGO_MEF = s.CODIGO_MEF )
    WHEN NOT MATCHED THEN INSERT (
        CODIGO, CODIGO_MEF, CODIGO_SISPER, NOMBRE, TIPO,
        TIPO_CONCEPTO, AFECTO_IR_5TA, AFECTO_APORTE_PENS, AFECTO_ESSALUD,
        ES_MUC, ES_CUC, REGIMEN_APLICABLE,
        ACTIVO, CREATED_AT, FECHA_VIG_INI
    ) VALUES (
        s.CODIGO_MEF, s.CODIGO_MEF, s.CODIGO_SISPER, s.NOMBRE,
        -- TIPO legacy derivado (compat con frontend Spec 009)
        CASE
            WHEN s.TIPO_CONCEPTO IN ('REMUNERATIVO','NO_REMUNERATIVO') THEN 'INGRESO'
            WHEN s.TIPO_CONCEPTO IN ('DESCUENTO','APORTE_TRABAJADOR')   THEN 'DESCUENTO'
            ELSE 'APORTE'
        END,
        s.TIPO_CONCEPTO, s.AFECTO_IR_5TA, s.AFECTO_APORTE_PENS, s.AFECTO_ESSALUD,
        s.ES_MUC, s.ES_CUC, s.REGIMEN_APLICABLE,
        1, CURRENT_TIMESTAMP, DATE '2026-01-01'
    );
    v_insertados := SQL%ROWCOUNT;
    DBMS_OUTPUT.PUT_LINE('MERGE INSERT: ' || v_insertados || ' conceptos nuevos sembrados (0..20).');

    -- ------------------------------------------------------------------
    -- 3. UPDATEs SEGUROS — sólo CODIGO_SISPER, sólo si NULL
    --    No toca NOMBRE, TIPO_CONCEPTO, REGIMEN ni AFECTO_*.
    -- ------------------------------------------------------------------
    UPDATE GESTIONRRHH.INDECI_CONCEPTO_PLANILLA
       SET CODIGO_SISPER = CASE CODIGO_MEF
                               WHEN '05001' THEN '812'
                               WHEN '05002' THEN '816'
                               WHEN '05003' THEN '817'
                               WHEN '05004' THEN '818'
                               WHEN '05101' THEN '820'
                               WHEN '05201' THEN '716'
                               WHEN '06001' THEN '905'
                           END
     WHERE CODIGO_MEF IN ('05001', '05002', '05003', '05004', '05101', '05201', '06001')
       AND CODIGO_SISPER IS NULL;
    v_actualizados := SQL%ROWCOUNT;
    DBMS_OUTPUT.PUT_LINE('UPDATE seguro CODIGO_SISPER: ' || v_actualizados || ' filas (0..7).');

    COMMIT;

    -- ------------------------------------------------------------------
    -- 4. REPORTE FINAL — cuántos conceptos hay en BD ahora
    -- ------------------------------------------------------------------
    SELECT COUNT(*) INTO v_total
      FROM GESTIONRRHH.INDECI_CONCEPTO_PLANILLA
     WHERE ACTIVO = 1;
    DBMS_OUTPUT.PUT_LINE('');
    DBMS_OUTPUT.PUT_LINE('Total conceptos ACTIVO=1 en INDECI_CONCEPTO_PLANILLA ahora: ' || v_total);
    DBMS_OUTPUT.PUT_LINE('V010_11 finalizado.');
END;
/

-- ============================================================================
-- BLOQUE COMENTADO — UPDATEs sensibles para los 7 CODIGO_MEF en conflicto.
--
-- DESCOMENTAR SOLO SI el DIAGNÓSTICO de arriba reportó:
--   EN_MOVIMIENTO = 0 Y EN_EMPLEADO_CONCEPTO = 0
-- para el código a actualizar.
--
-- Si algún concepto tiene uso (>0 en cualquiera de las dos columnas), NO
-- descomentar su UPDATE: cambiar el NOMBRE/TIPO_CONCEPTO/REGIMEN cambia
-- retroactivamente la semántica de movimientos ya generados. En ese caso,
-- crear un concepto NUEVO con un CODIGO_MEF distinto (libre) y migrar manualmente.
-- ============================================================================

-- 00501 — V010_04: "Remuneración CAS" (1057) → SPEC v2: "Compensación Económica" (SERVIR)
-- UPDATE GESTIONRRHH.INDECI_CONCEPTO_PLANILLA
--    SET NOMBRE              = 'Compensación Económica (CE)',
--        TIPO_CONCEPTO       = 'REMUNERATIVO',
--        REGIMEN_APLICABLE   = 'SERVIR',
--        AFECTO_IR_5TA       = 'S',
--        AFECTO_APORTE_PENS  = 'S',
--        AFECTO_ESSALUD      = 'S',
--        ES_MUC              = 'N',
--        ES_CUC              = 'N'
--  WHERE CODIGO_MEF = '00501';

-- 00502 — V010_04: "Asignación Familiar CAS" (1057) → SPEC v2: "Incremento DS 265-2024-EF" (SERVIR, SISPER 071)
-- UPDATE GESTIONRRHH.INDECI_CONCEPTO_PLANILLA
--    SET NOMBRE              = 'Incremento DS 265-2024-EF',
--        CODIGO_SISPER       = '071',
--        TIPO_CONCEPTO       = 'REMUNERATIVO',
--        REGIMEN_APLICABLE   = 'SERVIR',
--        AFECTO_IR_5TA       = 'S',
--        AFECTO_APORTE_PENS  = 'S',
--        AFECTO_ESSALUD      = 'S'
--  WHERE CODIGO_MEF = '00502';

-- 05201 — V010_04: "Pensión Alimentaria" → SPEC v2: "Descuento Judicial" (genérico, SISPER 716)
-- UPDATE GESTIONRRHH.INDECI_CONCEPTO_PLANILLA
--    SET NOMBRE = 'Descuento Judicial'
--  WHERE CODIGO_MEF = '05201';
-- Nota: el CODIGO_SISPER='716' ya lo aplica el UPDATE seguro del paso 3 si está NULL.

-- 05301 — V010_04: "Cuota Préstamo Interno" → SPEC v2: "Coop. La Rehabilitadora" (voluntario, SISPER 735)
-- UPDATE GESTIONRRHH.INDECI_CONCEPTO_PLANILLA
--    SET NOMBRE          = 'Coop. La Rehabilitadora',
--        CODIGO_SISPER   = '735',
--        TIPO_CONCEPTO   = 'DESCUENTO',
--        REGIMEN_APLICABLE = 'TODOS'
--  WHERE CODIGO_MEF = '05301';

-- 05401 — V010_04: "Descuento por Tardanza" → SPEC v2: no aparece (huérfano)
-- Opción: dejar ACTIVO=1 si el motor de tardanzas (Etapa 3) lo va a reutilizar.
-- Si se decide dar de baja:
-- UPDATE GESTIONRRHH.INDECI_CONCEPTO_PLANILLA
--    SET ACTIVO = 0,
--        FECHA_VIG_FIN = SYSDATE
--  WHERE CODIGO_MEF = '05401';

-- 05402 — V010_04: "Descuento por Falta" → SPEC v2: no aparece (huérfano)
-- UPDATE GESTIONRRHH.INDECI_CONCEPTO_PLANILLA
--    SET ACTIVO = 0,
--        FECHA_VIG_FIN = SYSDATE
--  WHERE CODIGO_MEF = '05402';

-- 06002 — V010_04: "Costo Único Consolidado (CUC)" → SPEC v2: "ESSALUD 6.75% (con EPS)" (SISPER 907)
-- CONFLICTO MÁS PELIGROSO: el motor actual de Etapa 1 puede haber grabado CUC en este código.
-- Verificar EN_MOVIMIENTO con extremo cuidado antes de descomentar.
-- UPDATE GESTIONRRHH.INDECI_CONCEPTO_PLANILLA
--    SET NOMBRE          = 'ESSALUD 6.75% (con EPS)',
--        CODIGO_SISPER   = '907',
--        TIPO_CONCEPTO   = 'APORTE_EMPLEADOR',
--        ES_CUC          = 'N'
--  WHERE CODIGO_MEF = '06002';
-- (El CUC verdadero se siembra en este script como CODIGO_MEF='06004' con ES_CUC='S')

-- COMMIT manual al final, tras revisar cada UPDATE descomentado.
-- COMMIT;
