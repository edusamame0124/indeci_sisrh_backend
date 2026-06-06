-- ============================================================================
-- V010_54 — Backfill: remuneración base de EmpleadoConcepto → EmpleadoPlanilla.sueldoBasico
--
-- Con el nuevo motor, la base remunerativa se toma de INDECI_EMPLEADO_PLANILLA.
-- SUELDO_BASICO (no de un concepto asignado a mano). Este backfill:
--   1) Si la planilla activa del empleado NO tiene sueldo (NULL/0) y tiene un
--      concepto base manual activo → copia el monto al SUELDO_BASICO.
--   2) Desactiva los conceptos base manuales (para que no se dupliquen ni se
--      ofrezcan; el motor ya graba la base desde SUELDO_BASICO).
--
-- Conjunto base (MEF): 00101, 00102, 00301, 00501 (CAS/728/276) + L001..L004
-- (SERVIR RD0111) + 010289..010292 (SERVIR legacy RD0082, por si existieran).
-- Idempotente: tras correr no quedan conceptos base activos → re-ejecutar no hace nada.
-- Ejecutar en GESTIONRRHH / Oracle 19c+ DESPUÉS de V010_53.
-- ============================================================================

SET SERVEROUTPUT ON;

DECLARE
    v_planillas NUMBER := 0;
    v_conceptos NUMBER := 0;
BEGIN
    -- 1) Subir el monto base al SUELDO_BASICO cuando esté vacío.
    UPDATE INDECI_EMPLEADO_PLANILLA pl
       SET pl.SUELDO_BASICO = (
            SELECT MAX(ec.MONTO)
              FROM INDECI_EMPLEADO_CONCEPTO ec
              JOIN INDECI_CONCEPTO_PLANILLA c ON c.ID = ec.CONCEPTO_PLANILLA_ID
             WHERE ec.EMPLEADO_ID = pl.EMPLEADO_ID
               AND ec.ACTIVO = 1
               AND c.CODIGO_MEF IN ('00101','00102','00301','00501',
                                    'L001','L002','L003','L004',
                                    '010289','010290','010291','010292'))
     WHERE pl.ACTIVO = 1
       AND (pl.SUELDO_BASICO IS NULL OR pl.SUELDO_BASICO = 0)
       AND EXISTS (
            SELECT 1
              FROM INDECI_EMPLEADO_CONCEPTO ec
              JOIN INDECI_CONCEPTO_PLANILLA c ON c.ID = ec.CONCEPTO_PLANILLA_ID
             WHERE ec.EMPLEADO_ID = pl.EMPLEADO_ID
               AND ec.ACTIVO = 1
               AND c.CODIGO_MEF IN ('00101','00102','00301','00501',
                                    'L001','L002','L003','L004',
                                    '010289','010290','010291','010292'));
    v_planillas := SQL%ROWCOUNT;

    -- 2) Desactivar los conceptos base manuales.
    UPDATE INDECI_EMPLEADO_CONCEPTO ec
       SET ec.ACTIVO = 0
     WHERE ec.ACTIVO = 1
       AND ec.CONCEPTO_PLANILLA_ID IN (
            SELECT c.ID FROM INDECI_CONCEPTO_PLANILLA c
             WHERE c.CODIGO_MEF IN ('00101','00102','00301','00501',
                                    'L001','L002','L003','L004',
                                    '010289','010290','010291','010292'));
    v_conceptos := SQL%ROWCOUNT;

    DBMS_OUTPUT.PUT_LINE('Planillas con SUELDO_BASICO backfilleado: ' || v_planillas);
    DBMS_OUTPUT.PUT_LINE('Conceptos base manuales desactivados: '   || v_conceptos);
    COMMIT;
    DBMS_OUTPUT.PUT_LINE('V010_54 finalizado.');
END;
/
