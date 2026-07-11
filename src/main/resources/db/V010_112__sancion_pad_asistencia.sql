-- ============================================================================
-- Spec M04 / V010_112 — Nuevo tipo de dia SANCION_PAD (Sancion por PAD)
--
-- OBJETIVO:
--   Ampliar CHECK INDECI_ASIST_DET_TIPO_CK para permitir 'SANCION_PAD' como
--   tipo de dia adicional. Se comporta igual que FALTA para efectos de
--   descuento de planilla (AsistenciaResumenCalculator) y computo de record
--   vacacional (AsistenciaDetalleRepository.contarFaltas), pero exige
--   observacion obligatoria (motivo/expediente del proceso administrativo
--   disciplinario) validada en AsistenciaService.
--
-- DECISIONES:
--   - Idempotente: drop/recreate CHECK (misma tecnica de V010_67).
--   - Sin nueva tabla catalogo: se mantiene el patron VARCHAR2 + CHECK ya
--     usado por TIPO_DIA (evita desviacion grande del patron actual).
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
    drop_constraint_if_exists('INDECI_ASISTENCIA_DETALLE', 'INDECI_ASIST_DET_TIPO_CK');
    add_constraint_if_missing(
        'INDECI_ASISTENCIA_DETALLE',
        'INDECI_ASIST_DET_TIPO_CK',
        'CHECK (TIPO_DIA IN (''LABORAL'', ''FALTA'', ''TARDANZA'', ''LICENCIA'', ''VACACIONES'', ''DESCANSO'', ''FERIADO'', ''OBSERVADO'', ''SANCION_PAD''))');

    EXECUTE IMMEDIATE 'COMMENT ON COLUMN GESTIONRRHH.INDECI_ASISTENCIA_DETALLE.TIPO_DIA IS ''LABORAL | FALTA | TARDANZA | LICENCIA | VACACIONES | DESCANSO | FERIADO | OBSERVADO | SANCION_PAD.''';

    DBMS_OUTPUT.PUT_LINE('V010_112 finalizado.');
END;
/
