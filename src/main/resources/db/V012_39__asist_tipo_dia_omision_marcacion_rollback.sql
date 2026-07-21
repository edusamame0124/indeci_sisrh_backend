-- ============================================================================
-- Rollback V012_39 — quita OMISION_MARCACION y ASISTENCIA_JUSTIFICADA del
-- CHECK INDECI_ASIST_DET_TIPO_CK (vuelve a la lista de V012_35).
--
-- IMPORTANTE: si ya existen filas con TIPO_DIA en esos dos valores, el ADD del
-- CHECK fallara (ORA-02293). Primero reconvierta/limpie esas filas.
-- Ejecutar en esquema GESTIONRRHH / Oracle 19c+.
-- ============================================================================
SET SERVEROUTPUT ON;

DECLARE
    PROCEDURE drop_constraint_if_exists(p_table VARCHAR2, p_cons VARCHAR2) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_exists FROM ALL_CONSTRAINTS
         WHERE OWNER = 'GESTIONRRHH' AND TABLE_NAME = p_table AND CONSTRAINT_NAME = p_cons;
        IF v_exists > 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE GESTIONRRHH.' || p_table || ' DROP CONSTRAINT ' || p_cons;
            DBMS_OUTPUT.PUT_LINE(p_cons || ' -> eliminado.');
        END IF;
    END;
BEGIN
    drop_constraint_if_exists('INDECI_ASISTENCIA_DETALLE', 'INDECI_ASIST_DET_TIPO_CK');
    EXECUTE IMMEDIATE
        'ALTER TABLE GESTIONRRHH.INDECI_ASISTENCIA_DETALLE ADD CONSTRAINT INDECI_ASIST_DET_TIPO_CK '
        || 'CHECK (TIPO_DIA IN (''LABORAL'', ''FALTA'', ''TARDANZA'', ''LICENCIA'', ''VACACIONES'', ''DESCANSO'', ''FERIADO'', ''OBSERVADO'', ''SANCION_PAD'', ''TELETRABAJO'', ''PERMISO''))';
    DBMS_OUTPUT.PUT_LINE('Rollback V012_39 finalizado.');
END;
/
