-- ============================================================================
-- V010_73 — Corregir datos legacy en INDECI_REGIMEN_PENSIONARIO e INDECI_EMPLEADO_PENSION
--
-- Problema raíz (confirmado con datos reales):
--   INDECI_REGIMEN_PENSIONARIO contiene filas legacy (ej. ID=1, NOMBRE='INTEGRA')
--   donde CODIGO = ID numérico ('1', '2', ...) y TIPO IS NULL.
--   Estas filas preceden a las filas limpias (IDs 21-23) y NO son devueltas
--   por queries con WHERE TIPO='AFP', por eso pasaron desapercibidas.
--
--   INDECI_EMPLEADO_PENSION tiene TIPO_REGIMEN=NULL para esos empleados.
--
-- Corrección en 2 pasos:
--   1. Actualizar CODIGO y TIPO en INDECI_REGIMEN_PENSIONARIO para filas cuyo
--      NOMBRE coincide con un AFP del catálogo (INDECI_AFP).
--   2. Rellenar TIPO_REGIMEN en INDECI_EMPLEADO_PENSION donde está NULL.
--
-- Idempotente. Ejecutar en esquema GESTIONRRHH / Oracle 19c+.
-- ============================================================================

-- ── Diagnóstico previo ───────────────────────────────────────────────────────
SELECT ID, NOMBRE, CODIGO, TIPO, ACTIVO
FROM   GESTIONRRHH.INDECI_REGIMEN_PENSIONARIO
ORDER  BY ID;

-- ── Paso 1: Corregir CODIGO y TIPO en INDECI_REGIMEN_PENSIONARIO ─────────────
-- Aplica a filas donde TIPO IS NULL o TIPO='AFP' y cuyo NOMBRE coincide
-- (normalizado) con un CODIGO de INDECI_AFP.
MERGE INTO GESTIONRRHH.INDECI_REGIMEN_PENSIONARIO rp
USING (
    SELECT rp2.ID,
           afp.CODIGO AS CODIGO_CORRECTO
    FROM   GESTIONRRHH.INDECI_REGIMEN_PENSIONARIO rp2
    JOIN   GESTIONRRHH.INDECI_AFP afp
           ON  UPPER(TRIM(afp.CODIGO))
               = UPPER(TRIM(REPLACE(UPPER(rp2.NOMBRE), 'AFP ', '')))
    WHERE  (rp2.TIPO IS NULL OR rp2.TIPO = 'AFP')
      AND  (rp2.CODIGO != afp.CODIGO OR rp2.TIPO IS NULL)
) src
ON (rp.ID = src.ID)
WHEN MATCHED THEN
    UPDATE SET
        rp.CODIGO = src.CODIGO_CORRECTO,
        rp.TIPO   = 'AFP';

COMMIT;

-- ── Paso 2: Rellenar TIPO_REGIMEN en INDECI_EMPLEADO_PENSION donde es NULL ───
UPDATE GESTIONRRHH.INDECI_EMPLEADO_PENSION ep
SET    ep.TIPO_REGIMEN = (
           SELECT rp.TIPO
           FROM   GESTIONRRHH.INDECI_REGIMEN_PENSIONARIO rp
           WHERE  rp.ID = ep.REGIMEN_PENSIONARIO_ID
       )
WHERE  ep.TIPO_REGIMEN IS NULL
  AND  ep.REGIMEN_PENSIONARIO_ID IS NOT NULL;

COMMIT;

-- ── Verificación post-corrección ─────────────────────────────────────────────
-- 1. Filas AFP deben tener CODIGO semántico y TIPO='AFP':
SELECT ID, NOMBRE, CODIGO, TIPO, ACTIVO
FROM   GESTIONRRHH.INDECI_REGIMEN_PENSIONARIO
WHERE  TIPO = 'AFP'
ORDER  BY ID;

-- 2. Filas que aún no pudieron resolverse (deben ser 0):
SELECT rp.ID, rp.NOMBRE, rp.CODIGO, rp.TIPO
FROM   GESTIONRRHH.INDECI_REGIMEN_PENSIONARIO rp
WHERE  (rp.TIPO IS NULL OR rp.TIPO = 'AFP')
  AND  NOT EXISTS (
           SELECT 1 FROM GESTIONRRHH.INDECI_AFP afp
           WHERE  afp.CODIGO = rp.CODIGO
       );

-- 3. Empleados cuyo TIPO_REGIMEN sigue siendo NULL (idealmente 0):
SELECT ep.ID, ep.EMPLEADO_ID, ep.REGIMEN_PENSIONARIO_ID,
       ep.TIPO_REGIMEN, rp.NOMBRE, rp.CODIGO, rp.TIPO
FROM   GESTIONRRHH.INDECI_EMPLEADO_PENSION ep
LEFT   JOIN GESTIONRRHH.INDECI_REGIMEN_PENSIONARIO rp
       ON rp.ID = ep.REGIMEN_PENSIONARIO_ID
WHERE  ep.ACTIVO = 1
ORDER  BY ep.EMPLEADO_ID;
