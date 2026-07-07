-- ============================================================================
-- V012_17 ROLLBACK — Revierte SOLO la asignación de ADM_META a GESTOR_USUARIOS.
--
-- No borra el permiso ADM_META de SS_PERMISO ni lo quita de SUPER_ADMIN/ADMIN_TI/
-- ADMIN: esos roles debían tenerlo desde V001 (fase 1); este script solo creó lo
-- que faltaba. Deshacer eso rompería el catálogo de Roles/Permisos para TI.
-- Idempotente. Ejecutar en GESTIONRRHH.
-- ============================================================================

SET SERVEROUTPUT ON;
SET DEFINE OFF;

DELETE FROM GESTIONRRHH.SS_ROL_PERMISO
 WHERE ID_ROL     IN (SELECT ID_ROL     FROM GESTIONRRHH.SS_ROL     WHERE UPPER(CODIGO) = 'GESTOR_USUARIOS')
   AND ID_PERMISO IN (SELECT ID_PERMISO FROM GESTIONRRHH.SS_PERMISO WHERE UPPER(CODIGO) = 'ADM_META');

COMMIT;

PROMPT V012_17 rollback: ADM_META desasignado de GESTOR_USUARIOS.
