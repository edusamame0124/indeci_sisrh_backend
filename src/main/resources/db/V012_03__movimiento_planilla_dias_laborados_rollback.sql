-- ============================================================
-- ROLLBACK V012_03 — Elimina la columna DIAS_LABORADOS.
-- ADVERTENCIA: se pierde el dato de días laborados persistido; la lista y la
-- boleta vuelven a mostrar 30 fijo. Usar solo en reversión controlada.
-- Idempotente: solo elimina la columna si existe.
-- ============================================================

SET SERVEROUTPUT ON;

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count
      FROM USER_TAB_COLUMNS
     WHERE TABLE_NAME  = 'INDECI_MOVIMIENTO_PLANILLA'
       AND COLUMN_NAME = 'DIAS_LABORADOS';

    IF v_count > 0 THEN
        EXECUTE IMMEDIATE
            'ALTER TABLE GESTIONRRHH.INDECI_MOVIMIENTO_PLANILLA DROP COLUMN DIAS_LABORADOS';
        DBMS_OUTPUT.PUT_LINE('DIAS_LABORADOS -> columna eliminada.');
    ELSE
        DBMS_OUTPUT.PUT_LINE('DIAS_LABORADOS no existe. Sin cambios.');
    END IF;

    DBMS_OUTPUT.PUT_LINE('Rollback V012_03 finalizado.');
EXCEPTION
    WHEN OTHERS THEN
        DBMS_OUTPUT.PUT_LINE('ERROR en rollback V012_03: ' || SQLERRM);
        RAISE;
END;
/
