-- ============================================================================
-- Spec 010 / V010_16 — Baja lógica de EmpleadoConcepto legacy auto-calculados
--
-- Antes de Spec 010 los aportes (ONP/AFP/comisión/prima), el ESSALUD, el
-- copago EPS y la retención 5ta se cargaban a mano como INDECI_EMPLEADO_CONCEPTO.
-- El motor v2 ahora los CALCULA automáticamente y los IGNORA como manuales
-- (Corrección A) — el cálculo ya es correcto. Pero esos registros legacy
-- siguen visibles en la ficha del empleado y confunden al operador.
--
-- Este script les da de BAJA LÓGICA (ACTIVO = 0). No afecta el cálculo
-- (el motor ya los ignoraba); solo limpia la ficha.
--
-- CÓDIGOS LIMPIADOS — solo los que el motor calcula SIEMPRE:
--   05001 ONP · 05002 AFP · 05003 comisión · 05004 prima
--   05101 retención 5ta · 06001 ESSALUD · 06002 ESSALUD EPS · 05309 copago EPS
--
-- NO se incluye 00302 / 00502 (asignación familiar): el motor solo la calcula
-- si EMPLEADO_PLANILLA.TIENE_ASIGNACION_FAMILIAR = 1. Dar de baja un manual
-- 00302 sin verificar ese flag podría hacer perder la asignación. Se revisa
-- aparte (ver nota al final).
--
-- Ejecutar en esquema GESTIONRRHH / Oracle 19c+. Idempotente.
-- ============================================================================

SET SERVEROUTPUT ON;

-- ----------------------------------------------------------------------------
-- BLOQUE 1 — DIAGNÓSTICO: qué EmpleadoConcepto activos se van a dar de baja.
-- ----------------------------------------------------------------------------
SELECT ec.EMPLEADO_ID,
       cp.CODIGO_MEF,
       cp.NOMBRE,
       ec.MONTO,
       ec.PORCENTAJE
FROM   GESTIONRRHH.INDECI_EMPLEADO_CONCEPTO ec
JOIN   GESTIONRRHH.INDECI_CONCEPTO_PLANILLA cp ON cp.ID = ec.CONCEPTO_PLANILLA_ID
WHERE  ec.ACTIVO = 1
  AND  cp.CODIGO_MEF IN
       ('05001', '05002', '05003', '05004', '05101', '06001', '06002', '05309')
ORDER  BY ec.EMPLEADO_ID, cp.CODIGO_MEF;

-- ----------------------------------------------------------------------------
-- BLOQUE 2 — BAJA LÓGICA + reporte.
-- ----------------------------------------------------------------------------
DECLARE
    v_filas NUMBER;
BEGIN
    UPDATE GESTIONRRHH.INDECI_EMPLEADO_CONCEPTO ec
       SET ec.ACTIVO = 0
     WHERE ec.ACTIVO = 1
       AND ec.CONCEPTO_PLANILLA_ID IN (
               SELECT cp.ID
                 FROM GESTIONRRHH.INDECI_CONCEPTO_PLANILLA cp
                WHERE cp.CODIGO_MEF IN
                      ('05001', '05002', '05003', '05004',
                       '05101', '06001', '06002', '05309'));

    v_filas := SQL%ROWCOUNT;
    COMMIT;

    DBMS_OUTPUT.PUT_LINE('V010_16 — EmpleadoConcepto legacy dados de baja: ' || v_filas);
    DBMS_OUTPUT.PUT_LINE('El motor ya los ignoraba; el cálculo de planilla no cambia.');
END;
/

-- ----------------------------------------------------------------------------
-- NOTA — Asignación familiar (00302 / 00502):
-- Revisar manualmente si hay empleados con un EmpleadoConcepto 00302/00502 y
-- TIENE_ASIGNACION_FAMILIAR = 0. En esos casos, antes de dar de baja el
-- concepto, poner el flag en 1 para que el motor la siga calculando:
--
-- SELECT ec.EMPLEADO_ID, cp.CODIGO_MEF, ep.TIENE_ASIGNACION_FAMILIAR
-- FROM   GESTIONRRHH.INDECI_EMPLEADO_CONCEPTO ec
-- JOIN   GESTIONRRHH.INDECI_CONCEPTO_PLANILLA cp ON cp.ID = ec.CONCEPTO_PLANILLA_ID
-- LEFT JOIN GESTIONRRHH.INDECI_EMPLEADO_PLANILLA ep
--        ON ep.EMPLEADO_ID = ec.EMPLEADO_ID AND ep.ACTIVO = 1
-- WHERE  ec.ACTIVO = 1 AND cp.CODIGO_MEF IN ('00302','00502');
-- ----------------------------------------------------------------------------
