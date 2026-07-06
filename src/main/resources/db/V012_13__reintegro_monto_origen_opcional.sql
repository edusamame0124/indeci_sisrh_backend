-- =====================================================================
-- F2/BLOQUE 2 — Origen OPCIONAL en INDECI_REINTEGRO_MONTO
-- Los devengados judiciales / reposición no tienen movimiento origen; su
-- trazabilidad es el N° de resolución (SUSTENTO). Se relajan a NULL las
-- columnas de origen. Idempotente: solo modifica si están en NOT NULL.
-- =====================================================================
DECLARE
    PROCEDURE relajar(p_col IN VARCHAR2) IS
        v_notnull NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_notnull
          FROM user_tab_columns
         WHERE table_name = 'INDECI_REINTEGRO_MONTO'
           AND column_name = p_col
           AND nullable = 'N';
        IF v_notnull = 1 THEN
            EXECUTE IMMEDIATE
                'ALTER TABLE INDECI_REINTEGRO_MONTO MODIFY (' || p_col || ' NULL)';
        END IF;
    END;
BEGIN
    relajar('PERIODO_ORIGEN');
    relajar('MOVIMIENTO_ORIGEN_ID');
    relajar('CONCEPTO_ORIGEN_CODIGO');
END;
/
