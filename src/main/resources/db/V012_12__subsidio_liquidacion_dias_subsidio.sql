-- =====================================================================
-- F1.8 — Fix Prorrateo de Treintavos por Subsidio
-- Agrega DIAS_SUBSIDIO a INDECI_SUBSIDIO_LIQUIDACION para que el módulo de
-- subsidios provea los días vía contrato de dominio (diasSubsidioMotor) y el
-- motor reduzca el haber ordinario (regla Contraloría, divisor 1/30).
-- Idempotente: no falla si la columna ya existe (re-ejecución segura).
-- =====================================================================
DECLARE
    v_existe NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_existe
      FROM user_tab_columns
     WHERE table_name = 'INDECI_SUBSIDIO_LIQUIDACION'
       AND column_name = 'DIAS_SUBSIDIO';

    IF v_existe = 0 THEN
        EXECUTE IMMEDIATE
            'ALTER TABLE INDECI_SUBSIDIO_LIQUIDACION ADD (DIAS_SUBSIDIO NUMBER(3))';
    END IF;
END;
/
