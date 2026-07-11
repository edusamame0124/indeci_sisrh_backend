-- ============================================================================
-- V012_23 — Conceptos para Liquidación Vacacional en LBS (VAC_TRUNCAS / VAC_NO_GOZADA)
-- (Reubicada desde db/migration/V012_22 y deconflictada → V012_23 en carpeta canónica db/.)
-- ============================================================================
-- Esquema real GESTIONRRHH.INDECI_CONCEPTO_PLANILLA. LBS los busca por CODIGO
-- (LbsCalculationService.findByCodigoAndActivo). Vacaciones Truncas = afecto IR 5ta +
-- previsional, INAFECTO ESSALUD.
--
-- 🚫 NO EJECUTAR TODAVÍA (SPEC_VACACIONES F10):
--    ⚠️ LEY-01 (Ley 32448): CODIGO_MEF NO puede inventarse y tiene UNIQUE
--    (INDECI_CONCEPTO_MEF_UK). Los valores '020000'/'020001' son PLACEHOLDERS.
--    Reemplazar por los CODIGO_MEF oficiales (AIRHSP) que entregue RRHH antes de aplicar.
--
-- Idempotente: WHERE NOT EXISTS por CODIGO.
-- ============================================================================

INSERT INTO GESTIONRRHH.INDECI_CONCEPTO_PLANILLA
  (CODIGO, NOMBRE, TIPO, NATURALEZA, TIPO_CONCEPTO, CODIGO_MEF,
   ESTADO, ACTIVO, AFECTO_IR_5TA, AFECTO_APORTE_PENS, AFECTO_ESSALUD,
   ES_MUC, ES_CUC, REGIMEN_APLICABLE, MODO_CALCULO, ES_PRORRATEABLE,
   VERSION, INCLUYE_EN_PLANILLA, CREATED_AT)
SELECT 'VAC_TRUNCAS', 'Vacaciones Truncas', 'INGRESO', 'REMUNERATIVO', 'REMUNERATIVO',
       '020000', /* PLACEHOLDER — reemplazar por CODIGO_MEF oficial RRHH (LEY-01) */
       'ACTIVO', 1, 'S', 'S', 'N',
       'N', 'N', 'CAS,276,728,SERVIR', 'RESULTADO_MOTOR', 'N',
       1, 'S', SYSTIMESTAMP
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM GESTIONRRHH.INDECI_CONCEPTO_PLANILLA WHERE CODIGO = 'VAC_TRUNCAS');

INSERT INTO GESTIONRRHH.INDECI_CONCEPTO_PLANILLA
  (CODIGO, NOMBRE, TIPO, NATURALEZA, TIPO_CONCEPTO, CODIGO_MEF,
   ESTADO, ACTIVO, AFECTO_IR_5TA, AFECTO_APORTE_PENS, AFECTO_ESSALUD,
   ES_MUC, ES_CUC, REGIMEN_APLICABLE, MODO_CALCULO, ES_PRORRATEABLE,
   VERSION, INCLUYE_EN_PLANILLA, CREATED_AT)
SELECT 'VAC_NO_GOZADA', 'Vac. No Gozadas', 'INGRESO', 'REMUNERATIVO', 'REMUNERATIVO',
       '020001', /* PLACEHOLDER — reemplazar por CODIGO_MEF oficial RRHH (LEY-01); DISTINTO por UNIQUE */
       'ACTIVO', 1, 'S', 'S', 'N',
       'N', 'N', 'CAS,276,728,SERVIR', 'RESULTADO_MOTOR', 'N',
       1, 'S', SYSTIMESTAMP
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM GESTIONRRHH.INDECI_CONCEPTO_PLANILLA WHERE CODIGO = 'VAC_NO_GOZADA');
