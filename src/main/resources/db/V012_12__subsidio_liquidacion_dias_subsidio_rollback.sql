-- Rollback F1.8 — elimina DIAS_SUBSIDIO de INDECI_SUBSIDIO_LIQUIDACION.
-- Idempotente: no falla si la columna no existe.
DECLARE
    v_existe NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_existe
      FROM user_tab_columns
     WHERE table_name = 'INDECI_SUBSIDIO_LIQUIDACION'
       AND column_name = 'DIAS_SUBSIDIO';

    IF v_existe = 1 THEN
        EXECUTE IMMEDIATE
            'ALTER TABLE INDECI_SUBSIDIO_LIQUIDACION DROP COLUMN DIAS_SUBSIDIO';
    END IF;
END;
/
