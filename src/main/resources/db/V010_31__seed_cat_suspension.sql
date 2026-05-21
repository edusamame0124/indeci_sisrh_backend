-- ============================================================================
-- Spec 013 / B3 / V010_31 — Seed del catálogo Tabla 21 SUNAT (suspensiones)
--                          (Etapa 3 · B3 · M09)
--
-- Siembra los 11 tipos de suspensión/licencia de la Tabla 21 SUNAT validados con
-- RRHH (fuente: db/seeds/catalogo-suspension-sunat.csv).
--
-- IDEMPOTENTE: MERGE — WHEN NOT MATCHED inserta; WHEN MATCHED actualiza la
-- metadata (descripción/flags) por si cambió. No duplica filas.
--
-- REGLAS DE NEGOCIO CLAVE:
--   - Cód 21 (Lactancia): VA_EN_SNL='N' → no se declara en el .snl, solo .jor.
--   - Cód 23 (Faltas): COD_DESCUENTO_PLAME='2046' → gatilla descuento directo.
--
-- Ejecutar en esquema GESTIONRRHH / Oracle 19c+ (después de V010_30).
-- ============================================================================

MERGE INTO GESTIONRRHH.INDECI_CAT_SUSPENSION_SUNAT d
USING (
    SELECT '01' AS COD_SUSPENSION, 'Incapacidad Temporal (primeros 20 dias)' AS DESCRIPCION, 'NO_LABORADO_NO_SUB' AS TIPO_PLAME, 'S' AS REQUIERE_CMP, 'N' AS REQUIERE_RESOLUCION, 'S' AS VA_EN_SNL, CAST(NULL AS VARCHAR2(6)) AS COD_DESCUENTO_PLAME, 'Asumido por INDECI empleador' AS SUSTENTO_LEGAL FROM DUAL UNION ALL
    SELECT '02', 'Maternidad (subsidio EsSalud)',          'SUBSIDIADO',         'S', 'N', 'S', NULL,   '98 dias regulares (128 parto multiple)'  FROM DUAL UNION ALL
    SELECT '03', 'Descanso Medico (dia 21 en adelante)',   'SUBSIDIADO',         'S', 'N', 'S', NULL,   'Reembolso EsSalud'                       FROM DUAL UNION ALL
    SELECT '05', 'Licencia con goce de haber (general)',   'NO_LABORADO_NO_SUB', 'N', 'S', 'S', NULL,   'Permisos oficiales por Ley'             FROM DUAL UNION ALL
    SELECT '06', 'Licencia sin goce de haber (general)',   'NO_LABORADO_NO_SUB', 'N', 'S', 'S', NULL,   'Suspension perfecta afecta neto a pagar' FROM DUAL UNION ALL
    SELECT '07', 'Sancion Disciplinaria (suspension)',     'NO_LABORADO_NO_SUB', 'N', 'S', 'S', NULL,   'Medida correctiva regimen disciplinario' FROM DUAL UNION ALL
    SELECT '09', 'Fallecimiento de familiares directos',   'NO_LABORADO_NO_SUB', 'N', 'S', 'S', NULL,   'Licencia con goce obligatoria por ley'  FROM DUAL UNION ALL
    SELECT '21', 'Lactancia Materna (hora diaria)',        'ESPECIAL',           'N', 'S', 'N', NULL,   'No reduce dias - solo archivo .jor'     FROM DUAL UNION ALL
    SELECT '23', 'Inasistencia Injustificada (faltas)',    'NO_LABORADO_NO_SUB', 'N', 'N', 'S', '2046', 'Activa descuento directo concepto 2046'  FROM DUAL UNION ALL
    SELECT '25', 'Licencia por Paternidad',                'NO_LABORADO_NO_SUB', 'N', 'S', 'S', NULL,   '10 dias calendario con goce de haber'   FROM DUAL UNION ALL
    SELECT '31', 'Huelga / Paro de Labores',               'NO_LABORADO_NO_SUB', 'N', 'N', 'S', NULL,   'Suspension calificada por MTPE'         FROM DUAL
) s
ON (d.COD_SUSPENSION = s.COD_SUSPENSION)
WHEN MATCHED THEN
    UPDATE SET
        d.DESCRIPCION         = s.DESCRIPCION,
        d.TIPO_PLAME          = s.TIPO_PLAME,
        d.REQUIERE_CMP        = s.REQUIERE_CMP,
        d.REQUIERE_RESOLUCION = s.REQUIERE_RESOLUCION,
        d.VA_EN_SNL           = s.VA_EN_SNL,
        d.COD_DESCUENTO_PLAME = s.COD_DESCUENTO_PLAME,
        d.SUSTENTO_LEGAL      = s.SUSTENTO_LEGAL
WHEN NOT MATCHED THEN
    INSERT (COD_SUSPENSION, DESCRIPCION, TIPO_PLAME, REQUIERE_CMP,
            REQUIERE_RESOLUCION, VA_EN_SNL, COD_DESCUENTO_PLAME, SUSTENTO_LEGAL)
    VALUES (s.COD_SUSPENSION, s.DESCRIPCION, s.TIPO_PLAME, s.REQUIERE_CMP,
            s.REQUIERE_RESOLUCION, s.VA_EN_SNL, s.COD_DESCUENTO_PLAME, s.SUSTENTO_LEGAL);

COMMIT;

-- Verificación rápida:
-- SELECT COD_SUSPENSION, TIPO_PLAME, VA_EN_SNL, COD_DESCUENTO_PLAME
--   FROM GESTIONRRHH.INDECI_CAT_SUSPENSION_SUNAT ORDER BY COD_SUSPENSION;
-- Esperado: 11 filas. Cód 21 VA_EN_SNL='N'; cód 23 COD_DESCUENTO_PLAME='2046'.
