-- ============================================================================
-- V012_46 — Trazabilidad de origen (Reloj 1 / COEN) en la carga de asistencia.
--
-- Contexto: el módulo soporta dos formatos de marcador que deben CONVIVIR
-- (SPEC_ASISTENCIA_COEN.md D8): Reloj 1 (marcador clásico, con DNI) y Reloj 2 /
-- COEN (log de eventos, otra sede, identidad por alias). FormatoDetector ya
-- distingue ambos al leer el archivo, pero el dato se descartaba: no quedaba
-- registrado en qué formato se cargó cada importación, ni se podía saber si
-- una "rectificación" de un empleado en realidad venía de un reloj distinto.
--
-- Este script solo agrega la columna. Filas existentes quedan NULL (no se
-- puede reconstruir el formato de una carga ya hecha sin volver a leer el
-- archivo original); el código trata NULL como "no determinado", nunca como
-- un formato real.
--
-- Idempotente. Ejecutar en GESTIONRRHH.
-- ============================================================================

SET SERVEROUTPUT ON;
SET DEFINE OFF;

DECLARE
    v_existe NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_existe
      FROM all_tab_columns
     WHERE owner = 'GESTIONRRHH'
       AND table_name = 'INDECI_ASISTENCIA_IMPORTACION'
       AND column_name = 'FORMATO_ORIGEN';

    IF v_existe = 0 THEN
        EXECUTE IMMEDIATE '
            ALTER TABLE GESTIONRRHH.INDECI_ASISTENCIA_IMPORTACION
            ADD FORMATO_ORIGEN VARCHAR2(20)';

        EXECUTE IMMEDIATE '
            ALTER TABLE GESTIONRRHH.INDECI_ASISTENCIA_IMPORTACION
            ADD CONSTRAINT INDECI_ASIST_IMPORT_FORMATO_CK
            CHECK (FORMATO_ORIGEN IN (''RELOJ1_DIARIO'', ''RELOJ2_COEN''))';

        DBMS_OUTPUT.PUT_LINE('V012_46: columna FORMATO_ORIGEN agregada.');
    ELSE
        DBMS_OUTPUT.PUT_LINE('V012_46: la columna FORMATO_ORIGEN ya existía — nada que hacer.');
    END IF;
END;
/

PROMPT V012_46 — trazabilidad de origen (Reloj 1 / COEN) aplicada.
