-- ============================================================================
-- V012_49 — Activa JUSTIFICA_ASISTENCIA para código "011" (Licencia)
--
-- V012_36 dejó 011 (Licencia) y 012 (Vacaciones) en 0 a propósito, con la nota
-- "tienen su propio tipo de dia" — en ese momento no existía reconciliación
-- automática para ninguna de las dos. Ya se activó y extendió para Vacaciones
-- (decisión RR.HH., ver PapeletaJustificacionResolver); esta migración aplica
-- el mismo criterio a Licencia: una papeleta 011 APROBADA que cubre la fecha
-- ahora justifica el día en la carga de asistencia (no queda en FALTA).
--
-- El código (PapeletaJustificacionResolver.construir/observacion) distingue
-- CON GOCE / SIN GOCE vía TipoLicencia.ES_SIN_GOCE — ambas mapean a
-- TIPO_DIA='LICENCIA' (única categoría válida del CHECK para licencias), pero
-- la observación deja explícito cuál de las dos es.
--
-- Idempotente. Oracle 19c+ / GESTIONRRHH.
-- ============================================================================

SET SERVEROUTPUT ON

BEGIN
    UPDATE GESTIONRRHH.INDECI_TIPO_SOLICITUD_RRHH
       SET JUSTIFICA_ASISTENCIA = 1
     WHERE CODIGO = '011'
       AND JUSTIFICA_ASISTENCIA <> 1;

    DBMS_OUTPUT.PUT_LINE('JUSTIFICA_ASISTENCIA=1 para código 011 (' || SQL%ROWCOUNT || ' fila[s]).');

    COMMIT;
    DBMS_OUTPUT.PUT_LINE('V012_49 finalizado.');
END;
/
