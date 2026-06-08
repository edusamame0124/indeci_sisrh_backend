-- ============================================================================
-- Spec M04 / V010_67 — Importacion masiva CSV marcador biometrico
--
-- OBJETIVO:
--   1. INDECI_ASISTENCIA_IMPORTACION        — cabecera de importacion
--   2. INDECI_ASISTENCIA_IMPORTACION_FILA   — trazabilidad fila a fila (P0)
--   3. Extender INDECI_ASISTENCIA_CABECERA  — importacionId, marcador, estados
--   4. Extender INDECI_ASISTENCIA_DETALLE   — marcas, horas extra, origen
--   5. CHECK TIPO_DIA: agregar FERIADO y OBSERVADO
--   6. CHECK ESTADO cabecera: BORRADOR, PREVALIDADA, LISTA_PARA_VALIDAR,
--      OBSERVADA, VALIDADA
--
-- DECISIONES:
--   - Naming INDECI_*; PKs IDENTITY (coherencia V010_17).
--   - TABLESPACE explicito heredado de INDECI_EMPLEADO (ancla estable). Evita
--     ORA-00959 cuando el default tablespace del schema (TBS_RRHH) no existe
--     en el entorno (misma tecnica de V010_34/V010_48/V010_51).
--   - Idempotente: add_table/column/index/fk + drop/recreate CHECK.
--   - Planilla M05 solo consume cabeceras en estado VALIDADA (sin cambio).
--   - Sin USUARIO_CREO: auditoria centralizada en AuditoriaAspect.
--
-- Ejecutar en esquema GESTIONRRHH / Oracle 19c+.
-- ============================================================================

SET SERVEROUTPUT ON;

