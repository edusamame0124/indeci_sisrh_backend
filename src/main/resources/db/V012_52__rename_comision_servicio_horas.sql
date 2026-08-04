-- ============================================================================
-- V012_52 — Renombrar tipo '006' a "Comisión de servicio por horas"
--
-- OBJETIVO:
--   Distinguir en el catálogo el permiso existente (código '006', por horas)
--   del nuevo tipo "Comisión de servicio por día" (código 'COMISION_DIA',
--   V012_53). Solo cambia NOMBRE — CODIGO, flags y comportamiento (validación,
--   asistencia, PDF) quedan exactamente igual que hoy.
--
-- Idempotente. Oracle 19c+ / GESTIONRRHH.
-- ============================================================================

SET SERVEROUTPUT ON;

DECLARE
    v_rows NUMBER;
BEGIN
    UPDATE INDECI_TIPO_SOLICITUD_RRHH
       SET NOMBRE = 'Comisión de servicio por horas'
     WHERE CODIGO = '006'
       AND NOMBRE <> 'Comisión de servicio por horas';

    v_rows := SQL%ROWCOUNT;

    COMMIT;
    DBMS_OUTPUT.PUT_LINE('V012_52: NOMBRE actualizado para CODIGO=''006'' (' || v_rows || ' fila[s]).');
END;
/
