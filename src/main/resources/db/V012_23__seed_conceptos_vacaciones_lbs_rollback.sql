-- Rollback V012_23 — conceptos de Liquidación Vacacional en LBS.
-- La clave del concepto es CODIGO (no CODIGO_INTERNO).
DELETE FROM GESTIONRRHH.INDECI_CONCEPTO_PLANILLA
WHERE CODIGO IN ('VAC_TRUNCAS', 'VAC_NO_GOZADA');
