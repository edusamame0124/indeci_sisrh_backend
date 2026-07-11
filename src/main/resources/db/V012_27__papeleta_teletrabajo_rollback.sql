-- ============================================================================
-- V012_27 ROLLBACK — Papeleta de Teletrabajo diaria
--
-- Revierte V012_27: elimina la tabla hija de actividades y el tipo TELETRABAJO.
-- Idempotente. Ejecutar en GESTIONRRHH / Oracle 19c+.
--
-- ADVERTENCIA: no ejecutar si ya existen papeletas de tipo TELETRABAJO en
-- INDECI_SOLICITUD_RRHH (perderían su detalle). Verificar antes.
-- ============================================================================

SET SERVEROUTPUT ON;

DECLARE
    v NUMBER;
BEGIN
    -- 1) Tabla hija -----------------------------------------------------------
    SELECT COUNT(*) INTO v FROM USER_TABLES
     WHERE TABLE_NAME = 'INDECI_SOLICITUD_TELETRABAJO_DET';
    IF v > 0 THEN
        EXECUTE IMMEDIATE 'DROP TABLE INDECI_SOLICITUD_TELETRABAJO_DET PURGE';
        DBMS_OUTPUT.PUT_LINE('INDECI_SOLICITUD_TELETRABAJO_DET -> eliminada.');
    ELSE
        DBMS_OUTPUT.PUT_LINE('INDECI_SOLICITUD_TELETRABAJO_DET no existe.');
    END IF;

    -- 2) Tipo TELETRABAJO -----------------------------------------------------
    DELETE FROM INDECI_TIPO_SOLICITUD_RRHH WHERE CODIGO = 'TELETRABAJO';
    DBMS_OUTPUT.PUT_LINE('Tipo TELETRABAJO -> eliminado (' || SQL%ROWCOUNT || ' fila).');

    COMMIT;
    DBMS_OUTPUT.PUT_LINE('V012_27 rollback finalizado.');
END;
/
