-- ============================================================================
-- Spec 010 / V010_15 — Seed régimen pensionario ONP
--
-- INDECI_REGIMEN_PENSIONARIO tenía solo AFP (PROFUTURO/INTEGRA/PRIMA),
-- CPMP y AFP_RETIRO — faltaba el Sistema Nacional de Pensiones (ONP).
-- El motor de planilla resuelve el aporte por el campo TIPO ('ONP' | 'AFP'),
-- así que ONP debe existir con TIPO = 'ONP' para que se calcule el aporte 13%.
--
-- Idempotente: MERGE por TIPO = 'ONP' (no duplica si ya existe).
-- Ejecutar en esquema GESTIONRRHH / Oracle 19c+.
-- ============================================================================

MERGE INTO GESTIONRRHH.INDECI_REGIMEN_PENSIONARIO d
USING (
    SELECT 'ONP' AS NOMBRE, 'ONP' AS CODIGO, 'ONP' AS TIPO FROM DUAL
) s
ON (d.TIPO = s.TIPO)
WHEN NOT MATCHED THEN INSERT (NOMBRE, CODIGO, TIPO, ACTIVO)
VALUES (s.NOMBRE, s.CODIGO, s.TIPO, 1);

COMMIT;

-- Verificación: obtener el ID asignado a ONP (se necesita para los UPDATE
-- de INDECI_EMPLEADO_PENSION de los empleados que aportan a ONP).
SELECT ID, NOMBRE, CODIGO, TIPO, ACTIVO
FROM   GESTIONRRHH.INDECI_REGIMEN_PENSIONARIO
WHERE  TIPO = 'ONP';
