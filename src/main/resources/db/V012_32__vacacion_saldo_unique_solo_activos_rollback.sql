-- Rollback V012_32 — restaura la UNIQUE(EMPLEADO_ID, ANIO) global (sin ACTIVO).
-- ADVERTENCIA: si ya existen filas anuladas (ACTIVO=0) duplicadas por año, la
-- constraint global fallará al recrearse; primero habría que depurar esas filas.
SET SERVEROUTPUT ON

DECLARE
    PROCEDURE drop_index_if_exists(p_index IN VARCHAR2) IS
        l_cnt NUMBER;
    BEGIN
        SELECT COUNT(*) INTO l_cnt
        FROM all_indexes
        WHERE owner = 'GESTIONRRHH' AND index_name = p_index;
        IF l_cnt > 0 THEN
            EXECUTE IMMEDIATE 'DROP INDEX GESTIONRRHH.' || p_index;
        END IF;
    END;
BEGIN
    drop_index_if_exists('INDECI_VAC_SALDO_ACT_UK');
    BEGIN
        EXECUTE IMMEDIATE
            'ALTER TABLE GESTIONRRHH.INDECI_VACACION_SALDO '
            || 'ADD CONSTRAINT INDECI_VAC_SALDO_UK UNIQUE (EMPLEADO_ID, ANIO)';
    EXCEPTION
        WHEN OTHERS THEN
            IF SQLCODE != -2261 THEN RAISE; END IF;
    END;
END;
/
