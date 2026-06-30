-- ==============================================================================
-- HU-02: Fix tamaño de columna PERIODO
-- Se requiere longitud 7 ("YYYY-MM") en lugar de 6.
-- ==============================================================================

BEGIN
    EXECUTE IMMEDIATE 'ALTER TABLE GESTIONRRHH.INDECI_PLANILLA_LOTE MODIFY PERIODO VARCHAR2(7)';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -942 THEN
            RAISE;
        END IF;
END;
/
