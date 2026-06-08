-- ============================================================================
-- V010_66 evento maternidad P0
--
-- Objetivo:
--   1. Extender INDECI_EMPLEADO_EVENTO con datos de maternidad.
--   2. Crear SEQ_EVENTO_DIST_MES si falta.
--   3. Crear INDECI_EVENTO_DISTRIBUCION_MES si falta.
--
-- Defensa:
--   - Idempotente: permite reejecutar si la migracion quedo aplicada a medias.
--   - TABLESPACE explicito heredado de INDECI_EMPLEADO para evitar ORA-00959
--     cuando el default del schema apunta a TBS_RRHH inexistente.
-- ============================================================================

SET SERVEROUTPUT ON;

DECLARE
    v_count NUMBER;
    v_ts    VARCHAR2(30);

    PROCEDURE add_column_if_missing(
        p_table_name VARCHAR2,
        p_col_name   VARCHAR2,
        p_col_ddl    VARCHAR2
    ) IS
    BEGIN
        SELECT COUNT(*)
          INTO v_count
          FROM USER_TAB_COLUMNS
         WHERE TABLE_NAME  = UPPER(p_table_name)
           AND COLUMN_NAME = UPPER(p_col_name);

        IF v_count = 0 THEN
            EXECUTE IMMEDIATE
                'ALTER TABLE ' || p_table_name || ' ADD (' || p_col_ddl || ')';
            DBMS_OUTPUT.PUT_LINE(p_table_name || '.' || p_col_name || ' -> agregada.');
        ELSE
            DBMS_OUTPUT.PUT_LINE(p_table_name || '.' || p_col_name || ' ya existe. Sin cambios.');
        END IF;
    END;

    PROCEDURE add_sequence_if_missing(
        p_sequence_name VARCHAR2,
        p_create_ddl    VARCHAR2
    ) IS
    BEGIN
        SELECT COUNT(*)
          INTO v_count
          FROM USER_SEQUENCES
         WHERE SEQUENCE_NAME = UPPER(p_sequence_name);

        IF v_count = 0 THEN
            EXECUTE IMMEDIATE p_create_ddl;
            DBMS_OUTPUT.PUT_LINE(p_sequence_name || ' -> creada.');
        ELSE
            DBMS_OUTPUT.PUT_LINE(p_sequence_name || ' ya existe. Sin cambios.');
        END IF;
    END;

    PROCEDURE add_table_if_missing(
        p_table_name VARCHAR2,
        p_create_ddl VARCHAR2
    ) IS
    BEGIN
        SELECT COUNT(*)
          INTO v_count
          FROM USER_TABLES
         WHERE TABLE_NAME = UPPER(p_table_name);

        IF v_count = 0 THEN
            EXECUTE IMMEDIATE p_create_ddl || ' TABLESPACE ' || v_ts;
            DBMS_OUTPUT.PUT_LINE(p_table_name || ' -> creada en tablespace ' || v_ts || '.');
        ELSE
            DBMS_OUTPUT.PUT_LINE(p_table_name || ' ya existe. Sin cambios.');
        END IF;
    END;

BEGIN
    SELECT TABLESPACE_NAME
      INTO v_ts
      FROM USER_TABLES
     WHERE TABLE_NAME = 'INDECI_EMPLEADO';

    add_column_if_missing('INDECI_EMPLEADO_EVENTO', 'DURACION_LEGAL',
        'DURACION_LEGAL NUMBER(3)');
    add_column_if_missing('INDECI_EMPLEADO_EVENTO', 'MOTIVO_EXTENSION',
        'MOTIVO_EXTENSION VARCHAR2(40)');
    add_column_if_missing('INDECI_EMPLEADO_EVENTO', 'FECHA_PROBABLE_PARTO',
        'FECHA_PROBABLE_PARTO DATE');
    add_column_if_missing('INDECI_EMPLEADO_EVENTO', 'DIFIERE_PRENATAL_POSTNATAL',
        'DIFIERE_PRENATAL_POSTNATAL VARCHAR2(20)');
    add_column_if_missing('INDECI_EMPLEADO_EVENTO', 'TIPO_DOCUMENTO',
        'TIPO_DOCUMENTO VARCHAR2(30)');
    add_column_if_missing('INDECI_EMPLEADO_EVENTO', 'NRO_CITT',
        'NRO_CITT VARCHAR2(50)');
    add_column_if_missing('INDECI_EMPLEADO_EVENTO', 'FECHA_EMISION_DOC',
        'FECHA_EMISION_DOC DATE');

    add_sequence_if_missing(
        'SEQ_EVENTO_DIST_MES',
        'CREATE SEQUENCE SEQ_EVENTO_DIST_MES START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE'
    );

    add_table_if_missing(
        'INDECI_EVENTO_DISTRIBUCION_MES',
        'CREATE TABLE INDECI_EVENTO_DISTRIBUCION_MES ('
        || 'ID NUMBER(19) NOT NULL,'
        || 'EMPLEADO_EVENTO_ID NUMBER(19) NOT NULL,'
        || 'PERIODO VARCHAR2(6) NOT NULL,'
        || 'FECHA_DESDE DATE NOT NULL,'
        || 'FECHA_HASTA DATE NOT NULL,'
        || 'DIAS_SUBSIDIO NUMBER(3) NOT NULL,'
        || 'AFECTA_DIAS_LABORADOS CHAR(1) DEFAULT ''S'' NOT NULL,'
        || 'ESTADO_TRAMO VARCHAR2(30) DEFAULT ''PENDIENTE_IMPUTACION'' NOT NULL,'
        || 'CREATED_AT TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,'
        || 'CONSTRAINT TBL_EVT_DIST_MES_PK PRIMARY KEY (ID)'
        || ')'
    );

    DBMS_OUTPUT.PUT_LINE('V010_66 finalizado.');
END;
/
