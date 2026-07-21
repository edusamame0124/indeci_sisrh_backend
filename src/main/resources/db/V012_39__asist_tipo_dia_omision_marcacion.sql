-- ============================================================================
-- Spec M04 / V012_39 — Nuevos tipos de dia OMISION_MARCACION y ASISTENCIA_JUSTIFICADA
--
-- OBJETIVO (Regla SERVIR/INDECI "Omision de Marcacion" vs "Falta"):
--   Ampliar CHECK INDECI_ASIST_DET_TIPO_CK para admitir:
--     - OMISION_MARCACION: el trabajador marco entrada pero no salida (o viceversa).
--       NO es falta: tiene periodo de gracia para presentar la papeleta 004
--       (Permiso de Justificacion de Omision de Registro de Asistencia).
--       NO descuenta hasta el cierre.
--     - ASISTENCIA_JUSTIFICADA: la omision quedo cubierta por una papeleta 004
--       APROBADA -> se calcula tiempo completo, cuenta como laborado, no descuenta.
--   Al cierre de planilla, toda OMISION_MARCACION sin papeleta 004 se penaliza
--   como FALTA (lo aplica el motor; aqui solo se habilita el tipo en el CHECK).
--
-- DECISIONES:
--   - Idempotente: drop/recreate CHECK (misma tecnica de V010_112 / V012_35).
--   - Sin nueva tabla catalogo: patron VARCHAR2 + CHECK ya usado por TIPO_DIA.
--
-- Ejecutar en esquema GESTIONRRHH / Oracle 19c+.
-- ============================================================================

SET SERVEROUTPUT ON;

DECLARE
    -- En PL/SQL las variables deben declararse ANTES que los subprogramas anidados.
    v_len NUMBER;

    PROCEDURE drop_constraint_if_exists(
        p_table_name      VARCHAR2,
        p_constraint_name VARCHAR2
    ) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*)
          INTO v_exists
          FROM ALL_CONSTRAINTS
         WHERE OWNER           = 'GESTIONRRHH'
           AND TABLE_NAME      = p_table_name
           AND CONSTRAINT_NAME = p_constraint_name;

        IF v_exists > 0 THEN
            EXECUTE IMMEDIATE
                'ALTER TABLE GESTIONRRHH.' || p_table_name
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
        SELECT COUNT(*)
          INTO v_exists
          FROM ALL_CONSTRAINTS
         WHERE OWNER           = 'GESTIONRRHH'
           AND TABLE_NAME      = p_table_name
           AND CONSTRAINT_NAME = p_constraint_name;

        IF v_exists = 0 THEN
            EXECUTE IMMEDIATE
                'ALTER TABLE GESTIONRRHH.' || p_table_name
                || ' ADD CONSTRAINT ' || p_constraint_name || ' ' || p_constraint_ddl;
            DBMS_OUTPUT.PUT_LINE(p_constraint_name || ' -> agregado.');
        ELSE
            DBMS_OUTPUT.PUT_LINE(p_constraint_name || ' ya existe. Sin cambios.');
        END IF;
    END;
BEGIN
    -- La columna era VARCHAR2(15): no caben OMISION_MARCACION (17) ni ASISTENCIA_JUSTIFICADA (22).
    -- Se amplía a 30 (idempotente: solo si el largo actual es menor).
    SELECT data_length INTO v_len
      FROM all_tab_columns
     WHERE owner = 'GESTIONRRHH'
       AND table_name = 'INDECI_ASISTENCIA_DETALLE'
       AND column_name = 'TIPO_DIA';
    IF v_len < 30 THEN
        EXECUTE IMMEDIATE
            'ALTER TABLE GESTIONRRHH.INDECI_ASISTENCIA_DETALLE MODIFY (TIPO_DIA VARCHAR2(30))';
        DBMS_OUTPUT.PUT_LINE('TIPO_DIA ampliada a VARCHAR2(30).');
    ELSE
        DBMS_OUTPUT.PUT_LINE('TIPO_DIA ya tiene largo suficiente (' || v_len || ').');
    END IF;

    drop_constraint_if_exists('INDECI_ASISTENCIA_DETALLE', 'INDECI_ASIST_DET_TIPO_CK');
    add_constraint_if_missing(
        'INDECI_ASISTENCIA_DETALLE',
        'INDECI_ASIST_DET_TIPO_CK',
        'CHECK (TIPO_DIA IN (''LABORAL'', ''FALTA'', ''TARDANZA'', ''LICENCIA'', ''VACACIONES'', ''DESCANSO'', ''FERIADO'', ''OBSERVADO'', ''SANCION_PAD'', ''TELETRABAJO'', ''PERMISO'', ''OMISION_MARCACION'', ''ASISTENCIA_JUSTIFICADA''))');

    EXECUTE IMMEDIATE 'COMMENT ON COLUMN GESTIONRRHH.INDECI_ASISTENCIA_DETALLE.TIPO_DIA IS ''LABORAL | FALTA | TARDANZA | LICENCIA | VACACIONES | DESCANSO | FERIADO | OBSERVADO | SANCION_PAD | TELETRABAJO | PERMISO | OMISION_MARCACION | ASISTENCIA_JUSTIFICADA.''';

    DBMS_OUTPUT.PUT_LINE('V012_39 finalizado.');
END;
/
