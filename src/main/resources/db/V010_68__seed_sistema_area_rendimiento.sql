-- ============================================================================
-- SSO / GDR / V010_68 - Catalogo de areas (oficinas) del sistema Rendimiento
--
-- Proposito: poblar INDECI_SISTEMA_AREA para el sistema 'rendimiento' (GDR) de
-- modo que la consola de Gestion de Usuarios muestre el selector de Oficina
-- (igual que Convocatoria) y persista AREA_CODIGO en INDECI_USUARIO_SISTEMA.
--
-- Fuente: snapshot de HR_ORG_UNIT (BD GDR). CODIGO_AREA == HR_ORG_UNIT.UNIT_CODE
-- porque GDR resuelve la oficina por codigo (findActiveByCode) al aprovisionar
-- la HrPerson del usuario SSO. NOMBRE_AREA en ASCII por consistencia con V010_47.
--
-- Requiere V010_34 (INDECI_SISTEMA) y V010_47 (INDECI_SISTEMA_AREA). Idempotente.
--
-- POSIBLE_CAMBIO_RRHH: si GDR agrega/renombra oficinas en HR_ORG_UNIT, este
-- seed debe re-aplicarse (snapshot estatico; no hay sync automatico cross-BD).
-- ============================================================================

SET SERVEROUTPUT ON;

-- Seed oficinas de Rendimiento (refleja HR_ORG_UNIT de GDR; UNIT_CODE = CODIGO_AREA)
MERGE INTO INDECI_SISTEMA_AREA d
USING (
    SELECT s.ID AS SISTEMA_ID,
           'ORH'          AS CODIGO_AREA, 'Oficina de Recursos Humanos'        AS NOMBRE_AREA, 'ORH'          AS SIGLA, 1 AS ORDEN FROM INDECI_SISTEMA s WHERE s.CODIGO = 'rendimiento'
    UNION ALL
    SELECT s.ID, 'DESP_JEF',      'Despacho Jefatural',                  'DESP_JEF',      2 FROM INDECI_SISTEMA s WHERE s.CODIGO = 'rendimiento'
    UNION ALL
    SELECT s.ID, 'SEC_GRAL',      'Secretaria General',                  'SEC_GRAL',      3 FROM INDECI_SISTEMA s WHERE s.CODIGO = 'rendimiento'
    UNION ALL
    SELECT s.ID, 'ORG_CTRL_INST', 'Organo de Control Institucional',     'ORG_CTRL_INST', 4 FROM INDECI_SISTEMA s WHERE s.CODIGO = 'rendimiento'
    UNION ALL
    SELECT s.ID, 'OF_AS_JUR',     'Oficina de Asesoria Juridica',        'OF_AS_JUR',     5 FROM INDECI_SISTEMA s WHERE s.CODIGO = 'rendimiento'
    UNION ALL
    SELECT s.ID, 'OF_PLA_PPTO',   'Oficina de Planeamiento y Presupuesto', 'OF_PLA_PPTO', 6 FROM INDECI_SISTEMA s WHERE s.CODIGO = 'rendimiento'
    UNION ALL
    SELECT s.ID, 'OF_GRAL_ADM',   'Oficina General de Administracion',   'OF_GRAL_ADM',   7 FROM INDECI_SISTEMA s WHERE s.CODIGO = 'rendimiento'
    UNION ALL
    SELECT s.ID, 'OF_INFORM',     'Oficina de Informatica',              'OF_INFORM',     8 FROM INDECI_SISTEMA s WHERE s.CODIGO = 'rendimiento'
    UNION ALL
    SELECT s.ID, 'UNI_REV_APEL',  'Unidad de Revision de Apelaciones',   'UNI_REV_APEL',  9 FROM INDECI_SISTEMA s WHERE s.CODIGO = 'rendimiento'
    UNION ALL
    SELECT s.ID, 'UNI_PLA_PPTO',  'Unidad de Planeamiento y Presupuesto', 'UNI_PLA_PPTO', 10 FROM INDECI_SISTEMA s WHERE s.CODIGO = 'rendimiento'
    UNION ALL
    SELECT s.ID, 'UNI_RACIONAL',  'Unidad de Racionalizacion',           'UNI_RACIONAL',  11 FROM INDECI_SISTEMA s WHERE s.CODIGO = 'rendimiento'
    UNION ALL
    SELECT s.ID, 'OI',            'Oficina de Informatica',              'OI',            12 FROM INDECI_SISTEMA s WHERE s.CODIGO = 'rendimiento'
) s
ON (d.SISTEMA_ID = s.SISTEMA_ID AND d.CODIGO_AREA = s.CODIGO_AREA)
WHEN NOT MATCHED THEN
    INSERT (SISTEMA_ID, CODIGO_AREA, NOMBRE_AREA, SIGLA, ORDEN, ACTIVO)
    VALUES (s.SISTEMA_ID, s.CODIGO_AREA, s.NOMBRE_AREA, s.SIGLA, s.ORDEN, 1);

COMMIT;
