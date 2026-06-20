-- ============================================================================
-- M04 / V010_84 — Decisión de papeleta/permiso sobre el día de asistencia
--
-- OBJETIVO: cuando un día tiene una papeleta APROBADA (INDECI_SOLICITUD_RRHH,
--   ESTADO_SOLICITUD_ID = 9), RR. HH. decide en la consulta diaria si la
--   AUTORIZA o NO sobre la asistencia:
--     - Autorizar    -> condición pasa a Presente (LABORAL); PAPELETA_AUTORIZADA = 1.
--     - No autorizar -> condición pasa a Observado (OBSERVADO); PAPELETA_AUTORIZADA = 0
--                       y se exige PAPELETA_MOTIVO_RECHAZO. Este observado SÍ descuenta
--                       como falta (a diferencia del observado por marca incompleta).
--   La decisión es editable y queda trazada (usuario + fecha).
--
-- Idempotente; TABLESPACE ancla de INDECI_EMPLEADO.
-- ============================================================================

SET SERVEROUTPUT ON;

DECLARE
    v_count NUMBER;
    v_ts    VARCHAR2(30);

    PROCEDURE add_column_if_missing(p_table_name VARCHAR2, p_col_name VARCHAR2, p_col_ddl VARCHAR2) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_exists FROM ALL_TAB_COLUMNS
         WHERE OWNER = 'GESTIONRRHH' AND TABLE_NAME = p_table_name AND COLUMN_NAME = p_col_name;
        IF v_exists = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE GESTIONRRHH.' || p_table_name || ' ADD (' || p_col_ddl || ')';
            DBMS_OUTPUT.PUT_LINE(p_table_name || '.' || p_col_name || ' -> agregada.');
        ELSE
            DBMS_OUTPUT.PUT_LINE(p_table_name || '.' || p_col_name || ' ya existe. Sin cambios.');
        END IF;
    END;
BEGIN
    SELECT TABLESPACE_NAME INTO v_ts FROM USER_TABLES WHERE TABLE_NAME = 'INDECI_EMPLEADO';
    DBMS_OUTPUT.PUT_LINE('Tablespace ancla (INDECI_EMPLEADO): ' || v_ts);

    add_column_if_missing('INDECI_ASISTENCIA_DETALLE', 'PAPELETA_AUTORIZADA', 'PAPELETA_AUTORIZADA NUMBER(1)');
    add_column_if_missing('INDECI_ASISTENCIA_DETALLE', 'PAPELETA_MOTIVO_RECHAZO', 'PAPELETA_MOTIVO_RECHAZO VARCHAR2(500 CHAR)');
    add_column_if_missing('INDECI_ASISTENCIA_DETALLE', 'PAPELETA_DECISION_USUARIO', 'PAPELETA_DECISION_USUARIO VARCHAR2(100 CHAR)');
    add_column_if_missing('INDECI_ASISTENCIA_DETALLE', 'PAPELETA_DECISION_FECHA', 'PAPELETA_DECISION_FECHA TIMESTAMP');

    EXECUTE IMMEDIATE 'COMMENT ON COLUMN GESTIONRRHH.INDECI_ASISTENCIA_DETALLE.PAPELETA_AUTORIZADA IS '
        || '''Decisión RR. HH. sobre la papeleta del día: 1 = autorizada (Presente), 0 = no autorizada (Observado descontable), NULL = sin decisión.''';
    EXECUTE IMMEDIATE 'COMMENT ON COLUMN GESTIONRRHH.INDECI_ASISTENCIA_DETALLE.PAPELETA_MOTIVO_RECHAZO IS '
        || '''Motivo obligatorio cuando la papeleta NO es autorizada.''';
    EXECUTE IMMEDIATE 'COMMENT ON COLUMN GESTIONRRHH.INDECI_ASISTENCIA_DETALLE.PAPELETA_DECISION_USUARIO IS '
        || '''Usuario que registró la decisión de la papeleta.''';
    EXECUTE IMMEDIATE 'COMMENT ON COLUMN GESTIONRRHH.INDECI_ASISTENCIA_DETALLE.PAPELETA_DECISION_FECHA IS '
        || '''Fecha/hora de la decisión de la papeleta.''';

    DBMS_OUTPUT.PUT_LINE('V010_84 finalizado.');
    SELECT COUNT(*) INTO v_count FROM ALL_TAB_COLUMNS
     WHERE OWNER = 'GESTIONRRHH' AND TABLE_NAME = 'INDECI_ASISTENCIA_DETALLE'
       AND COLUMN_NAME IN ('PAPELETA_AUTORIZADA', 'PAPELETA_MOTIVO_RECHAZO',
                           'PAPELETA_DECISION_USUARIO', 'PAPELETA_DECISION_FECHA');
    DBMS_OUTPUT.PUT_LINE('Columnas decisión papeleta: ' || v_count || ' / 4.');
END;
/
