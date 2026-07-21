-- ============================================================================
-- Spec M04 / V012_40 — ORIGEN 'PAPELETA' y 'CALENDARIO' en INDECI_ASISTENCIA_DETALLE
--
-- OBJETIVO:
--   Ampliar CHECK INDECI_ASIST_DET_ORIGEN_CK para admitir los valores de ORIGEN
--   que el código ya produce pero el CHECK original (V010_67: solo 'MANUAL' e
--   'IMPORT_MARCADOR') rechazaba con ORA-02290:
--     - 'PAPELETA'   : día justificado por papeleta APROBADA (TELETRABAJO/PERMISO
--                      desde V012_35; ASISTENCIA_JUSTIFICADA por papeleta 004, V012_39).
--     - 'CALENDARIO' : FALTA generada por el calendario (día laborable sin marca).
--   Ambos eran bugs latentes que se manifiestan al confirmar una importación con
--   faltas y/o días justificados por papeleta.
--
-- DECISIONES:
--   - Idempotente: drop/recreate CHECK (misma técnica de V010_67 / V012_35 / V012_39).
--
-- Ejecutar en esquema GESTIONRRHH / Oracle 19c+.
-- ============================================================================
SET SERVEROUTPUT ON;

DECLARE
    PROCEDURE drop_constraint_if_exists(
        p_table_name      VARCHAR2,
        p_constraint_name VARCHAR2
    ) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_exists FROM ALL_CONSTRAINTS
         WHERE OWNER = 'GESTIONRRHH' AND TABLE_NAME = p_table_name
           AND CONSTRAINT_NAME = p_constraint_name;
        IF v_exists > 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE GESTIONRRHH.' || p_table_name
                || ' DROP CONSTRAINT ' || p_constraint_name;
            DBMS_OUTPUT.PUT_LINE(p_constraint_name || ' -> eliminado.');
        ELSE
            DBMS_OUTPUT.PUT_LINE(p_constraint_name || ' no existe. Sin cambios.');
        END IF;
    END;

    PROCEDURE add_constraint_if_missing(
        p_table_name      VARCHAR2,
        p_constraint_name VARCHAR2,
        p_constraint_ddl  VARCHAR2
    ) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_exists FROM ALL_CONSTRAINTS
         WHERE OWNER = 'GESTIONRRHH' AND TABLE_NAME = p_table_name
           AND CONSTRAINT_NAME = p_constraint_name;
        IF v_exists = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE GESTIONRRHH.' || p_table_name
                || ' ADD CONSTRAINT ' || p_constraint_name || ' ' || p_constraint_ddl;
            DBMS_OUTPUT.PUT_LINE(p_constraint_name || ' -> agregado.');
        ELSE
            DBMS_OUTPUT.PUT_LINE(p_constraint_name || ' ya existe. Sin cambios.');
        END IF;
    END;
BEGIN
    drop_constraint_if_exists('INDECI_ASISTENCIA_DETALLE', 'INDECI_ASIST_DET_ORIGEN_CK');
    add_constraint_if_missing(
        'INDECI_ASISTENCIA_DETALLE',
        'INDECI_ASIST_DET_ORIGEN_CK',
        'CHECK (ORIGEN IS NULL OR ORIGEN IN (''MANUAL'', ''IMPORT_MARCADOR'', ''PAPELETA'', ''CALENDARIO''))');

    DBMS_OUTPUT.PUT_LINE('V012_40 finalizado.');
END;
/
