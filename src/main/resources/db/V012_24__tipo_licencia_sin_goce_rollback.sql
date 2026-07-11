-- Rollback V012_24 — quita flags de INDECI_TIPO_LICENCIA y los tipos sembrados.
-- NO borra tipos que ya estuvieran referenciados por papeletas (solo los sembrados aquí).
SET SERVEROUTPUT ON;
DECLARE
    PROCEDURE drop_col(p_column VARCHAR2) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_exists FROM ALL_TAB_COLUMNS
         WHERE OWNER='GESTIONRRHH' AND TABLE_NAME='INDECI_TIPO_LICENCIA' AND COLUMN_NAME=p_column;
        IF v_exists > 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE GESTIONRRHH.INDECI_TIPO_LICENCIA DROP COLUMN '||p_column;
        END IF;
    END;
BEGIN
    DELETE FROM GESTIONRRHH.INDECI_TIPO_LICENCIA
     WHERE CODIGO IN ('LIC_SIN_PAR','LIC_SIN_CAP','LIC_SIN_CON','LIC_SIN_ENF','LIC_SIN_PUB')
       AND NOT EXISTS (SELECT 1 FROM GESTIONRRHH.INDECI_SOLICITUD_RRHH s
                        WHERE s.TIPO_LICENCIA_ID = INDECI_TIPO_LICENCIA.ID);
    drop_col('ES_SIN_GOCE');
    drop_col('REQUIERE_RESOLUCION');
    drop_col('COD_PLAME_SUNAT');
    COMMIT;
END;
/
