-- ============================================================================
-- Spec 010 / V010_05 — Backfill CODIGO_MEF y TIPO_CONCEPTO para conceptos
-- pre-existentes en INDECI_CONCEPTO_PLANILLA (datos creados antes de Spec 010).
--
-- ⚠️  TODOS LOS UPDATEs ESTÁN COMENTADOS — revisar uno a uno antes de aplicar.
--
-- Pasos sugeridos:
--   1. Ejecutar el SELECT de inventario al final para listar conceptos sin CODIGO_MEF.
--   2. Para cada concepto, decidir si:
--      (a) Corresponde a un concepto MEF estándar → descomentar el UPDATE con el código.
--      (b) Es un concepto custom de la entidad → solicitar CODIGO_MEF al MEF / AIRHSP.
--      (c) Es legacy y debe darse de baja → ACTIVO = 0.
--   3. Recién después, aplicar V010_06 para volver CODIGO_MEF NOT NULL + UK.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Plantillas de backfill (descomentar y completar el WHERE según los códigos
-- internos reales que ya existan en INDECI_CONCEPTO_PLANILLA.CODIGO):
-- ----------------------------------------------------------------------------

-- UPDATE GESTIONRRHH.INDECI_CONCEPTO_PLANILLA SET
--     CODIGO_MEF          = '00101',
--     TIPO_CONCEPTO       = 'REMUNERATIVO',
--     AFECTO_APORTE_PENS  = 'S',
--     AFECTO_ESSALUD      = 'S',
--     REGIMEN_APLICABLE   = '276'
-- WHERE CODIGO = 'SB-276' AND CODIGO_MEF IS NULL;

-- UPDATE GESTIONRRHH.INDECI_CONCEPTO_PLANILLA SET
--     CODIGO_MEF          = '00301',
--     TIPO_CONCEPTO       = 'REMUNERATIVO',
--     AFECTO_IR_5TA       = 'S',
--     AFECTO_APORTE_PENS  = 'S',
--     AFECTO_ESSALUD      = 'S',
--     REGIMEN_APLICABLE   = '728'
-- WHERE CODIGO = 'SB-728' AND CODIGO_MEF IS NULL;

-- UPDATE GESTIONRRHH.INDECI_CONCEPTO_PLANILLA SET
--     CODIGO_MEF          = '00501',
--     TIPO_CONCEPTO       = 'REMUNERATIVO',
--     AFECTO_APORTE_PENS  = 'S',
--     AFECTO_ESSALUD      = 'S',
--     REGIMEN_APLICABLE   = '1057'
-- WHERE CODIGO = 'CAS-REM' AND CODIGO_MEF IS NULL;

-- UPDATE GESTIONRRHH.INDECI_CONCEPTO_PLANILLA SET
--     CODIGO_MEF          = '05001',
--     TIPO_CONCEPTO       = 'APORTE_TRABAJADOR',
--     REGIMEN_APLICABLE   = 'TODOS'
-- WHERE CODIGO IN ('ONP', 'APORTE-ONP') AND CODIGO_MEF IS NULL;

-- UPDATE GESTIONRRHH.INDECI_CONCEPTO_PLANILLA SET
--     CODIGO_MEF          = '05002',
--     TIPO_CONCEPTO       = 'APORTE_TRABAJADOR',
--     REGIMEN_APLICABLE   = 'TODOS'
-- WHERE CODIGO IN ('AFP', 'APORTE-AFP') AND CODIGO_MEF IS NULL;

-- ============================================================================
-- INVENTARIO — ejecutar este SELECT primero:
-- ============================================================================
SELECT
    cp.ID,
    cp.CODIGO,
    cp.NOMBRE,
    cp.TIPO            AS TIPO_LEGACY,
    cp.NATURALEZA,
    cp.ACTIVO,
    cp.CODIGO_MEF,
    cp.TIPO_CONCEPTO,
    cp.REGIMEN_APLICABLE
FROM   GESTIONRRHH.INDECI_CONCEPTO_PLANILLA cp
WHERE  cp.CODIGO_MEF IS NULL
   OR  cp.TIPO_CONCEPTO IS NULL
ORDER  BY cp.ID;

-- ============================================================================
-- COMMIT manual al final, tras revisar cada UPDATE.
-- ============================================================================
-- COMMIT;
