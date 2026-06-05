-- ============================================================================
-- V010_63 — Inserta PLA_WRITE en SS_PERMISO (si falta) y lo asigna a los
--           roles RRHH / PLANILLA / ADMIN en SS_ROL_PERMISO.
--
-- V010_62 retornó 0 filas porque PLA_WRITE no existía en SS_PERMISO;
-- el CROSS JOIN no generó filas para hacer el MERGE.
-- Este script primero garantiza la existencia del permiso y luego mapea.
--
-- Idempotente: todos los pasos usan MERGE (no duplica).
-- Ejecutar en GESTIONRRHH / Oracle 19c+.
-- ============================================================================

SET SERVEROUTPUT ON;
SET DEFINE OFF;

-- ── 1. Asegurar que los permisos PLA_* existen en SS_PERMISO ────────────────
MERGE INTO GESTIONRRHH.SS_PERMISO d
USING (
    SELECT 'PLA_READ'    AS CODIGO, 'Planilla — lectura'              AS DESCRIPCION, 'PLA' AS TIPO FROM DUAL UNION ALL
    SELECT 'PLA_WRITE',             'Planilla — procesamiento',        'PLA' FROM DUAL UNION ALL
    SELECT 'PLA_APPROVE',           'Planilla — aprobación y cierre',  'PLA' FROM DUAL
) s ON (UPPER(d.CODIGO) = UPPER(s.CODIGO))
WHEN MATCHED THEN
    UPDATE SET d.DESCRIPCION = s.DESCRIPCION, d.TIPO = s.TIPO, d.ACTIVO = 'S'
WHEN NOT MATCHED THEN
    INSERT (CODIGO, DESCRIPCION, TIPO, ACTIVO, ORDEN, DESPLEGABLE)
    VALUES (s.CODIGO, s.DESCRIPCION, s.TIPO, 'S', 0, 'N');

DBMS_OUTPUT.PUT_LINE('SS_PERMISO PLA_* sincronizado.');

-- ── 2. Asignar PLA_WRITE a roles operativos ─────────────────────────────────
MERGE INTO GESTIONRRHH.SS_ROL_PERMISO d
USING (
    SELECT r.ID_ROL, p.ID_PERMISO
      FROM GESTIONRRHH.SS_ROL r
      CROSS JOIN GESTIONRRHH.SS_PERMISO p
     WHERE UPPER(r.CODIGO) IN (
               'SUPER_ADMIN',
               'ADMIN_TI',
               'ADMIN',
               'RRHH_JEFE',
               'RRHH_ADMIN',
               'RRHH_ANALISTA',
               'PLANILLA_ANALISTA',
               'PLANILLA_APROBADOR'
           )
       AND UPPER(p.CODIGO) = 'PLA_WRITE'
) s ON (d.ID_ROL = s.ID_ROL AND d.ID_PERMISO = s.ID_PERMISO)
WHEN NOT MATCHED THEN
    INSERT (ID_ROL, ID_PERMISO) VALUES (s.ID_ROL, s.ID_PERMISO);

DBMS_OUTPUT.PUT_LINE('SS_ROL_PERMISO PLA_WRITE asignado.');

COMMIT;

PROMPT V010_63 — PLA_WRITE en SS_PERMISO y SS_ROL_PERMISO aplicado.
