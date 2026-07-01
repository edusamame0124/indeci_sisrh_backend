-- ============================================================
-- V012_03 — Persistir días laborados netos en el movimiento de planilla
--
-- Motivo: la "columna Días" de la lista de movimientos y de la boleta oficial
--         mostraba 30 fijo (hardcode) aunque el trabajador tuviera faltas o
--         eventos que afectan días laborados. El motor SÍ calcula el valor real
--         (30 − faltas − eventos) pero no lo persistía. Esta columna guarda ese
--         valor para que lista y boleta lean el dato correcto.
--
-- Nullable a propósito: los movimientos anteriores a esta migración quedan con
-- DIAS_LABORADOS NULL y las pantallas caen al fallback 30 hasta que se regeneren.
--
-- Idempotente: solo agrega la columna si no existe.
-- ============================================================

SET SERVEROUTPUT ON;

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count
      FROM USER_TAB_COLUMNS
     WHERE TABLE_NAME  = 'INDECI_MOVIMIENTO_PLANILLA'
       AND COLUMN_NAME = 'DIAS_LABORADOS';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE
            'ALTER TABLE GESTIONRRHH.INDECI_MOVIMIENTO_PLANILLA '
            || 'ADD (DIAS_LABORADOS NUMBER(3))';
        DBMS_OUTPUT.PUT_LINE('DIAS_LABORADOS -> columna agregada.');
    ELSE
        DBMS_OUTPUT.PUT_LINE('DIAS_LABORADOS ya existe. Sin cambios.');
    END IF;

    DBMS_OUTPUT.PUT_LINE('V012_03 finalizado correctamente.');
EXCEPTION
    WHEN OTHERS THEN
        DBMS_OUTPUT.PUT_LINE('ERROR en V012_03: ' || SQLERRM);
        RAISE;
END;
/
