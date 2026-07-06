-- ============================================================
-- ROLLBACK V012_09 — Gratificación CAS (Ley 32563)
--
-- Revierte: borra los 2 conceptos operativos sembrados (0077/0025) y las reglas
-- de beneficio CAS, y elimina la tabla INDECI_REGLA_BENEFICIO_CAS.
-- NO toca movimientos ni períodos históricos. Idempotente.
-- ============================================================

SET SERVEROUTPUT ON;

DECLARE
    v_count NUMBER;
BEGIN
    -- 1) Conceptos operativos sembrados por V012_09 (solo si no fueron usados).
    DELETE FROM GESTIONRRHH.INDECI_CONCEPTO_PLANILLA
     WHERE CODIGO_MEF IN ('0077', '0025')
       AND NOT EXISTS (
           SELECT 1 FROM GESTIONRRHH.INDECI_MOVIMIENTO_PLANILLA_DET dt
            WHERE dt.CONCEPTO_PLANILLA_ID = GESTIONRRHH.INDECI_CONCEPTO_PLANILLA.ID);
    DBMS_OUTPUT.PUT_LINE('Conceptos gratificación CAS eliminados (no usados): ' || SQL%ROWCOUNT);

    -- 2) Tabla de reglas.
    SELECT COUNT(*) INTO v_count FROM USER_TABLES
     WHERE TABLE_NAME = 'INDECI_REGLA_BENEFICIO_CAS';
    IF v_count > 0 THEN
        EXECUTE IMMEDIATE 'DROP TABLE GESTIONRRHH.INDECI_REGLA_BENEFICIO_CAS PURGE';
        DBMS_OUTPUT.PUT_LINE('INDECI_REGLA_BENEFICIO_CAS -> eliminada.');
    ELSE
        DBMS_OUTPUT.PUT_LINE('INDECI_REGLA_BENEFICIO_CAS no existe. Sin cambios.');
    END IF;

    COMMIT;
    DBMS_OUTPUT.PUT_LINE('ROLLBACK V012_09 finalizado.');
EXCEPTION
    WHEN OTHERS THEN
        DBMS_OUTPUT.PUT_LINE('ERROR en rollback V012_09: ' || SQLERRM);
        RAISE;
END;
/
