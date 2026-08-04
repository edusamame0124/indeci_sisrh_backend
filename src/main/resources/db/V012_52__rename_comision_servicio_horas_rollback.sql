-- ============================================================================
-- V012_52 ROLLBACK — Revierte el nombre de '006' a "Comisión de Servicio"
-- Idempotente. Oracle 19c+ / GESTIONRRHH.
-- ============================================================================

SET SERVEROUTPUT ON;

DECLARE
    v_rows NUMBER;
BEGIN
    UPDATE INDECI_TIPO_SOLICITUD_RRHH
       SET NOMBRE = 'Comisión de Servicio'
     WHERE CODIGO = '006'
       AND NOMBRE <> 'Comisión de Servicio';

    v_rows := SQL%ROWCOUNT;

    COMMIT;
    DBMS_OUTPUT.PUT_LINE('V012_52 rollback: NOMBRE restaurado para CODIGO=''006'' (' || v_rows || ' fila[s]).');
END;
/
