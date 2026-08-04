-- ============================================================================
-- V012_53 ROLLBACK — Elimina el tipo 'COMISION_DIA'
-- Idempotente. Ejecutar en GESTIONRRHH / Oracle 19c+.
--
-- ADVERTENCIA: no ejecutar si ya existen papeletas de tipo COMISION_DIA en
-- INDECI_SOLICITUD_RRHH (quedarían con TIPO_SOLICITUD_ID huérfano). Verificar antes.
-- ============================================================================

SET SERVEROUTPUT ON;

BEGIN
    DELETE FROM INDECI_TIPO_SOLICITUD_RRHH WHERE CODIGO = 'COMISION_DIA';
    DBMS_OUTPUT.PUT_LINE('Tipo COMISION_DIA -> eliminado (' || SQL%ROWCOUNT || ' fila).');

    COMMIT;
    DBMS_OUTPUT.PUT_LINE('V012_53 rollback finalizado.');
END;
/
