-- ============================================================================
-- P0 / V010_56 - Regimenes pensionarios sin aporte
--
-- Objetivo:
--   Formalizar catalogos operativos que NO generan descuento AFP/ONP:
--   pensionista, retiro y sin regimen pensionario.
--
-- Regla motor:
--   Si INDECI_REGIMEN_PENSIONARIO.TIPO esta en estos valores, no se calcula
--   aporte pensionario ni se autocompletan tasas AFP/ONP.
--
-- Idempotente. Ejecutar en esquema GESTIONRRHH / Oracle 19c+.
-- ============================================================================

MERGE INTO GESTIONRRHH.INDECI_REGIMEN_PENSIONARIO d
USING (
    SELECT 'PENSIONISTA' AS NOMBRE, 'PENSIONISTA' AS CODIGO, 'PENSIONISTA' AS TIPO FROM DUAL UNION ALL
    SELECT 'RETIRO',              'RETIRO',              'RETIRO'              FROM DUAL UNION ALL
    SELECT 'AFP RETIRO',          'AFP_RETIRO',          'AFP_RETIRO'          FROM DUAL UNION ALL
    SELECT 'SIN REGIMEN',         'SIN_REGIMEN',         'SIN_REGIMEN'         FROM DUAL
) s
ON (d.CODIGO = s.CODIGO)
WHEN MATCHED THEN UPDATE SET
    d.NOMBRE = s.NOMBRE,
    d.TIPO   = s.TIPO,
    d.ACTIVO = 1
WHEN NOT MATCHED THEN INSERT (NOMBRE, CODIGO, TIPO, ACTIVO)
VALUES (s.NOMBRE, s.CODIGO, s.TIPO, 1);

COMMIT;

SELECT ID, NOMBRE, CODIGO, TIPO, ACTIVO
FROM   GESTIONRRHH.INDECI_REGIMEN_PENSIONARIO
WHERE  CODIGO IN ('PENSIONISTA', 'RETIRO', 'AFP_RETIRO', 'SIN_REGIMEN');
