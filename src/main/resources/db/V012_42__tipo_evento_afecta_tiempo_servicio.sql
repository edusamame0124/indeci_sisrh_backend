-- ============================================================================
-- V012_42 — Columna AFECTA_TIEMPO_SERVICIO en INDECI_TIPO_EVENTO
--
-- OBJETIVO:
--   Desacoplar el efecto de un TipoEvento sobre la PLANILLA (AFECTA_DIAS_LABORADOS,
--   ya existente) del efecto sobre el TIEMPO DE SERVICIO / RECORD VACACIONAL
--   (AFECTA_TIEMPO_SERVICIO, nueva). Necesario porque el importador histórico
--   (Excel "DEDUCCIONES DEL TIEMPO DE SERVICIOS") va a sembrar eventos con fechas
--   recientes (algunas dentro de 2026) que NO deben volver a descontar días de
--   planilla ya calculados por Asistencia, pero SI deben descontar el récord
--   vacacional / tiempo de servicio.
--
-- DECISIONES:
--   - Idempotente: ALTER ADD solo si la columna no existe.
--   - Backfill: AFECTA_TIEMPO_SERVICIO = AFECTA_DIAS_LABORADOS para TODAS las filas
--     existentes. Esto preserva 1:1 el comportamiento actual del récord vacacional
--     (hoy EventosIncidenciaProvider filtra por AFECTA_DIAS_LABORADOS='S'; después
--     de este cambio filtra por AFECTA_TIEMPO_SERVICIO='S') — CERO regresión.
--   - Nuevos tipos de catálogo para el importador histórico (Día 0):
--       FALTA_HISTORICA       — Excel "INASISTENCIA INJUSTIFICADA".
--       SUSPENSION_HISTORICA  — Excel "SUSPENSION PAD" y "SANCION PAD" (agrupados;
--         decisión RR.HH.: una sanción disciplinaria de cese temporal se ejecuta en
--         la práctica como suspensión sin goce — mismo código, "SANCION PAD" queda
--         trazado en OBSERVACION junto al N° de resolución).
--     Ambos con AFECTA_DIAS_LABORADOS='N' (no tocan planilla — esos días ya se
--     liquidaron o son anteriores al sistema) y AFECTA_TIEMPO_SERVICIO='S' (sí
--     descuentan récord/tiempo de servicio).
--
-- Ejecutar en esquema GESTIONRRHH / Oracle 19c+.
-- ============================================================================
SET SERVEROUTPUT ON;

-- ----------------------------------------------------------------------------
-- 1) Columna nueva + backfill.
-- ----------------------------------------------------------------------------
DECLARE
    v_exists NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_exists
      FROM all_tab_columns
     WHERE owner = 'GESTIONRRHH'
       AND table_name = 'INDECI_TIPO_EVENTO'
       AND column_name = 'AFECTA_TIEMPO_SERVICIO';

    IF v_exists = 0 THEN
        EXECUTE IMMEDIATE
            'ALTER TABLE GESTIONRRHH.INDECI_TIPO_EVENTO ADD (AFECTA_TIEMPO_SERVICIO VARCHAR2(1 CHAR) DEFAULT ''N'' NOT NULL)';
        DBMS_OUTPUT.PUT_LINE('Columna AFECTA_TIEMPO_SERVICIO agregada.');

        -- Backfill 1:1 con AFECTA_DIAS_LABORADOS — preserva el comportamiento actual
        -- del récord vacacional (cero regresión sobre Padrón/LBS/Acumulación).
        EXECUTE IMMEDIATE
            'UPDATE GESTIONRRHH.INDECI_TIPO_EVENTO SET AFECTA_TIEMPO_SERVICIO = AFECTA_DIAS_LABORADOS';
        DBMS_OUTPUT.PUT_LINE('Backfill AFECTA_TIEMPO_SERVICIO = AFECTA_DIAS_LABORADOS aplicado.');
    ELSE
        DBMS_OUTPUT.PUT_LINE('Columna AFECTA_TIEMPO_SERVICIO ya existe (omitida).');
    END IF;

    EXECUTE IMMEDIATE
        'COMMENT ON COLUMN GESTIONRRHH.INDECI_TIPO_EVENTO.AFECTA_TIEMPO_SERVICIO IS '
        || '''Descuenta tiempo de servicio / récord vacacional (EventosIncidenciaProvider). '
        || 'Independiente de AFECTA_DIAS_LABORADOS (planilla) para que el importador histórico '
        || 'no reabra el cálculo de días de boletas ya emitidas.''';
END;
/

