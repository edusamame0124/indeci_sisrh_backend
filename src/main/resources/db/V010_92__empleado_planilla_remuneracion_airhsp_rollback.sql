-- ============================================================================
-- Rollback V010_92 — empleado_planilla_remuneracion_airhsp
--
-- Revierte únicamente lo introducido por V010_92:
--   - Seeds INCREMENTO_DS_*
--   - Índice IDX_EMP_PLANILLA_AIRHSP
--   - Constraints CHK_AIRHSP_FORMATO, CHK_MONTO_CONTRATO_POS
--   - NOT NULL en CODIGO_AIRHSP (vuelve a nullable)
--
-- NO elimina columnas CODIGO_AIRHSP ni MONTO_CONTRATO (pueden preexistir /
-- V010_36). NO toca parámetros DS_* legacy de V010_13.
--
-- Ejecutar conectado como GESTIONRRHH. Revisar en entornos compartidos.
-- ============================================================================

SET SERVEROUTPUT ON;

DECLARE
    v_deleted NUMBER;
BEGIN
    DELETE FROM GESTIONRRHH.INDECI_PARAMETRO_REMUNERATIVO
     WHERE CODIGO_PARAMETRO IN (
           'INCREMENTO_DS_311_2022',
           'INCREMENTO_DS_313_2023',
           'INCREMENTO_DS_265_2024',
           'INCREMENTO_DS_279_2024',
           'INCREMENTO_DS_327_2025'
         )
       AND ANIO_FISCAL = 2026
       AND REGIMEN_LABORAL_ID IS NULL;

    v_deleted := SQL%ROWCOUNT;
    DBMS_OUTPUT.PUT_LINE('Seeds INCREMENTO_DS_* eliminados: ' || v_deleted || ' fila(s).');
END;
/

-- Índice
DECLARE
    v_exists NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO v_exists
      FROM ALL_INDEXES
     WHERE OWNER      = 'GESTIONRRHH'
       AND INDEX_NAME = 'IDX_EMP_PLANILLA_AIRHSP';

    IF v_exists > 0 THEN
        EXECUTE IMMEDIATE 'DROP INDEX GESTIONRRHH.IDX_EMP_PLANILLA_AIRHSP';
        DBMS_OUTPUT.PUT_LINE('IDX_EMP_PLANILLA_AIRHSP -> eliminado.');
    ELSE
        DBMS_OUTPUT.PUT_LINE('IDX_EMP_PLANILLA_AIRHSP no existe. Sin cambios.');
    END IF;
END;
/

-- Constraints + NOT NULL
DECLARE
    v_nullable VARCHAR2(1);

    PROCEDURE drop_constraint_if_exists(p_name VARCHAR2) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*)
          INTO v_exists
          FROM ALL_CONSTRAINTS
         WHERE OWNER           = 'GESTIONRRHH'
           AND TABLE_NAME      = 'INDECI_EMPLEADO_PLANILLA'
           AND CONSTRAINT_NAME = p_name;

        IF v_exists > 0 THEN
            EXECUTE IMMEDIATE
                'ALTER TABLE GESTIONRRHH.INDECI_EMPLEADO_PLANILLA '
                || 'DROP CONSTRAINT ' || p_name;
            DBMS_OUTPUT.PUT_LINE(p_name || ' -> eliminado.');
        ELSE
            DBMS_OUTPUT.PUT_LINE(p_name || ' no existe. Sin cambios.');
        END IF;
    END;
BEGIN
    drop_constraint_if_exists('CHK_AIRHSP_FORMATO');
    drop_constraint_if_exists('CHK_MONTO_CONTRATO_POS');

    BEGIN
        SELECT NULLABLE
          INTO v_nullable
          FROM ALL_TAB_COLUMNS
         WHERE OWNER       = 'GESTIONRRHH'
           AND TABLE_NAME  = 'INDECI_EMPLEADO_PLANILLA'
           AND COLUMN_NAME = 'CODIGO_AIRHSP';

        IF v_nullable = 'N' THEN
            EXECUTE IMMEDIATE
                'ALTER TABLE GESTIONRRHH.INDECI_EMPLEADO_PLANILLA '
                || 'MODIFY (CODIGO_AIRHSP NULL)';
            DBMS_OUTPUT.PUT_LINE('CODIGO_AIRHSP -> nullable restaurado.');
        ELSE
            DBMS_OUTPUT.PUT_LINE('CODIGO_AIRHSP ya era nullable. Sin cambios.');
        END IF;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            DBMS_OUTPUT.PUT_LINE('Aviso: columna CODIGO_AIRHSP no encontrada.');
    END;

    DBMS_OUTPUT.PUT_LINE('V010_92 rollback finalizado.');
END;
/

COMMIT;