DECLARE
    v_count NUMBER;
    v_ts    VARCHAR2(30);

    PROCEDURE add_table_if_missing(
        p_table_name VARCHAR2,
        p_create_ddl VARCHAR2
    ) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*)
          INTO v_exists
          FROM ALL_TABLES
         WHERE OWNER      = 'GESTIONRRHH'
           AND TABLE_NAME = p_table_name;

        IF v_exists = 0 THEN
            EXECUTE IMMEDIATE p_create_ddl || ' TABLESPACE ' || v_ts;
            DBMS_OUTPUT.PUT_LINE(p_table_name || ' -> creada en TBS ' || v_ts || '.');
        ELSE
            DBMS_OUTPUT.PUT_LINE(p_table_name || ' ya existe. Sin cambios.');
        END IF;
    END;

    PROCEDURE add_index_if_missing(
        p_index_name VARCHAR2,
        p_create_ddl VARCHAR2
    ) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*)
          INTO v_exists
          FROM ALL_INDEXES
         WHERE OWNER      = 'GESTIONRRHH'
           AND INDEX_NAME = p_index_name;

        IF v_exists = 0 THEN
            EXECUTE IMMEDIATE p_create_ddl || ' TABLESPACE ' || v_ts;
            DBMS_OUTPUT.PUT_LINE(p_index_name || ' -> creado en TBS ' || v_ts || '.');
        ELSE
            DBMS_OUTPUT.PUT_LINE(p_index_name || ' ya existe. Sin cambios.');
        END IF;
    END;

    PROCEDURE add_column_if_missing(
        p_table_name VARCHAR2,
        p_col_name   VARCHAR2,
        p_col_ddl    VARCHAR2
    ) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*)
          INTO v_exists
          FROM ALL_TAB_COLUMNS
         WHERE OWNER       = 'GESTIONRRHH'
           AND TABLE_NAME  = p_table_name
           AND COLUMN_NAME = p_col_name;

        IF v_exists = 0 THEN
            EXECUTE IMMEDIATE
                'ALTER TABLE GESTIONRRHH.' || p_table_name || ' ADD ('
                || p_col_ddl || ')';
            DBMS_OUTPUT.PUT_LINE(p_table_name || '.' || p_col_name || ' -> agregada.');
        ELSE
            DBMS_OUTPUT.PUT_LINE(p_table_name || '.' || p_col_name || ' ya existe. Sin cambios.');
        END IF;
    END;

    PROCEDURE modify_column_if_needed(
        p_table_name  VARCHAR2,
        p_col_name    VARCHAR2,
        p_new_def     VARCHAR2
    ) IS
        v_data_type   VARCHAR2(128);
        v_char_length NUMBER;
        v_target_len  NUMBER;
    BEGIN
        SELECT DATA_TYPE, CHAR_LENGTH
          INTO v_data_type, v_char_length
          FROM ALL_TAB_COLUMNS
         WHERE OWNER       = 'GESTIONRRHH'
           AND TABLE_NAME  = p_table_name
           AND COLUMN_NAME = p_col_name;

        IF v_data_type LIKE 'VARCHAR%' THEN
            v_target_len := TO_NUMBER(REGEXP_SUBSTR(p_new_def, '[0-9]+'));
            IF v_char_length < v_target_len THEN
                EXECUTE IMMEDIATE
                    'ALTER TABLE GESTIONRRHH.' || p_table_name
                    || ' MODIFY (' || p_col_name || ' ' || p_new_def || ')';
                DBMS_OUTPUT.PUT_LINE(
                    p_table_name || '.' || p_col_name
                    || ' -> ampliada a ' || p_new_def || '.');
            ELSE
                DBMS_OUTPUT.PUT_LINE(
                    p_table_name || '.' || p_col_name
                    || ' ya cumple tamano. Sin cambios.');
            END IF;
        ELSE
            DBMS_OUTPUT.PUT_LINE(
                p_table_name || '.' || p_col_name
                || ' no es VARCHAR2. Sin cambios.');
        END IF;
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            DBMS_OUTPUT.PUT_LINE(
                p_table_name || '.' || p_col_name || ' no existe. Sin cambios.');
    END;

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
    -- Tablespace ancla: el de INDECI_EMPLEADO (tabla estable en todos los entornos).
    SELECT TABLESPACE_NAME
      INTO v_ts
      FROM USER_TABLES
     WHERE TABLE_NAME = 'INDECI_EMPLEADO';

    DBMS_OUTPUT.PUT_LINE('Tablespace ancla (INDECI_EMPLEADO): ' || v_ts);
    add_table_if_missing(
        'INDECI_ASISTENCIA_IMPORTACION',
        'CREATE TABLE GESTIONRRHH.INDECI_ASISTENCIA_IMPORTACION ('
        ||   'ID                    NUMBER GENERATED BY DEFAULT ON NULL AS IDENTITY,'
        ||   'PERIODO               VARCHAR2(7 CHAR)   NOT NULL,'
        ||   'NOMBRE_ARCHIVO        VARCHAR2(255 CHAR) NOT NULL,'
        ||   'HASH_SHA256           VARCHAR2(64 CHAR)  NOT NULL,'
        ||   'RUTA_ARCHIVO          VARCHAR2(500 CHAR),'
        ||   'ENCODING              VARCHAR2(32 CHAR),'
        ||   'USUARIO               VARCHAR2(100 CHAR) NOT NULL,'
        ||   'FECHA_IMPORTACION     TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,'
        ||   'ESTADO                VARCHAR2(20 CHAR)  NOT NULL,'
        ||   'ESTRATEGIA_CONFLICTO  VARCHAR2(40 CHAR),'
        ||   'FILAS_TOTAL           NUMBER(8)  DEFAULT 0 NOT NULL,'
        ||   'FILAS_VALIDAS         NUMBER(8)  DEFAULT 0 NOT NULL,'
        ||   'FILAS_ERROR           NUMBER(8)  DEFAULT 0 NOT NULL,'
        ||   'FILAS_OBSERVADAS      NUMBER(8)  DEFAULT 0 NOT NULL,'
        ||   'EMPLEADOS_PROCESADOS  NUMBER(8)  DEFAULT 0 NOT NULL,'
        ||   'RESULTADO_JSON        VARCHAR2(4000 CHAR),'
        ||   'CONSTRAINT INDECI_ASIST_IMPORT_PK PRIMARY KEY (ID),'
        ||   'CONSTRAINT INDECI_ASIST_IMPORT_ESTADO_CK '
        ||     'CHECK (ESTADO IN ('
        ||       '''BORRADOR_PREVIEW'', ''CONFIRMADA'', ''PARCIAL'', ''FALLIDA'')),'
        ||   'CONSTRAINT INDECI_ASIST_IMPORT_FILAS_CK '
        ||     'CHECK (FILAS_TOTAL >= 0 AND FILAS_VALIDAS >= 0 '
        ||       'AND FILAS_ERROR >= 0 AND FILAS_OBSERVADAS >= 0 '
        ||       'AND EMPLEADOS_PROCESADOS >= 0)'
        || ')'
    );

    add_index_if_missing(
        'IX_INDECI_ASIST_IMPORT_PERIODO',
        'CREATE INDEX GESTIONRRHH.IX_INDECI_ASIST_IMPORT_PERIODO '
        || 'ON GESTIONRRHH.INDECI_ASISTENCIA_IMPORTACION (PERIODO, FECHA_IMPORTACION)'
    );

    add_index_if_missing(
        'IX_INDECI_ASIST_IMPORT_HASH',
        'CREATE INDEX GESTIONRRHH.IX_INDECI_ASIST_IMPORT_HASH '
        || 'ON GESTIONRRHH.INDECI_ASISTENCIA_IMPORTACION (HASH_SHA256)'
    );

    add_table_if_missing(
        'INDECI_ASISTENCIA_IMPORTACION_FILA',
        'CREATE TABLE GESTIONRRHH.INDECI_ASISTENCIA_IMPORTACION_FILA ('
        ||   'ID                    NUMBER GENERATED BY DEFAULT ON NULL AS IDENTITY,'
        ||   'IMPORTACION_ID        NUMBER NOT NULL,'
        ||   'NUMERO_FILA           NUMBER(8) NOT NULL,'
        ||   'LINEA_ORIGINAL        VARCHAR2(4000 CHAR),'
        ||   'DNI                   VARCHAR2(8 CHAR),'
        ||   'FECHA                 DATE,'
        ||   'MARCA1                VARCHAR2(16 CHAR),'
        ||   'MARCA2                VARCHAR2(16 CHAR),'
        ||   'TARDANZA_RAW          VARCHAR2(16 CHAR),'
        ||   'OBSERVACION_MARCADOR  VARCHAR2(500 CHAR),'
        ||   'ESTADO_FILA           VARCHAR2(15 CHAR) NOT NULL,'
        ||   'ERRORES_JSON          VARCHAR2(4000 CHAR),'
        ||   'EMPLEADO_ID           NUMBER,'
        ||   'HASH_ARCHIVO          VARCHAR2(64 CHAR),'
        ||   'USUARIO_IMPORTACION   VARCHAR2(100 CHAR),'
        ||   'FECHA_IMPORTACION     TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,'
        ||   'CONSTRAINT INDECI_ASIST_IMP_FILA_PK PRIMARY KEY (ID),'
        ||   'CONSTRAINT INDECI_ASIST_IMP_FILA_IMP_FK FOREIGN KEY (IMPORTACION_ID) '
        ||     'REFERENCES GESTIONRRHH.INDECI_ASISTENCIA_IMPORTACION (ID),'
        ||   'CONSTRAINT INDECI_ASIST_IMP_FILA_EMP_FK FOREIGN KEY (EMPLEADO_ID) '
        ||     'REFERENCES GESTIONRRHH.INDECI_EMPLEADO (ID),'
        ||   'CONSTRAINT INDECI_ASIST_IMP_FILA_ESTADO_CK '
        ||     'CHECK (ESTADO_FILA IN (''VALIDA'', ''WARN'', ''ERROR'', ''OBSERVADA'')),'
        ||   'CONSTRAINT INDECI_ASIST_IMP_FILA_UK UNIQUE (IMPORTACION_ID, NUMERO_FILA)'
        || ')'
    );

    add_index_if_missing(
        'IX_INDECI_ASIST_IMP_FILA_IMP',
        'CREATE INDEX GESTIONRRHH.IX_INDECI_ASIST_IMP_FILA_IMP '
        || 'ON GESTIONRRHH.INDECI_ASISTENCIA_IMPORTACION_FILA (IMPORTACION_ID)'
    );

    add_index_if_missing(
        'IX_INDECI_ASIST_IMP_FILA_DNI',
        'CREATE INDEX GESTIONRRHH.IX_INDECI_ASIST_IMP_FILA_DNI '
        || 'ON GESTIONRRHH.INDECI_ASISTENCIA_IMPORTACION_FILA (DNI)'
    );

    add_index_if_missing(
        'IX_INDECI_ASIST_IMP_FILA_EMP',
        'CREATE INDEX GESTIONRRHH.IX_INDECI_ASIST_IMP_FILA_EMP '
        || 'ON GESTIONRRHH.INDECI_ASISTENCIA_IMPORTACION_FILA (EMPLEADO_ID)'
    );

    add_column_if_missing('INDECI_ASISTENCIA_CABECERA', 'IMPORTACION_ID', 'IMPORTACION_ID NUMBER');
    add_column_if_missing('INDECI_ASISTENCIA_CABECERA', 'MINUTOS_SALIDA_ANTICIPADA', 'MINUTOS_SALIDA_ANTICIPADA NUMBER(6) DEFAULT 0 NOT NULL');
    add_column_if_missing('INDECI_ASISTENCIA_CABECERA', 'MARCAS_INCOMPLETAS', 'MARCAS_INCOMPLETAS NUMBER(3) DEFAULT 0 NOT NULL');
    add_column_if_missing('INDECI_ASISTENCIA_CABECERA', 'BASE_ASISTENCIA_ORIGEN', 'BASE_ASISTENCIA_ORIGEN VARCHAR2(64 CHAR)');

    modify_column_if_needed('INDECI_ASISTENCIA_CABECERA', 'ESTADO', 'VARCHAR2(20 CHAR)');

    drop_constraint_if_exists('INDECI_ASISTENCIA_CABECERA', 'INDECI_ASIST_CAB_ESTADO_CK');
    add_constraint_if_missing(
        'INDECI_ASISTENCIA_CABECERA',
        'INDECI_ASIST_CAB_ESTADO_CK',
        'CHECK (ESTADO IN (''BORRADOR'', ''PREVALIDADA'', ''LISTA_PARA_VALIDAR'', ''OBSERVADA'', ''VALIDADA''))');

    add_constraint_if_missing(
        'INDECI_ASISTENCIA_CABECERA',
        'INDECI_ASIST_CAB_IMPORT_FK',
        'FOREIGN KEY (IMPORTACION_ID) REFERENCES GESTIONRRHH.INDECI_ASISTENCIA_IMPORTACION (ID)');

    add_index_if_missing(
        'IX_INDECI_ASIST_CAB_IMPORT',
        'CREATE INDEX GESTIONRRHH.IX_INDECI_ASIST_CAB_IMPORT '
        || 'ON GESTIONRRHH.INDECI_ASISTENCIA_CABECERA (IMPORTACION_ID)'
    );

    add_column_if_missing('INDECI_ASISTENCIA_DETALLE', 'MARCA_ENTRADA', 'MARCA_ENTRADA VARCHAR2(16 CHAR)');
    add_column_if_missing('INDECI_ASISTENCIA_DETALLE', 'MARCA_SALIDA', 'MARCA_SALIDA VARCHAR2(16 CHAR)');
    add_column_if_missing('INDECI_ASISTENCIA_DETALLE', 'HORA_ENTRADA_ESPERADA', 'HORA_ENTRADA_ESPERADA VARCHAR2(16 CHAR)');
    add_column_if_missing('INDECI_ASISTENCIA_DETALLE', 'MINUTOS_SALIDA_ANTICIPADA', 'MINUTOS_SALIDA_ANTICIPADA NUMBER(4) DEFAULT 0 NOT NULL');
    add_column_if_missing('INDECI_ASISTENCIA_DETALLE', 'HORAS_TRABAJADAS_MIN', 'HORAS_TRABAJADAS_MIN NUMBER(5) DEFAULT 0 NOT NULL');
    add_column_if_missing('INDECI_ASISTENCIA_DETALLE', 'HORAS_EXTRA_25_MIN', 'HORAS_EXTRA_25_MIN NUMBER(5) DEFAULT 0 NOT NULL');
    add_column_if_missing('INDECI_ASISTENCIA_DETALLE', 'HORAS_EXTRA_35_MIN', 'HORAS_EXTRA_35_MIN NUMBER(5) DEFAULT 0 NOT NULL');
    add_column_if_missing('INDECI_ASISTENCIA_DETALLE', 'HORAS_EXTRA_100_MIN', 'HORAS_EXTRA_100_MIN NUMBER(5) DEFAULT 0 NOT NULL');
    add_column_if_missing('INDECI_ASISTENCIA_DETALLE', 'HORAS_EXTRA_TOTAL_MIN', 'HORAS_EXTRA_TOTAL_MIN NUMBER(5) DEFAULT 0 NOT NULL');
    add_column_if_missing('INDECI_ASISTENCIA_DETALLE', 'DIA_SEMANA', 'DIA_SEMANA VARCHAR2(8 CHAR)');
    add_column_if_missing('INDECI_ASISTENCIA_DETALLE', 'ORIGEN', 'ORIGEN VARCHAR2(32 CHAR)');

    drop_constraint_if_exists('INDECI_ASISTENCIA_DETALLE', 'INDECI_ASIST_DET_TIPO_CK');
    add_constraint_if_missing(
        'INDECI_ASISTENCIA_DETALLE',
        'INDECI_ASIST_DET_TIPO_CK',
        'CHECK (TIPO_DIA IN (''LABORAL'', ''FALTA'', ''TARDANZA'', ''LICENCIA'', ''VACACIONES'', ''DESCANSO'', ''FERIADO'', ''OBSERVADO''))');

    add_constraint_if_missing(
        'INDECI_ASISTENCIA_DETALLE',
        'INDECI_ASIST_DET_ORIGEN_CK',
        'CHECK (ORIGEN IS NULL OR ORIGEN IN (''MANUAL'', ''IMPORT_MARCADOR''))');

    EXECUTE IMMEDIATE 'COMMENT ON TABLE GESTIONRRHH.INDECI_ASISTENCIA_IMPORTACION IS ''M04 P0 - Cabecera importacion masiva CSV marcador biometrico.''';
    EXECUTE IMMEDIATE 'COMMENT ON TABLE GESTIONRRHH.INDECI_ASISTENCIA_IMPORTACION_FILA IS ''M04 P0 - Trazabilidad fila a fila del CSV importado.''';
    EXECUTE IMMEDIATE 'COMMENT ON COLUMN GESTIONRRHH.INDECI_ASISTENCIA_IMPORTACION.ESTADO IS ''BORRADOR_PREVIEW | CONFIRMADA | PARCIAL | FALLIDA.''';
    EXECUTE IMMEDIATE 'COMMENT ON COLUMN GESTIONRRHH.INDECI_ASISTENCIA_IMPORTACION_FILA.ESTADO_FILA IS ''VALIDA | WARN | ERROR | OBSERVADA.''';
    EXECUTE IMMEDIATE 'COMMENT ON COLUMN GESTIONRRHH.INDECI_ASISTENCIA_CABECERA.IMPORTACION_ID IS ''FK nullable a importacion masiva origen.''';
    EXECUTE IMMEDIATE 'COMMENT ON COLUMN GESTIONRRHH.INDECI_ASISTENCIA_CABECERA.ESTADO IS ''BORRADOR | PREVALIDADA | LISTA_PARA_VALIDAR | OBSERVADA | VALIDADA. Solo VALIDADA habilita M05.''';
    EXECUTE IMMEDIATE 'COMMENT ON COLUMN GESTIONRRHH.INDECI_ASISTENCIA_CABECERA.BASE_ASISTENCIA_ORIGEN IS ''Origen remuneracion base: RESOLVER_CAS, FALLBACK_SUELDO_BASICO, etc.''';
    EXECUTE IMMEDIATE 'COMMENT ON COLUMN GESTIONRRHH.INDECI_ASISTENCIA_DETALLE.TIPO_DIA IS ''LABORAL | FALTA | TARDANZA | LICENCIA | VACACIONES | DESCANSO | FERIADO | OBSERVADO.''';
    EXECUTE IMMEDIATE 'COMMENT ON COLUMN GESTIONRRHH.INDECI_ASISTENCIA_DETALLE.ORIGEN IS ''MANUAL | IMPORT_MARCADOR.''';

    DBMS_OUTPUT.PUT_LINE('V010_67 finalizado.');

    SELECT COUNT(*) INTO v_count FROM ALL_TABLES
     WHERE OWNER = 'GESTIONRRHH' AND TABLE_NAME IN ('INDECI_ASISTENCIA_IMPORTACION','INDECI_ASISTENCIA_IMPORTACION_FILA');
    DBMS_OUTPUT.PUT_LINE('Tablas importacion: ' || v_count || ' / 2.');

    SELECT COUNT(*) INTO v_count FROM ALL_TAB_COLUMNS
     WHERE OWNER = 'GESTIONRRHH' AND TABLE_NAME = 'INDECI_ASISTENCIA_CABECERA'
       AND COLUMN_NAME IN ('IMPORTACION_ID','MINUTOS_SALIDA_ANTICIPADA','MARCAS_INCOMPLETAS','BASE_ASISTENCIA_ORIGEN');
    DBMS_OUTPUT.PUT_LINE('Columnas cabecera nuevas: ' || v_count || ' / 4.');

    SELECT COUNT(*) INTO v_count FROM ALL_TAB_COLUMNS
     WHERE OWNER = 'GESTIONRRHH' AND TABLE_NAME = 'INDECI_ASISTENCIA_DETALLE'
       AND COLUMN_NAME IN ('MARCA_ENTRADA','MARCA_SALIDA','HORA_ENTRADA_ESPERADA','MINUTOS_SALIDA_ANTICIPADA','HORAS_TRABAJADAS_MIN','HORAS_EXTRA_25_MIN','HORAS_EXTRA_35_MIN','HORAS_EXTRA_100_MIN','HORAS_EXTRA_TOTAL_MIN','DIA_SEMANA','ORIGEN');
    DBMS_OUTPUT.PUT_LINE('Columnas detalle nuevas: ' || v_count || ' / 11.');
END;
/
