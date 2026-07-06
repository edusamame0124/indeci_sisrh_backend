-- ============================================================
-- V012_04 — Vinculación laboral: datos de cese
--
-- Agrega el motivo y documento de cese al vínculo (INDECI_EMPLEADO_PLANILLA).
-- Las fechas y el estado ya existen (FECHA_FIN, FECHA_CESE, ESTADO_LABORAL);
-- aquí se completan los "hechos" que RR.HH. registra para que el sistema derive
-- el estado del vínculo (PROGRAMADO/VIGENTE/VENCIDO_PENDIENTE/CESADO/ANULADO).
-- El estado NO se guarda como campo editable: se deriva en el servicio.
--
-- Idempotente: solo agrega columnas si no existen.
-- ============================================================

SET SERVEROUTPUT ON;

DECLARE
    PROCEDURE add_col_si_falta(p_col VARCHAR2, p_ddl VARCHAR2) IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count
          FROM USER_TAB_COLUMNS
         WHERE TABLE_NAME = 'INDECI_EMPLEADO_PLANILLA'
           AND COLUMN_NAME = UPPER(p_col);
        IF v_count = 0 THEN
            EXECUTE IMMEDIATE p_ddl;
            DBMS_OUTPUT.PUT_LINE(p_col || ' -> columna agregada.');
        ELSE
            DBMS_OUTPUT.PUT_LINE(p_col || ' ya existe. Sin cambios.');
        END IF;
    END;
BEGIN
    add_col_si_falta('MOTIVO_CESE',
        'ALTER TABLE GESTIONRRHH.INDECI_EMPLEADO_PLANILLA ADD (MOTIVO_CESE VARCHAR2(120))');
    add_col_si_falta('DOCUMENTO_CESE',
        'ALTER TABLE GESTIONRRHH.INDECI_EMPLEADO_PLANILLA ADD (DOCUMENTO_CESE VARCHAR2(200))');

    DBMS_OUTPUT.PUT_LINE('V012_04 finalizado correctamente.');
EXCEPTION
    WHEN OTHERS THEN
        DBMS_OUTPUT.PUT_LINE('ERROR en V012_04: ' || SQLERRM);
        RAISE;
END;
/
