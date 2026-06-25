-- ============================================================================
-- SPEC_HOMOLOGACION_MGRH §C / V010_106 — Observación interna de homologación MGRH
--
-- Campo libre opcional para que RR.HH. anote el sustento de la homologación
-- (ej. "Homologación validada con especialista de planillas"). Nullable; no
-- afecta el motor ni bloquea. Solo metadata de la pestaña "Homologación MGRH/MEF".
--
-- IDEMPOTENTE: solo añade la columna si falta. Solo datos (sin TABLESPACE).
-- Ejecutar en GESTIONRRHH / Oracle 19c+ (después de V010_103).
-- ============================================================================

SET SERVEROUTPUT ON;

DECLARE
    v_exists NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_exists
      FROM ALL_TAB_COLUMNS
     WHERE OWNER = 'GESTIONRRHH'
       AND TABLE_NAME = 'INDECI_CONCEPTO_PLANILLA'
       AND COLUMN_NAME = 'OBSERVACION_HOMOLOGACION_MGRH';

    IF v_exists = 0 THEN
        EXECUTE IMMEDIATE
            'ALTER TABLE GESTIONRRHH.INDECI_CONCEPTO_PLANILLA '
            || 'ADD (OBSERVACION_HOMOLOGACION_MGRH VARCHAR2(300 CHAR))';
        DBMS_OUTPUT.PUT_LINE('OBSERVACION_HOMOLOGACION_MGRH -> añadida.');
    ELSE
        DBMS_OUTPUT.PUT_LINE('OBSERVACION_HOMOLOGACION_MGRH ya existe. Sin cambios.');
    END IF;

    EXECUTE IMMEDIATE
        'COMMENT ON COLUMN GESTIONRRHH.INDECI_CONCEPTO_PLANILLA.OBSERVACION_HOMOLOGACION_MGRH IS '
        || '''Observación interna libre de la homologación MGRH/MEF (opcional). No afecta el cálculo.''';
END;
/
