-- ============================================================================
-- SPEC_CONCEPTOS_PLANILLA §13 / V010_100 — "Tipo de Concepto" (SISPER) + código auto
--
-- OBJETIVO (aditivo, sin tocar el motor ni el CHECK de TIPO_CONCEPTO):
--   1. INDECI_TIPO_CONCEPTO_INTERNO — catálogo administrable de la taxonomía
--      funcional SISPER (8 valores). El mapeo al motor vive en la fila
--      (CLASIFICACION_MOTOR), NO se hardcodea.
--   2. INDECI_CONCEPTO_PLANILLA — ADD TIPO_CONCEPTO_INTERNO (FK al catálogo).
--   3. INDECI_SEQ_CONCEPTO_COD — secuencia para el código correlativo CONC-####.
--
-- DECISIONES DE DISEÑO (§13):
--   - Dos taxonomías COEXISTEN: TIPO_CONCEPTO (motor, 5 valores, con CHECK Oracle —
--     intacto) y "Tipo de Concepto" SISPER (8 valores, muchos-a-uno hacia el motor).
--   - TIPO_CONCEPTO se DERIVA en la app desde CLASIFICACION_MOTOR de la fila elegida.
--   - PK natural (CODIGO); naming INDECI_TIPO_CONC_INT_*. Sin USUARIO_CREO
--     (auditoría centralizada en AuditoriaAspect — D.L. 1451).
--
-- DEFENSA EN PROFUNDIDAD: idempotente + tablespace-safe (réplica del bloque de
-- resolución de TABLESPACE de V010_97). Ejecutar en GESTIONRRHH / Oracle 19c+.
-- ============================================================================

SET SERVEROUTPUT ON;

DECLARE
    v_count     NUMBER;
    v_ts        VARCHAR2(30);
    v_ts_exists NUMBER;
    v_ts_clause VARCHAR2(60);

    PROCEDURE add_table_if_missing(
        p_table_name VARCHAR2,
        p_create_ddl VARCHAR2
    ) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_exists
          FROM ALL_TABLES
         WHERE OWNER = 'GESTIONRRHH' AND TABLE_NAME = p_table_name;
        IF v_exists = 0 THEN
            EXECUTE IMMEDIATE p_create_ddl;
            DBMS_OUTPUT.PUT_LINE(p_table_name || ' -> creada.');
        ELSE
            DBMS_OUTPUT.PUT_LINE(p_table_name || ' ya existe. Sin cambios.');
        END IF;
    END;

    PROCEDURE add_column_if_missing(
        p_table_name  VARCHAR2,
        p_column_name VARCHAR2,
        p_alter_ddl   VARCHAR2
    ) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_exists
          FROM ALL_TAB_COLUMNS
         WHERE OWNER = 'GESTIONRRHH'
           AND TABLE_NAME = p_table_name
           AND COLUMN_NAME = p_column_name;
        IF v_exists = 0 THEN
            EXECUTE IMMEDIATE p_alter_ddl;
            DBMS_OUTPUT.PUT_LINE(p_table_name || '.' || p_column_name || ' -> añadida.');
        ELSE
            DBMS_OUTPUT.PUT_LINE(p_table_name || '.' || p_column_name
                || ' ya existe. Sin cambios.');
        END IF;
    END;

    PROCEDURE add_constraint_if_missing(
        p_constraint_name VARCHAR2,
        p_alter_ddl       VARCHAR2
    ) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_exists
          FROM ALL_CONSTRAINTS
         WHERE OWNER = 'GESTIONRRHH' AND CONSTRAINT_NAME = p_constraint_name;
        IF v_exists = 0 THEN
            EXECUTE IMMEDIATE p_alter_ddl;
            DBMS_OUTPUT.PUT_LINE(p_constraint_name || ' -> creada.');
        ELSE
            DBMS_OUTPUT.PUT_LINE(p_constraint_name || ' ya existe. Sin cambios.');
        END IF;
    END;

    PROCEDURE add_index_if_missing(
        p_index_name VARCHAR2,
        p_create_ddl VARCHAR2
    ) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_exists
          FROM ALL_INDEXES
         WHERE OWNER = 'GESTIONRRHH' AND INDEX_NAME = p_index_name;
        IF v_exists = 0 THEN
            EXECUTE IMMEDIATE p_create_ddl;
            DBMS_OUTPUT.PUT_LINE(p_index_name || ' -> creado.');
        ELSE
            DBMS_OUTPUT.PUT_LINE(p_index_name || ' ya existe. Sin cambios.');
        END IF;
    END;

    PROCEDURE add_sequence_if_missing(
        p_sequence_name VARCHAR2,
        p_create_ddl    VARCHAR2
    ) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_exists
          FROM ALL_SEQUENCES
         WHERE SEQUENCE_OWNER = 'GESTIONRRHH' AND SEQUENCE_NAME = p_sequence_name;
        IF v_exists = 0 THEN
            EXECUTE IMMEDIATE p_create_ddl;
            DBMS_OUTPUT.PUT_LINE(p_sequence_name || ' -> creada.');
        ELSE
            DBMS_OUTPUT.PUT_LINE(p_sequence_name || ' ya existe. Sin cambios.');
        END IF;
    END;