-- ----------------------------------------------------------------------------
-- 2) CHECK constraint (idempotente).
-- ----------------------------------------------------------------------------
DECLARE
    v_exists NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_exists
      FROM user_constraints
     WHERE constraint_name = 'INDECI_TIPO_EVENTO_AFEC_TS_CK';

    IF v_exists = 0 THEN
        EXECUTE IMMEDIATE
            'ALTER TABLE GESTIONRRHH.INDECI_TIPO_EVENTO '
            || 'ADD CONSTRAINT INDECI_TIPO_EVENTO_AFEC_TS_CK CHECK (AFECTA_TIEMPO_SERVICIO IN (''S'',''N''))';
        DBMS_OUTPUT.PUT_LINE('CHECK INDECI_TIPO_EVENTO_AFEC_TS_CK agregado.');
    ELSE
        DBMS_OUTPUT.PUT_LINE('CHECK INDECI_TIPO_EVENTO_AFEC_TS_CK ya existe (omitido).');
    END IF;
END;
/

-- ----------------------------------------------------------------------------
-- 3) Seed catálogo — tipos de evento "histórico migrado" (idempotente, MERGE).
--    AFECTA_DIAS_LABORADOS='N' (no tocan planilla), AFECTA_TIEMPO_SERVICIO='S'
--    (sí descuentan récord/tiempo de servicio). AFECTA_BASE_AFP/ESSALUD='S'
--    (neutro: no hay cálculo de planilla que los lea, dado AFECTA_DIAS_LABORADOS='N').
--    REQUIERE_ADJUNTO='S' — coherente con el resto del catálogo (licencias/suspensiones
--    exigen sustento); el importador histórico no pasa por esta validación (inserta
--    directo, igual que la materialización de LSG desde papeleta), pero si en el
--    futuro alguien crea uno de estos tipos a mano desde el formulario, debe sustentar.
-- ----------------------------------------------------------------------------
MERGE INTO INDECI_TIPO_EVENTO d
USING (
    SELECT 'FALTA_HISTORICA'       AS CODIGO, 'Inasistencia injustificada (histórico migrado)' AS NOMBRE,
           'N' AS AFECTA_DIAS_LABORADOS, 'S' AS AFECTA_BASE_AFP, 'S' AS AFECTA_BASE_ESSALUD,
           'N' AS GENERA_SUBSIDIO, 'S' AS REQUIERE_ADJUNTO, 'N' AS PERMITE_SOLAPE,
           'S' AS AFECTA_TIEMPO_SERVICIO, 110 AS ORDEN_VISUAL FROM DUAL UNION ALL
    SELECT 'SUSPENSION_HISTORICA', 'Suspensión / sanción PAD (histórico migrado)',
           'N','S','S','N','S','N', 'S', 120 FROM DUAL
) s
ON (d.CODIGO = s.CODIGO)
WHEN NOT MATCHED THEN
    INSERT (CODIGO, NOMBRE,
            AFECTA_DIAS_LABORADOS, AFECTA_BASE_AFP, AFECTA_BASE_ESSALUD,
            GENERA_SUBSIDIO, REQUIERE_ADJUNTO, PERMITE_SOLAPE,
            AFECTA_TIEMPO_SERVICIO, ORDEN_VISUAL, ACTIVO, CREATED_AT)
    VALUES (s.CODIGO, s.NOMBRE,
            s.AFECTA_DIAS_LABORADOS, s.AFECTA_BASE_AFP, s.AFECTA_BASE_ESSALUD,
            s.GENERA_SUBSIDIO, s.REQUIERE_ADJUNTO, s.PERMITE_SOLAPE,
            s.AFECTA_TIEMPO_SERVICIO, s.ORDEN_VISUAL, 1, SYSTIMESTAMP);

COMMIT;

-- ----------------------------------------------------------------------------
-- 4) Verificación rápida.
-- ----------------------------------------------------------------------------
DECLARE
    v_col     NUMBER;
    v_check   NUMBER;
    v_tipos   NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_col
      FROM all_tab_columns
     WHERE owner = 'GESTIONRRHH' AND table_name = 'INDECI_TIPO_EVENTO'
       AND column_name = 'AFECTA_TIEMPO_SERVICIO';
    SELECT COUNT(*) INTO v_check
      FROM user_constraints
     WHERE constraint_name = 'INDECI_TIPO_EVENTO_AFEC_TS_CK';
    SELECT COUNT(*) INTO v_tipos
      FROM INDECI_TIPO_EVENTO
     WHERE CODIGO IN ('FALTA_HISTORICA','SUSPENSION_HISTORICA') AND ACTIVO = 1;

    DBMS_OUTPUT.PUT_LINE('---');
    DBMS_OUTPUT.PUT_LINE('Columna AFECTA_TIEMPO_SERVICIO : ' || v_col   || ' (esperado 1)');
    DBMS_OUTPUT.PUT_LINE('CHECK AFEC_TS_CK                : ' || v_check || ' (esperado 1)');
    DBMS_OUTPUT.PUT_LINE('Tipos histórico migrado activos : ' || v_tipos || ' (esperado 2)');
    DBMS_OUTPUT.PUT_LINE('V012_42 listo.');
END;
/
