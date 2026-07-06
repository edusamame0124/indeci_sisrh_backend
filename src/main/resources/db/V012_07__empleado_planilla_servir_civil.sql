-- ============================================================
-- V012_07 — Ley 30057: grupo de servidor civil + condición de confianza
--
-- Campos específicos del régimen SERVIR (30057). La confianza NO es un grupo
-- aparte: es una condición asociada al vínculo. Se derivan/muestran dinámicamente
-- por régimen (no en un selector universal).
--
-- Idempotente: solo agrega columnas si faltan.
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
    add_col_si_falta('GRUPO_SERVIDOR_CIVIL',
        'ALTER TABLE GESTIONRRHH.INDECI_EMPLEADO_PLANILLA ADD (GRUPO_SERVIDOR_CIVIL VARCHAR2(60))');
    add_col_si_falta('ES_CONFIANZA',
        'ALTER TABLE GESTIONRRHH.INDECI_EMPLEADO_PLANILLA ADD (ES_CONFIANZA NUMBER(1) DEFAULT 0)');

    DBMS_OUTPUT.PUT_LINE('V012_07 finalizado correctamente.');
EXCEPTION
    WHEN OTHERS THEN
        DBMS_OUTPUT.PUT_LINE('ERROR en V012_07: ' || SQLERRM);
        RAISE;
END;
/
