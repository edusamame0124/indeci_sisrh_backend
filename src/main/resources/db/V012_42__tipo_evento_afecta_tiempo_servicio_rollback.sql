-- ============================================================================
-- ROLLBACK V012_42 — Revierte columna AFECTA_TIEMPO_SERVICIO y catálogo histórico.
-- ============================================================================
SET SERVEROUTPUT ON;

BEGIN
    DELETE FROM GESTIONRRHH.INDECI_EMPLEADO_EVENTO
     WHERE TIPO_EVENTO_ID IN (
        SELECT ID FROM GESTIONRRHH.INDECI_TIPO_EVENTO
         WHERE CODIGO IN ('FALTA_HISTORICA','SUSPENSION_HISTORICA'));

    DELETE FROM GESTIONRRHH.INDECI_TIPO_EVENTO
     WHERE CODIGO IN ('FALTA_HISTORICA','SUSPENSION_HISTORICA');

    COMMIT;
    DBMS_OUTPUT.PUT_LINE('Catálogo histórico (FALTA_HISTORICA/SUSPENSION_HISTORICA) y sus eventos eliminados.');
END;
/

DECLARE
    v_exists NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_exists
      FROM user_constraints
     WHERE constraint_name = 'INDECI_TIPO_EVENTO_AFEC_TS_CK';
    IF v_exists > 0 THEN
        EXECUTE IMMEDIATE
            'ALTER TABLE GESTIONRRHH.INDECI_TIPO_EVENTO DROP CONSTRAINT INDECI_TIPO_EVENTO_AFEC_TS_CK';
        DBMS_OUTPUT.PUT_LINE('CHECK INDECI_TIPO_EVENTO_AFEC_TS_CK eliminado.');
    END IF;

    SELECT COUNT(*) INTO v_exists
      FROM all_tab_columns
     WHERE owner = 'GESTIONRRHH' AND table_name = 'INDECI_TIPO_EVENTO'
       AND column_name = 'AFECTA_TIEMPO_SERVICIO';
    IF v_exists > 0 THEN
        EXECUTE IMMEDIATE
            'ALTER TABLE GESTIONRRHH.INDECI_TIPO_EVENTO DROP COLUMN AFECTA_TIEMPO_SERVICIO';
        DBMS_OUTPUT.PUT_LINE('Columna AFECTA_TIEMPO_SERVICIO eliminada.');
    END IF;

    DBMS_OUTPUT.PUT_LINE('Rollback V012_42 listo.');
END;
/
