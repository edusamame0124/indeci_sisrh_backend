-- ============================================================================
-- SPEC_CONCEPTOS_PLANILLA §15 / V010_107 — ¿Se incluye en planilla de pago?
--
-- Persiste la respuesta del wizard (pestaña Aplicabilidad):
--   'S' = el concepto se incluirá en una planilla de pago institucional (≥1 planilla).
--   'N' = solo configuración / cálculo / control (0 planillas).
--
-- Antes era derivado (0 planillas = "No"); ahora se guarda explícito para
-- reportes y para distinguir intención. Backfill coherente con las asociaciones
-- existentes en INDECI_CONCEPTO_PLANILLA_TIPO.
--
-- IDEMPOTENTE: solo añade la columna si falta. Solo datos (sin TABLESPACE).
-- Ejecutar en GESTIONRRHH / Oracle 19c+ (después de V010_102 y V010_105).
-- ============================================================================

SET SERVEROUTPUT ON;

DECLARE
    v_exists NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_exists
      FROM ALL_TAB_COLUMNS
     WHERE OWNER = 'GESTIONRRHH'
       AND TABLE_NAME = 'INDECI_CONCEPTO_PLANILLA'
       AND COLUMN_NAME = 'INCLUYE_EN_PLANILLA';

    IF v_exists = 0 THEN
        EXECUTE IMMEDIATE
            'ALTER TABLE GESTIONRRHH.INDECI_CONCEPTO_PLANILLA '
            || 'ADD (INCLUYE_EN_PLANILLA VARCHAR2(1 CHAR) DEFAULT ''S'' NOT NULL)';
        DBMS_OUTPUT.PUT_LINE('INCLUYE_EN_PLANILLA -> añadida (DEFAULT ''S'').');

        -- Backfill: 'N' para los conceptos SIN ninguna planilla asociada; 'S' resto.
        EXECUTE IMMEDIATE
            'UPDATE GESTIONRRHH.INDECI_CONCEPTO_PLANILLA c '
            || 'SET c.INCLUYE_EN_PLANILLA = '
            || 'CASE WHEN EXISTS (SELECT 1 FROM GESTIONRRHH.INDECI_CONCEPTO_PLANILLA_TIPO t '
            || '                   WHERE t.CONCEPTO_PLANILLA_ID = c.ID) THEN ''S'' ELSE ''N'' END';
        DBMS_OUTPUT.PUT_LINE('Backfill INCLUYE_EN_PLANILLA aplicado.');
        COMMIT;
    ELSE
        DBMS_OUTPUT.PUT_LINE('INCLUYE_EN_PLANILLA ya existe. Sin cambios.');
    END IF;

    EXECUTE IMMEDIATE
        'COMMENT ON COLUMN GESTIONRRHH.INDECI_CONCEPTO_PLANILLA.INCLUYE_EN_PLANILLA IS '
        || '''¿Se incluye en planilla de pago? S=sí (≥1 planilla) / N=solo configuración/cálculo/control.''';

    -- Guarda de dominio (S/N). Idempotente: ignora si ya existe.
    BEGIN
        EXECUTE IMMEDIATE
            'ALTER TABLE GESTIONRRHH.INDECI_CONCEPTO_PLANILLA '
            || 'ADD CONSTRAINT INDECI_CONCEPTO_PLANILLA_INCL_CK CHECK (INCLUYE_EN_PLANILLA IN (''S'',''N''))';
        DBMS_OUTPUT.PUT_LINE('CHECK INCLUYE_EN_PLANILLA (S/N) añadido.');
    EXCEPTION
        WHEN OTHERS THEN
            IF SQLCODE = -2264 OR SQLCODE = -2275 OR SQLCODE = -1430 THEN
                DBMS_OUTPUT.PUT_LINE('CHECK INCLUYE_EN_PLANILLA ya existe. Sin cambios.');
            ELSE
                RAISE;
            END IF;
    END;
END;
/
