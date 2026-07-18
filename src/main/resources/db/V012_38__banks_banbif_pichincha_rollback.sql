-- ============================================================================
-- V012_38 ROLLBACK — quita BANBIF y BANCO PICHINCHA
--
-- Solo los elimina si ninguna cuenta de empleado los referencia: si ya se
-- importaron trabajadores con esos bancos, las filas se conservan.
-- ============================================================================
SET SERVEROUTPUT ON;

DELETE FROM GESTIONRRHH.BANKS b
 WHERE UPPER(TRIM(b.CODE)) IN ('BANBIF', 'PICHINCHA')
   AND NOT EXISTS (SELECT 1
                     FROM GESTIONRRHH.INDECI_EMPLEADO_BANCO eb
                    WHERE eb.BANK_ID = b.ID);

COMMIT;
