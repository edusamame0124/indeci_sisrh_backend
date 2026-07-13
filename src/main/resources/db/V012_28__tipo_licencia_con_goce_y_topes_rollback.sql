-- ============================================================================
-- ROLLBACK V012_28 — Reestructuración de Licencias (con goce / sin goce).
--
-- Revierte:
--   1) Desactiva los 13 motivos CON GOCE y los 2 SIN GOCE sembrados por V012_28.
--   2) Reactiva los 5 subtipos SIN GOCE legacy (V012_24).
--   3) Deja la columna MAX_DIAS (no destructivo; borrarla es opcional y manual).
--
-- Idempotente. No borra papeletas históricas. Oracle 19c+ / GESTIONRRHH.
-- ============================================================================

SET SERVEROUTPUT ON;

BEGIN
    -- 1) Desactivar lo sembrado por V012_28.
    EXECUTE IMMEDIATE q'[UPDATE GESTIONRRHH.INDECI_TIPO_LICENCIA
                            SET ACTIVO = 0
                          WHERE CODIGO IN (
                                'LIC_CG_ENF','LIC_CG_INV','LIC_CG_MAT','LIC_CG_PAT','LIC_CG_ADO',
                                'LIC_CG_FAL','LIC_CG_SIN','LIC_CG_CUI','LIC_CG_CAP','LIC_CG_EDI',
                                'LIC_CG_ONO','LIC_CG_DIS','LIC_CG_LEY',
                                'LIC_SIN_JUS','LIC_SIN_OTR')]';

    -- 2) Reactivar los subtipos SIN GOCE legacy.
    EXECUTE IMMEDIATE q'[UPDATE GESTIONRRHH.INDECI_TIPO_LICENCIA
                            SET ACTIVO = 1
                          WHERE CODIGO IN ('LIC_SIN_PAR','LIC_SIN_CAP','LIC_SIN_CON','LIC_SIN_ENF','LIC_SIN_PUB')]';

    -- 3) La columna MAX_DIAS se conserva (no destructivo). Para eliminarla, ejecutar manualmente:
    --    ALTER TABLE GESTIONRRHH.INDECI_TIPO_LICENCIA DROP COLUMN MAX_DIAS;

    COMMIT;
    DBMS_OUTPUT.PUT_LINE('ROLLBACK V012_28 finalizado.');
END;
/
