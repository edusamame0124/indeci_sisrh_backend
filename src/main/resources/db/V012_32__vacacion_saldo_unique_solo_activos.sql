-- ============================================================================
-- V012_32 — "Provisionar Auto" (soft-delete + insert): la unicidad (EMPLEADO_ID,
-- ANIO) debe aplicar SOLO a las filas activas. El recálculo mantiene la fila
-- histórica anulada (ACTIVO=0) y crea una nueva (ACTIVO=1) para el mismo año,
-- lo que viola la vieja UNIQUE(EMPLEADO_ID, ANIO) que ignora ACTIVO.
--
-- Se reemplazan las dos constraints heredadas (INDECI_VAC_SALDO_UK de V010_19 y
-- UK_VACACION_SALDO_EMP_ANIO de V012_21 — ambas UNIQUE(EMPLEADO_ID, ANIO)) por
-- un ÍNDICE ÚNICO PARCIAL: las expresiones CASE devuelven NULL cuando ACTIVO<>1,
-- y Oracle NO indexa filas con todas las columnas NULL, por lo que la unicidad
-- rige únicamente sobre las filas activas (se permiten N filas anuladas por año).
-- ============================================================================
SET SERVEROUTPUT ON

DECLARE
    l_tbs VARCHAR2(128);

    PROCEDURE drop_constraint_if_exists(p_constraint IN VARCHAR2) IS
        l_cnt NUMBER;
    BEGIN
        SELECT COUNT(*) INTO l_cnt
        FROM all_constraints
        WHERE owner = 'GESTIONRRHH'
          AND table_name = 'INDECI_VACACION_SALDO'
          AND constraint_name = p_constraint;
        IF l_cnt > 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE GESTIONRRHH.INDECI_VACACION_SALDO DROP CONSTRAINT ' || p_constraint;
            DBMS_OUTPUT.PUT_LINE('Constraint ' || p_constraint || ' eliminada.');
        ELSE
            DBMS_OUTPUT.PUT_LINE('Constraint ' || p_constraint || ' no existe (omitida).');
        END IF;
    END;

    PROCEDURE drop_index_if_exists(p_index IN VARCHAR2) IS
        l_cnt NUMBER;
    BEGIN
        SELECT COUNT(*) INTO l_cnt
        FROM all_indexes
        WHERE owner = 'GESTIONRRHH' AND index_name = p_index;
        IF l_cnt > 0 THEN
            EXECUTE IMMEDIATE 'DROP INDEX GESTIONRRHH.' || p_index;
            DBMS_OUTPUT.PUT_LINE('Index ' || p_index || ' eliminado.');
        END IF;
    END;
BEGIN
    -- 1) Quitar las UNIQUE(EMPLEADO_ID, ANIO) que ignoran ACTIVO.
    drop_constraint_if_exists('INDECI_VAC_SALDO_UK');
    drop_constraint_if_exists('UK_VACACION_SALDO_EMP_ANIO');

    -- 2) Índice único parcial (solo filas activas). Idempotente. El índice hereda el
    --    tablespace de la PROPIA tabla (que existe con seguridad); no se deja al default
    --    del usuario, que en algunos entornos apunta a un tablespace inexistente (ORA-00959).
    SELECT tablespace_name INTO l_tbs
    FROM all_tables
    WHERE owner = 'GESTIONRRHH' AND table_name = 'INDECI_VACACION_SALDO';

    drop_index_if_exists('INDECI_VAC_SALDO_ACT_UK');
    EXECUTE IMMEDIATE
        'CREATE UNIQUE INDEX GESTIONRRHH.INDECI_VAC_SALDO_ACT_UK '
        || 'ON GESTIONRRHH.INDECI_VACACION_SALDO ('
        || '  CASE WHEN ACTIVO = 1 THEN EMPLEADO_ID END, '
        || '  CASE WHEN ACTIVO = 1 THEN ANIO END)'
        || CASE WHEN l_tbs IS NOT NULL THEN ' TABLESPACE ' || l_tbs ELSE '' END;
    DBMS_OUTPUT.PUT_LINE('Index único parcial INDECI_VAC_SALDO_ACT_UK creado (solo ACTIVO=1), tablespace ' || NVL(l_tbs, '<default>') || '.');
END;
/
