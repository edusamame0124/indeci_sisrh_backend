-- ============================================================================
-- SSO / GDR / V010_69 - Roles normativos faltantes en Rendimiento
--
-- Proposito: agregar GDR_CIE, GDR_TITULAR y GDR_AUDITOR al catalogo
-- INDECI_SISTEMA_ROL para el sistema 'rendimiento', de modo que la consola
-- de Gestion de Usuarios permita asignarlos y el JWT los incluya en
-- sistemas.rendimiento (Spec GDR Fases 13, 17-18).
--
-- Requiere V010_45 (catalogo base) y V010_34 (INDECI_SISTEMA). Idempotente.
-- ============================================================================

SET SERVEROUTPUT ON;

MERGE INTO INDECI_SISTEMA_ROL d
USING (
    SELECT s.ID AS SISTEMA_ID,
           'GDR_CIE' AS CODIGO_ROL,
           'CIE' AS NOMBRE,
           'Comite Institucional de Evaluacion GDR (RPE 068-2020 Art. 42-48)' AS DESCRIPCION,
           6 AS ORDEN
      FROM INDECI_SISTEMA s
     WHERE s.CODIGO = 'rendimiento'
    UNION ALL
    SELECT s.ID,
           'GDR_TITULAR',
           'Titular',
           'Titular / Alta Direccion - consulta institucional GDR',
           7
      FROM INDECI_SISTEMA s
     WHERE s.CODIGO = 'rendimiento'
    UNION ALL
    SELECT s.ID,
           'GDR_AUDITOR',
           'Auditor',
           'Auditor / OCI - trazabilidad y lectura GDR',
           8
      FROM INDECI_SISTEMA s
     WHERE s.CODIGO = 'rendimiento'
) s
ON (d.SISTEMA_ID = s.SISTEMA_ID AND d.CODIGO_ROL = s.CODIGO_ROL)
WHEN NOT MATCHED THEN
    INSERT (SISTEMA_ID, CODIGO_ROL, NOMBRE, DESCRIPCION, ORDEN, ACTIVO)
    VALUES (s.SISTEMA_ID, s.CODIGO_ROL, s.NOMBRE, s.DESCRIPCION, s.ORDEN, 1)
WHEN MATCHED THEN
    UPDATE SET d.NOMBRE = s.NOMBRE,
               d.DESCRIPCION = s.DESCRIPCION,
               d.ORDEN = s.ORDEN,
               d.ACTIVO = 1;

COMMIT;

BEGIN
    DBMS_OUTPUT.PUT_LINE('V010_69 roles GDR_CIE, GDR_TITULAR, GDR_AUDITOR aplicados.');
END;
/