BEGIN
    -- ==================================================================
    -- 0) Resolver un TABLESPACE válido (evita ORA-00959 cuando el default
    --    del esquema, TBS_RRHH, no existe). Ancla: INDECI_EMPLEADO →
    --    default del usuario → sin cláusula. Mismo patrón que V010_97.
    -- ==================================================================
    BEGIN
        SELECT TABLESPACE_NAME INTO v_ts
          FROM USER_TABLES
         WHERE TABLE_NAME = 'INDECI_EMPLEADO';
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            v_ts := NULL;
    END;

    IF v_ts IS NOT NULL THEN
        SELECT COUNT(*) INTO v_ts_exists
          FROM USER_TABLESPACES
         WHERE TABLESPACE_NAME = v_ts;
        IF v_ts_exists = 0 THEN
            DBMS_OUTPUT.PUT_LINE('Tablespace ancla ' || v_ts || ' no existe; se intenta default del usuario.');
            v_ts := NULL;
        END IF;
    END IF;

    IF v_ts IS NULL THEN
        SELECT DEFAULT_TABLESPACE INTO v_ts
          FROM USER_USERS
         WHERE USERNAME = USER;
        SELECT COUNT(*) INTO v_ts_exists
          FROM USER_TABLESPACES
         WHERE TABLESPACE_NAME = v_ts;
        IF v_ts_exists = 0 THEN
            DBMS_OUTPUT.PUT_LINE('Default tablespace ' || v_ts || ' no disponible; CREATE sin TABLESPACE.');
            v_ts := NULL;
        END IF;
    END IF;

    IF v_ts IS NOT NULL THEN
        v_ts_clause := ' TABLESPACE ' || v_ts;
        DBMS_OUTPUT.PUT_LINE('Tablespace para INDECI_TIPO_CONCEPTO_INTERNO / índices: ' || v_ts);
    ELSE
        v_ts_clause := '';
    END IF;

    -- ==================================================================
    -- 1) INDECI_TIPO_CONCEPTO_INTERNO — catálogo SISPER (PK natural = CODIGO)
    -- ==================================================================
    add_table_if_missing(
        'INDECI_TIPO_CONCEPTO_INTERNO',
        'CREATE TABLE GESTIONRRHH.INDECI_TIPO_CONCEPTO_INTERNO ('
        ||   'CODIGO             VARCHAR2(20 CHAR) NOT NULL,'
        ||   'NOMBRE             VARCHAR2(60 CHAR) NOT NULL,'
        ||   'CLASIFICACION_MOTOR VARCHAR2(20 CHAR) NOT NULL,'
        ||   'ORDEN              NUMBER(3),'
        ||   'ACTIVO             NUMBER(1) DEFAULT 1 NOT NULL,'
        ||   'CONSTRAINT INDECI_TIPO_CONC_INT_PK PRIMARY KEY (CODIGO),'
        ||   'CONSTRAINT INDECI_TIPO_CONC_INT_CLAS_CK CHECK (CLASIFICACION_MOTOR IN '
        ||     '(''REMUNERATIVO'', ''NO_REMUNERATIVO'', ''DESCUENTO'', '
        ||      '''APORTE_TRABAJADOR'', ''APORTE_EMPLEADOR'')),'
        ||   'CONSTRAINT INDECI_TIPO_CONC_INT_ACT_CK CHECK (ACTIVO IN (0, 1))'
        || ')' || v_ts_clause
    );

    add_index_if_missing(
        'IX_INDECI_TIPO_CONC_INT_ORDEN',
        'CREATE INDEX GESTIONRRHH.IX_INDECI_TIPO_CONC_INT_ORDEN '
        || 'ON GESTIONRRHH.INDECI_TIPO_CONCEPTO_INTERNO (ACTIVO, ORDEN)' || v_ts_clause
    );

    -- ==================================================================
    -- 2) INDECI_CONCEPTO_PLANILLA — TIPO_CONCEPTO_INTERNO (FK al catálogo)
    -- ==================================================================
    add_column_if_missing(
        'INDECI_CONCEPTO_PLANILLA', 'TIPO_CONCEPTO_INTERNO',
        'ALTER TABLE GESTIONRRHH.INDECI_CONCEPTO_PLANILLA '
        || 'ADD (TIPO_CONCEPTO_INTERNO VARCHAR2(20 CHAR))'
    );
    add_constraint_if_missing(
        'INDECI_CONCEPTO_TIPO_INT_FK',
        'ALTER TABLE GESTIONRRHH.INDECI_CONCEPTO_PLANILLA '
        || 'ADD CONSTRAINT INDECI_CONCEPTO_TIPO_INT_FK '
        || 'FOREIGN KEY (TIPO_CONCEPTO_INTERNO) '
        || 'REFERENCES GESTIONRRHH.INDECI_TIPO_CONCEPTO_INTERNO (CODIGO)'
    );

    -- ==================================================================
    -- 3) INDECI_SEQ_CONCEPTO_COD — código correlativo CONC-####
    -- ==================================================================
    add_sequence_if_missing(
        'INDECI_SEQ_CONCEPTO_COD',
        'CREATE SEQUENCE GESTIONRRHH.INDECI_SEQ_CONCEPTO_COD '
        || 'START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE'
    );

    -- ==================================================================
    -- COMMENTS
    -- ==================================================================
    EXECUTE IMMEDIATE 'COMMENT ON TABLE GESTIONRRHH.INDECI_TIPO_CONCEPTO_INTERNO IS '
        || '''SPEC_CONCEPTOS_PLANILLA §13 — Catálogo Tipo de Concepto (SISPER, 8 valores). '
        || 'CLASIFICACION_MOTOR mapea data-driven al TIPO_CONCEPTO del motor (muchos-a-uno).''';
    EXECUTE IMMEDIATE 'COMMENT ON COLUMN GESTIONRRHH.INDECI_TIPO_CONCEPTO_INTERNO.CLASIFICACION_MOTOR IS '
        || '''Clasificacion del motor derivada: REMUNERATIVO|NO_REMUNERATIVO|DESCUENTO|'
        || 'APORTE_TRABAJADOR|APORTE_EMPLEADOR. El motor sigue leyendo TIPO_CONCEPTO.''';
    EXECUTE IMMEDIATE 'COMMENT ON COLUMN GESTIONRRHH.INDECI_CONCEPTO_PLANILLA.TIPO_CONCEPTO_INTERNO IS '
        || '''FK a INDECI_TIPO_CONCEPTO_INTERNO(CODIGO). El TIPO_CONCEPTO (motor) se deriva '
        || 'de CLASIFICACION_MOTOR de esta fila (§13).''';

    DBMS_OUTPUT.PUT_LINE('V010_100 DDL finalizado.');
END;
/

-- ============================================================================
-- 4) SEED del catálogo (MERGE idempotente) — los 8 valores EXACTOS del §13.
--    Mapeo autoritativo CODIGO/NOMBRE/CLASIFICACION_MOTOR/ORDEN.
-- ============================================================================
MERGE INTO GESTIONRRHH.INDECI_TIPO_CONCEPTO_INTERNO d
USING (
    SELECT 'REM_FIJA'    AS CODIGO, 'REMUNERACION FIJA' AS NOMBRE, 'REMUNERATIVO'      AS CLASIFICACION_MOTOR, 1 AS ORDEN FROM DUAL UNION ALL
    SELECT 'REINTEGRO',         'REINTEGRO',          'REMUNERATIVO',      2 FROM DUAL UNION ALL
    SELECT 'ENCARGATURA',       'ENCARGATURA',        'REMUNERATIVO',      3 FROM DUAL UNION ALL
    SELECT 'INCENTIVOS',        'INCENTIVOS',         'NO_REMUNERATIVO',   4 FROM DUAL UNION ALL
    SELECT 'OTRA_REM',          'OTRA REMUNERACION',  'REMUNERATIVO',      5 FROM DUAL UNION ALL
    SELECT 'DESC_VAR',          'DESCUENTO VARIABLE', 'DESCUENTO',         6 FROM DUAL UNION ALL
    SELECT 'DESC_FIJO',         'DESCUENTO FIJO',     'DESCUENTO',         7 FROM DUAL UNION ALL
    SELECT 'APORTE_TRAB',       'APORTE TRABAJADOR',  'APORTE_TRABAJADOR', 8 FROM DUAL
) s
ON (d.CODIGO = s.CODIGO)
WHEN MATCHED THEN UPDATE SET
    d.NOMBRE              = s.NOMBRE,
    d.CLASIFICACION_MOTOR = s.CLASIFICACION_MOTOR,
    d.ORDEN               = s.ORDEN,
    d.ACTIVO              = 1
WHEN NOT MATCHED THEN INSERT
    (CODIGO, NOMBRE, CLASIFICACION_MOTOR, ORDEN, ACTIVO)
VALUES
    (s.CODIGO, s.NOMBRE, s.CLASIFICACION_MOTOR, s.ORDEN, 1);

COMMIT;
