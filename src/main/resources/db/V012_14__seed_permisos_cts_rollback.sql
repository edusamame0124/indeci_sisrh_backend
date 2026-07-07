-- ============================================================================
-- V012_14 ROLLBACK — Elimina los permisos PLA_CTS_* y sus asignaciones.
-- Orden: primero las asignaciones (SS_ROL_PERMISO), luego el permiso maestro.
-- Idempotente. GESTIONRRHH / Oracle 19c+.
-- ============================================================================

SET SERVEROUTPUT ON;
SET DEFINE OFF;

DELETE FROM GESTIONRRHH.SS_ROL_PERMISO
 WHERE ID_PERMISO IN (
     SELECT ID_PERMISO FROM GESTIONRRHH.SS_PERMISO
      WHERE UPPER(CODIGO) IN ('PLA_CTS_READ', 'PLA_CTS_WRITE', 'PLA_CTS_APPROVE')
 );

DELETE FROM GESTIONRRHH.SS_PERMISO
 WHERE UPPER(CODIGO) IN ('PLA_CTS_READ', 'PLA_CTS_WRITE', 'PLA_CTS_APPROVE');

COMMIT;

PROMPT V012_14 ROLLBACK — Permisos PLA_CTS_* removidos.
