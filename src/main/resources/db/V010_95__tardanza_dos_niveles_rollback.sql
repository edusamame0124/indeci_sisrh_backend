-- ============================================================================
-- Rollback V010_95 — quita las columnas de tardanza de dos niveles.
--
-- Revierte únicamente lo introducido por V010_95 (2 columnas en JORNADA_REGIMEN
-- + 5 agregados en ASISTENCIA_CABECERA). NO toca columnas previas.
--
-- Ejecutar conectado como GESTIONRRHH. Revisar en entornos compartidos.
-- ============================================================================

SET SERVEROUTPUT ON;

DECLARE
    PROCEDURE drop_column_if_exists(p_table VARCHAR2, p_col VARCHAR2) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*)
          INTO v_exists
          FROM ALL_TAB_COLUMNS
         WHERE OWNER       = 'GESTIONRRHH'
           AND TABLE_NAME  = p_table
           AND COLUMN_NAME = p_col;

        IF v_exists > 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE GESTIONRRHH.' || p_table || ' DROP COLUMN ' || p_col;
            DBMS_OUTPUT.PUT_LINE(p_table || '.' || p_col || ' -> eliminada.');
        ELSE
            DBMS_OUTPUT.PUT_LINE(p_table || '.' || p_col || ' no existe. Sin cambios.');
        END IF;
    END;
BEGIN
    drop_column_if_exists('INDECI_JORNADA_REGIMEN',     'UMBRAL_TARDANZA_DIARIA_MIN');
    drop_column_if_exists('INDECI_JORNADA_REGIMEN',     'TOPE_TARDANZA_MENSUAL_MIN');

    drop_column_if_exists('INDECI_ASISTENCIA_CABECERA', 'MIN_TARDANZA_DIARIA');
    drop_column_if_exists('INDECI_ASISTENCIA_CABECERA', 'MIN_TARDANZA_MENOR_ACUM');
    drop_column_if_exists('INDECI_ASISTENCIA_CABECERA', 'MIN_TARDANZA_EXCESO_MES');
    drop_column_if_exists('INDECI_ASISTENCIA_CABECERA', 'DESCUENTO_TARDANZA_DIARIA');
    drop_column_if_exists('INDECI_ASISTENCIA_CABECERA', 'DESCUENTO_TARDANZA_MENSUAL');

    DBMS_OUTPUT.PUT_LINE('V010_95 rollback finalizado.');
END;
/
