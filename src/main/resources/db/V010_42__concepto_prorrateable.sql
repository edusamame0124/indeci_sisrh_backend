-- ============================================================================
-- F1.5b / V010_42 — INDECI_CONCEPTO_PLANILLA: ES_PRORRATEABLE + seeds
--                   normativos (decisión RRHH 2026-05-31, Convenios Colectivos
--                   Centralizados + Ley 25129).
--
-- DECISIONES NORMATIVAS (RRHH 2026-05-31):
--
--   Mapeo de regímenes para los Decretos Supremos de aumento por pacto
--   colectivo centralizado MEF. NINGUNO aplica al régimen 276 (que tiene
--   su propia estructura vía MUC en AIRHSP):
--
--     DS 311-2022-EF  → '728,1057'
--     DS 313-2023-EF  → '728,1057'
--     DS 265-2024-EF  → '728,1057'  (CODIGO_MEF 11051 — V010_23)
--     DS 279-2024-EF  → '728,1057'  (CODIGO_MEF 11053 — V010_23)
--     DS 327-2025-EF  → '728,1057'  (CODIGO_MEF 11214 — V010_23)
--
--   Asignación familiar (Ley 25129) → '728' exclusivo.
--   Asignación familiar CAS interna → '1057'.
--
-- ALCANCE:
--   - Agrega 1 columna nueva: ES_PRORRATEABLE VARCHAR2(1) DEFAULT 'N' NOT NULL.
--     Las 3 columnas que el SPEC propuso (REGIMEN_APLICABLE, FECHA_VIG_INI,
--     FECHA_VIG_FIN) YA EXISTEN en INDECI_CONCEPTO_PLANILLA — solo no se
--     usaban antes. F1.5b las activa.
--   - Dropea el CK rígido INDECI_CONCEPTO_REGIMEN_CK preexistente (V010_02)
--     que solo permitía valores únicos en REGIMEN_APLICABLE. Es necesario
--     para admitir el formato CSV ('728,1057') del mapeo normativo.
--     Validación queda en aplicación (helper regimenAplicaConcepto).
--   - UPDATE de los conceptos DS y asignaciones familiares para fijar
--     ES_PRORRATEABLE='S' y REGIMEN_APLICABLE conforme al mapeo normativo.
--   - Soporte para REGIMEN_APLICABLE en formato CSV ('728,1057'). El motor
--     parsea en runtime y verifica si el régimen del empleado pertenece.
--
-- IDEMPOTENTE: ALTER condicional (USER_TAB_COLUMNS). UPDATE deja el estado
-- canónico cada vez (sobrescribe valores anteriores SOLO para los conceptos
-- listados). No afecta conceptos fuera del set.
--
-- Schema NO hardcodeado. Oracle 19c+.
-- ============================================================================

SET SERVEROUTPUT ON;

-- ----------------------------------------------------------------------------
-- 1) Agregar ES_PRORRATEABLE si no existe.
-- ----------------------------------------------------------------------------
DECLARE
    v_exists NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_exists
      FROM USER_TAB_COLUMNS
     WHERE TABLE_NAME = 'INDECI_CONCEPTO_PLANILLA'
       AND COLUMN_NAME = 'ES_PRORRATEABLE';

    IF v_exists = 0 THEN
        EXECUTE IMMEDIATE
            'ALTER TABLE INDECI_CONCEPTO_PLANILLA '
            || 'ADD ES_PRORRATEABLE VARCHAR2(1 CHAR) DEFAULT ''N'' NOT NULL';
        DBMS_OUTPUT.PUT_LINE('ES_PRORRATEABLE -> agregada con default N.');
    ELSE
        DBMS_OUTPUT.PUT_LINE('ES_PRORRATEABLE ya existe. Sin cambios.');
    END IF;

    -- CK opcional para garantizar 'S' o 'N' (idempotente).
    SELECT COUNT(*) INTO v_exists
      FROM USER_CONSTRAINTS
     WHERE CONSTRAINT_NAME = 'INDECI_CONCEPTO_PRORR_CK';
    IF v_exists = 0 THEN
        EXECUTE IMMEDIATE
            'ALTER TABLE INDECI_CONCEPTO_PLANILLA '
            || 'ADD CONSTRAINT INDECI_CONCEPTO_PRORR_CK '
            || 'CHECK (ES_PRORRATEABLE IN (''S'', ''N''))';
        DBMS_OUTPUT.PUT_LINE('CK INDECI_CONCEPTO_PRORR_CK -> creado.');
    END IF;

    EXECUTE IMMEDIATE 'COMMENT ON COLUMN INDECI_CONCEPTO_PLANILLA.ES_PRORRATEABLE IS '
        || '''F1.5b — S/N. Si S, el motor v3 (motor.v3.prorrateo.enabled=true) prorratea el monto del EmpleadoConcepto por días laborados.''';
END;
/

-- ----------------------------------------------------------------------------
-- 1.5) Relajar CK rígido sobre REGIMEN_APLICABLE si está presente.
--
--      INDECI_CONCEPTO_REGIMEN_CK (V010_02) solo permite valores únicos
--      ('276'|'728'|'1057'|'SERVIR'|'TODOS'). Eso bloquea los UPDATEs de
--      este script que requieren CSV '728,1057' (DS de pacto colectivo MEF
--      aplican simultáneamente a 728 y 1057).
--
--      Estrategia: dropear el CK si existe. La validación de formato y de
--      códigos de régimen vive en aplicación (regimenAplicaConcepto del
--      GeneradorPlanillaService F1.5b). Idempotente: si ya está dropeado
--      (porque V010_44 corrió antes), no hace nada.
-- ----------------------------------------------------------------------------
DECLARE
    v_exists NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_exists
      FROM USER_CONSTRAINTS
     WHERE CONSTRAINT_NAME = 'INDECI_CONCEPTO_REGIMEN_CK';

    IF v_exists = 0 THEN
        DBMS_OUTPUT.PUT_LINE('INDECI_CONCEPTO_REGIMEN_CK no existe. Sin cambios.');
    ELSE
        EXECUTE IMMEDIATE
            'ALTER TABLE INDECI_CONCEPTO_PLANILLA '
            || 'DROP CONSTRAINT INDECI_CONCEPTO_REGIMEN_CK';
        DBMS_OUTPUT.PUT_LINE(
            'INDECI_CONCEPTO_REGIMEN_CK -> dropeado (CK rigido bloqueaba CSV 728,1057).'
        );
    END IF;
