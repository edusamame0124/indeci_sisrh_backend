-- ============================================================================
-- Rollback V010_93 — quita los topes/alertas/reglas de INDECI_IR4TA_CONFIG_ANUAL
--
-- Revierte únicamente lo introducido por V010_93 (columnas + checks). NO toca
-- las columnas originales de V010_76 (UIT, tasa, base inafecta, etc.).
--
-- Ejecutar conectado como GESTIONRRHH. Revisar en entornos compartidos.
-- ============================================================================

SET SERVEROUTPUT ON;

DECLARE
    PROCEDURE drop_constraint_if_exists(p_name VARCHAR2) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*)
          INTO v_exists
          FROM ALL_CONSTRAINTS
         WHERE OWNER           = 'GESTIONRRHH'
           AND TABLE_NAME      = 'INDECI_IR4TA_CONFIG_ANUAL'
           AND CONSTRAINT_NAME = p_name;

        IF v_exists > 0 THEN
            EXECUTE IMMEDIATE
                'ALTER TABLE GESTIONRRHH.INDECI_IR4TA_CONFIG_ANUAL DROP CONSTRAINT ' || p_name;
            DBMS_OUTPUT.PUT_LINE(p_name || ' -> eliminado.');
        ELSE
            DBMS_OUTPUT.PUT_LINE(p_name || ' no existe. Sin cambios.');
        END IF;
    END;

    PROCEDURE drop_column_if_exists(p_col VARCHAR2) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*)
          INTO v_exists
          FROM ALL_TAB_COLUMNS
         WHERE OWNER       = 'GESTIONRRHH'
           AND TABLE_NAME  = 'INDECI_IR4TA_CONFIG_ANUAL'
           AND COLUMN_NAME = p_col;

        IF v_exists > 0 THEN
            EXECUTE IMMEDIATE
                'ALTER TABLE GESTIONRRHH.INDECI_IR4TA_CONFIG_ANUAL DROP COLUMN ' || p_col;
            DBMS_OUTPUT.PUT_LINE(p_col || ' -> eliminada.');
        ELSE
            DBMS_OUTPUT.PUT_LINE(p_col || ' no existe. Sin cambios.');
        END IF;
    END;
BEGIN
    drop_constraint_if_exists('INDECI_IR4TA_CFG_TOPEGEN_CK');
    drop_constraint_if_exists('INDECI_IR4TA_CFG_TOPEDIR_CK');
    drop_constraint_if_exists('INDECI_IR4TA_CFG_APLGEN_CK');
    drop_constraint_if_exists('INDECI_IR4TA_CFG_APLDIR_CK');
    drop_constraint_if_exists('INDECI_IR4TA_CFG_PCTPREV_CK');
    drop_constraint_if_exists('INDECI_IR4TA_CFG_PCTCRIT_CK');
    drop_constraint_if_exists('INDECI_IR4TA_CFG_FLGCALC_CK');
    drop_constraint_if_exists('INDECI_IR4TA_CFG_FLGA80_CK');
    drop_constraint_if_exists('INDECI_IR4TA_CFG_FLGA90_CK');
    drop_constraint_if_exists('INDECI_IR4TA_CFG_FLGVAL_CK');
    drop_constraint_if_exists('INDECI_IR4TA_CFG_FLGAUTO_CK');

    drop_column_if_exists('TOPE_ANUAL_GENERAL');
    drop_column_if_exists('TOPE_ANUAL_DIRECTOR');
    drop_column_if_exists('APLICA_CAS_GENERAL');
    drop_column_if_exists('APLICA_CAS_DIRECTOR');
    drop_column_if_exists('PCT_ALERTA_PREV');
    drop_column_if_exists('PCT_ALERTA_CRIT');
    drop_column_if_exists('CODIGO_SUNAT_PLAME');
    drop_column_if_exists('FLG_CALC_ACUMULADO');
    drop_column_if_exists('FLG_ALERTA_80');
    drop_column_if_exists('FLG_ALERTA_90');
    drop_column_if_exists('FLG_MARCAR_VALIDACION');
    drop_column_if_exists('FLG_RETENCION_AUTO');

    COMMIT;
    DBMS_OUTPUT.PUT_LINE('V010_93 rollback finalizado.');
END;
/
