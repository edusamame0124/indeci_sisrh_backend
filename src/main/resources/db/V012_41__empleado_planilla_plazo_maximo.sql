-- ============================================================================
-- V012_41 — Columna PLAZO_MAXIMO en INDECI_EMPLEADO_PLANILLA
--
-- OBJETIVO:
--   Guardar el aviso normativo del vínculo que RR.HH. agregó como columna
--   "PLAZO MAXIMO" en el Excel de import de Vinculación (p. ej. "DURACION DE 5
--   AÑOS MAXIMO"). Es texto libre; la pantalla de Configuración Remunerativa lo
--   muestra como label de advertencia debajo de "Tipo de contrato CAS".
--
-- DECISIONES:
--   - Idempotente: se agrega solo si la columna no existe (ALTER ADD).
--   - VARCHAR2(200): holgura para el texto del aviso.
--
-- Ejecutar en esquema GESTIONRRHH / Oracle 19c+.
-- ============================================================================
SET SERVEROUTPUT ON;

DECLARE
    v_exists NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_exists
      FROM all_tab_columns
     WHERE owner = 'GESTIONRRHH'
       AND table_name = 'INDECI_EMPLEADO_PLANILLA'
       AND column_name = 'PLAZO_MAXIMO';

    IF v_exists = 0 THEN
        EXECUTE IMMEDIATE
            'ALTER TABLE GESTIONRRHH.INDECI_EMPLEADO_PLANILLA ADD (PLAZO_MAXIMO VARCHAR2(200))';
        DBMS_OUTPUT.PUT_LINE('Columna PLAZO_MAXIMO agregada.');
    ELSE
        DBMS_OUTPUT.PUT_LINE('Columna PLAZO_MAXIMO ya existe (omitida).');
    END IF;

    EXECUTE IMMEDIATE
        'COMMENT ON COLUMN GESTIONRRHH.INDECI_EMPLEADO_PLANILLA.PLAZO_MAXIMO IS '
        || '''Aviso normativo del vínculo (texto libre del Excel de import, p. ej. DURACION DE 5 AÑOS MAXIMO). Se muestra en Config. Remunerativa.''';

    DBMS_OUTPUT.PUT_LINE('V012_41 finalizado.');
END;
/