END;
/

-- ----------------------------------------------------------------------------
-- 2) UPDATE seeds — Decretos Supremos por pacto colectivo MEF (régimen
--    '728,1057', ES_PRORRATEABLE='S'). UPDATEs separados para que cada uno
--    sea independiente (un fallo no aborta los demás).
--
--    NOTA: solo se actualizan filas que existan. Si DS 311 / 313 no están
--    sembrados, el UPDATE no afecta filas — RRHH los puede crear luego con
--    los valores correctos vía pantalla de conceptos.
-- ----------------------------------------------------------------------------

-- DS 311-2022-EF (si existe en catálogo). No tiene CODIGO_MEF oficial sembrado
-- todavía; se busca por nombre.
UPDATE INDECI_CONCEPTO_PLANILLA
   SET ES_PRORRATEABLE   = 'S',
       REGIMEN_APLICABLE = '728,1057'
 WHERE UPPER(NOMBRE) LIKE '%DS 311-2022%'
    OR UPPER(NOMBRE) LIKE '%D.S. 311-2022%';

-- DS 313-2023-EF (idem — no tiene CODIGO_MEF oficial sembrado).
UPDATE INDECI_CONCEPTO_PLANILLA
   SET ES_PRORRATEABLE   = 'S',
       REGIMEN_APLICABLE = '728,1057'
 WHERE UPPER(NOMBRE) LIKE '%DS 313-2023%'
    OR UPPER(NOMBRE) LIKE '%D.S. 313-2023%';

-- DS 265-2024-EF — CODIGO_MEF '11051' (V010_23).
UPDATE INDECI_CONCEPTO_PLANILLA
   SET ES_PRORRATEABLE   = 'S',
       REGIMEN_APLICABLE = '728,1057'
 WHERE CODIGO_MEF = '11051';

-- DS 279-2024-EF — CODIGO_MEF '11053' (V010_23).
UPDATE INDECI_CONCEPTO_PLANILLA
   SET ES_PRORRATEABLE   = 'S',
       REGIMEN_APLICABLE = '728,1057'
 WHERE CODIGO_MEF = '11053';

-- DS 327-2025-EF — CODIGO_MEF '11214' (V010_23).
UPDATE INDECI_CONCEPTO_PLANILLA
   SET ES_PRORRATEABLE   = 'S',
       REGIMEN_APLICABLE = '728,1057'
 WHERE CODIGO_MEF = '11214';

-- ----------------------------------------------------------------------------
-- 3) UPDATE seeds — Asignaciones familiares (Ley 25129 + asig.fam. CAS interna).
-- ----------------------------------------------------------------------------

-- Asignación Familiar régimen 728 (Ley 25129) — CODIGO_MEF '00302'.
UPDATE INDECI_CONCEPTO_PLANILLA
   SET ES_PRORRATEABLE   = 'S',
       REGIMEN_APLICABLE = '728'
 WHERE CODIGO_MEF = '00302';

-- Asignación Familiar CAS — CODIGO_MEF '00502'.
UPDATE INDECI_CONCEPTO_PLANILLA
   SET ES_PRORRATEABLE   = 'S',
       REGIMEN_APLICABLE = '1057'
 WHERE CODIGO_MEF = '00502';

-- ----------------------------------------------------------------------------
-- 4) Pago Diferencial (059) — CODIGO_MEF '110412' (V010_24) — régimen SERVIR.
--    Es remunerativo mensual SERVIR; también se prorratea por días laborados.
-- ----------------------------------------------------------------------------
UPDATE INDECI_CONCEPTO_PLANILLA
   SET ES_PRORRATEABLE   = 'S',
       REGIMEN_APLICABLE = 'SERVIR'
 WHERE CODIGO_MEF = '110412';

COMMIT;

-- ----------------------------------------------------------------------------
-- 5) Verificación rápida — lista los conceptos prorrateables con su régimen.
-- ----------------------------------------------------------------------------
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count
      FROM INDECI_CONCEPTO_PLANILLA
     WHERE ES_PRORRATEABLE = 'S';

    DBMS_OUTPUT.PUT_LINE('---');
    DBMS_OUTPUT.PUT_LINE('Conceptos prorrateables: ' || v_count);
    DBMS_OUTPUT.PUT_LINE('---');

    FOR r IN (
        SELECT CODIGO_MEF, NOMBRE, REGIMEN_APLICABLE
          FROM INDECI_CONCEPTO_PLANILLA
         WHERE ES_PRORRATEABLE = 'S'
         ORDER BY REGIMEN_APLICABLE, CODIGO_MEF
    ) LOOP
        DBMS_OUTPUT.PUT_LINE(
            RPAD(NVL(r.CODIGO_MEF, '-'), 8) ||
            ' | ' || RPAD(NVL(r.REGIMEN_APLICABLE, '-'), 12) ||
            ' | ' || r.NOMBRE
        );
    END LOOP;
    DBMS_OUTPUT.PUT_LINE('V010_42 listo.');
END;
/
