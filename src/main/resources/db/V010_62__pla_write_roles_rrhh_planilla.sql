-- ============================================================================
-- V010_62 — Agrega PLA_WRITE a roles RRHH, PLANILLA y ADMIN en SS_ROL_PERMISO.
--
-- El permiso PLA_WRITE (Planilla — procesamiento) no estaba asignado en BD
-- para los roles operativos de RRHH y Planilla. Sin esto el motor de generación
-- queda inaccesible por @PreAuthorize y la pantalla de edición de usuario no
-- muestra PLA_WRITE en la sección "Permisos de los roles asignados".
--
-- Roles que reciben PLA_WRITE:
--   SUPER_ADMIN, ADMIN_TI, ADMIN           → administración TI (acceso total)
--   RRHH_JEFE, RRHH_ADMIN                 → jefatura y legacy RRHH operativo
--   RRHH_ANALISTA                         → analista RRHH
--   PLANILLA_ANALISTA, PLANILLA_APROBADOR → procesamiento y cierre planilla
--
-- Roles que NO reciben PLA_WRITE (intencional):
--   RRHH_CONSULTA → solo lectura por diseño (descripción: "solo lectura")
--   PORTAL_PAPELETAS_ROLES → autoservicio, sin acceso a planilla operativa
--
-- Idempotente: MERGE no duplica si ya existe.
-- Ejecutar en GESTIONRRHH / Oracle 19c+.
-- ============================================================================

SET DEFINE OFF;

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

COMMIT;

PROMPT V010_62 — PLA_WRITE asignado a roles RRHH/PLANILLA/ADMIN.
