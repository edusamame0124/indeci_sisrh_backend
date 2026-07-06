-- ============================================================
-- V012_06 — Backfill del historial remunerativo desde SUELDO_BASICO
--
-- Crea una fila de historial (fuente MIGRACION_LEGACY, estado APROBADO) por cada
-- vínculo activo con SUELDO_BASICO, para que el motor tenga base vigente desde el
-- inicio. NO borra SUELDO_BASICO (queda como fallback).
--
-- Idempotente: solo inserta para vínculos que aún no tienen fila MIGRACION_LEGACY.
-- ============================================================

SET SERVEROUTPUT ON;

DECLARE
    v_rows NUMBER := 0;
BEGIN
    INSERT INTO GESTIONRRHH.INDECI_EMPLEADO_REMUNERACION_HIST
        (EMPLEADO_PLANILLA_ID, VIGENCIA_DESDE, VIGENCIA_HASTA, MONTO_BASE,
         REMUNERACION_TOTAL, TIPO_CAMBIO, FUENTE, ESTADO, OBSERVACION, CREATED_BY)
    SELECT ep.ID,
           COALESCE(ep.FECHA_INICIO_CONTRATO, ep.FECHA_INICIO, ep.FECHA_INGRESO, TRUNC(SYSDATE)),
           NULL,
           ep.MONTO_CONTRATO,
           ep.SUELDO_BASICO,
           'CONTRATO_INICIAL',
           'MIGRACION_LEGACY',
           'APROBADO',
           'Backfill V012_06 desde EMPLEADO_PLANILLA.SUELDO_BASICO',
           'SISTEMA'
      FROM GESTIONRRHH.INDECI_EMPLEADO_PLANILLA ep
     WHERE ep.ACTIVO = 1
       AND ep.SUELDO_BASICO IS NOT NULL
       AND NOT EXISTS (
           SELECT 1 FROM GESTIONRRHH.INDECI_EMPLEADO_REMUNERACION_HIST h
            WHERE h.EMPLEADO_PLANILLA_ID = ep.ID
              AND h.FUENTE = 'MIGRACION_LEGACY');

    v_rows := SQL%ROWCOUNT;
    COMMIT;
    DBMS_OUTPUT.PUT_LINE('V012_06 -> ' || v_rows || ' fila(s) de historial migradas.');
EXCEPTION
    WHEN OTHERS THEN
        ROLLBACK;
        DBMS_OUTPUT.PUT_LINE('ERROR en V012_06: ' || SQLERRM);
        RAISE;
END;
/
