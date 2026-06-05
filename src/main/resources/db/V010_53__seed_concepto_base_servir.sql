-- ============================================================================
-- V010_53 — Conceptos base de remuneración SERVIR (Ley 30057).
--
-- Catálogo Único de Conceptos de Ingresos — RD0111-2021-EF/53.01 (MEF),
-- bloque "Servidores civiles de la Ley 30057". Compensación económica base por
-- subgrupo. El motor (PASO 5) resuelve el concepto base SERVIR según el subgrupo
-- del servidor (INDECI_TIPO_PERSONAL.CODIGO), con fallback L003.
--
--   L001 — Compensación Económica del Funcionario Público
--   L002 — Compensación Económica del Directivo Público
--   L003 — Compensación Económica del Servidor Civil de Carrera
--   L004 — Compensación Económica de los Servidores de Actividades Complementarias
--
-- Nota: RD0082 usaba códigos numéricos 010289–010292; el catálogo vigente RD0111
-- usa L001–L004. Son conceptos de INGRESO (REMUNERATIVO), afectos a IR 5ta,
-- aporte pensionario y ESSALUD. Idempotente (MERGE por CODIGO_MEF).
-- Ejecutar en GESTIONRRHH / Oracle 19c+.
-- ============================================================================

SET SERVEROUTPUT ON;

MERGE INTO INDECI_CONCEPTO_PLANILLA d
USING (
    SELECT 'L001' AS CODIGO_MEF, 'Compensación Económica del Funcionario Público'                 AS NOMBRE FROM DUAL UNION ALL
    SELECT 'L002',               'Compensación Económica del Directivo Público'                              FROM DUAL UNION ALL
    SELECT 'L003',               'Compensación Económica del Servidor Civil de Carrera'                      FROM DUAL UNION ALL
    SELECT 'L004',               'Compensación Económica de los Servidores de Actividades Complementarias'   FROM DUAL
) s
ON ( d.CODIGO_MEF = s.CODIGO_MEF )
WHEN MATCHED THEN UPDATE SET
    d.NOMBRE            = s.NOMBRE,
    d.TIPO_CONCEPTO     = 'REMUNERATIVO',
    d.REGIMEN_APLICABLE = 'SERVIR',
    d.ACTIVO            = 1
WHEN NOT MATCHED THEN INSERT (
    CODIGO, CODIGO_MEF, NOMBRE, TIPO, NATURALEZA,
    TIPO_CONCEPTO, AFECTO_IR_5TA, AFECTO_APORTE_PENS, AFECTO_ESSALUD,
    ES_MUC, ES_CUC, REGIMEN_APLICABLE,
    ACTIVO, CREATED_AT, FECHA_VIG_INI
) VALUES (
    s.CODIGO_MEF, s.CODIGO_MEF, s.NOMBRE, 'INGRESO', 'INGRESO',
    'REMUNERATIVO', 'S', 'S', 'S',
    'N', 'N', 'SERVIR',
    1, CURRENT_TIMESTAMP, DATE '2026-01-01'
);

COMMIT;
