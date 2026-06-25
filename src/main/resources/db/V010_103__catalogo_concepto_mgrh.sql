-- ============================================================================
-- SPEC_HOMOLOGACION_MGRH §D / V010_103 — Catálogo Único de Conceptos MGRH/MEF
--
-- OBJETIVO: tabla maestra de referencia INDECI_CATALOGO_CONCEPTO_MGRH (versiones
-- anuales) + homologación = FK NULLABLE única en INDECI_CONCEPTO_PLANILLA. SOLO
-- catálogo + homologación; NADA de motor / generación / MCPP / PLAME / cálculos.
--
-- DECISIONES (SPEC C/D6):
--   - Clave de negocio: TIPO + CODIGO_CONCEPTO_MGRH + ANIO_CATALOGO (versión anual).
--   - CODIGO es TEXTO (preserva ceros 0001). PK = ID IDENTITY.
--   - SELECCIONABLE='N' para GASTOS POR ENCARGO (lo carga el seed, no esta DDL).
--   - VIGENTE='S' = versión anual más reciente (consulta por defecto).
--   - FECHA_VIGENCIA_TEXTO = texto crudo (siempre); FECHA_VIGENCIA_DATE = derivado
--     nullable (solo dd/mm/yyyy parseable).
--
-- Las columnas deben CALZAR EXACTO con el INSERT de V010_103 → V010_104 (seed):
--   TIPO, CODIGO_CONCEPTO_MGRH, DESCRIPCION_NORMA, DETALLE_NORMA, FECHA_VIGENCIA_TEXTO,
--   FECHA_VIGENCIA_DATE, IMPONIBLE, DESCRIPCION_TIPO_CONCEPTO, TIPO_NORMA, ESTADO,
--   SELECCIONABLE, ANIO_CATALOGO, VIGENTE, FUENTE_CATALOGO, ACTIVO, FECHA_REGISTRO,
--   FECHA_MODIFICACION (+ ID IDENTITY PK).
--
-- DEFENSA EN PROFUNDIDAD: idempotente + tablespace-safe (mismo patrón que V010_97).
-- Ejecutar en GESTIONRRHH / Oracle 19c+ ANTES de V010_104 (el seed la puebla).
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
BEGIN
    -- ==================================================================
    -- 0) Resolver un TABLESPACE válido (evita ORA-00959). Ancla:
    --    INDECI_EMPLEADO → default del usuario → sin cláusula. Igual que V010_97.
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
        DBMS_OUTPUT.PUT_LINE('Tablespace para INDECI_CATALOGO_CONCEPTO_MGRH / índices: ' || v_ts);
    ELSE
        v_ts_clause := '';
    END IF;

    -- ==================================================================
    -- 1) INDECI_CATALOGO_CONCEPTO_MGRH — catálogo maestro (versiones anuales)
    --    Columnas EXACTAS que inserta V010_104 + ID IDENTITY PK.
    -- ==================================================================
    add_table_if_missing(
        'INDECI_CATALOGO_CONCEPTO_MGRH',
        'CREATE TABLE GESTIONRRHH.INDECI_CATALOGO_CONCEPTO_MGRH ('
        ||   'ID                        NUMBER GENERATED BY DEFAULT AS IDENTITY,'
        ||   'TIPO                      VARCHAR2(20 CHAR)  NOT NULL,'
        ||   'CODIGO_CONCEPTO_MGRH      VARCHAR2(10 CHAR)  NOT NULL,'   -- TEXTO, preserva ceros (0001)
        ||   'DESCRIPCION_NORMA         VARCHAR2(120 CHAR),'
        ||   'DETALLE_NORMA             VARCHAR2(500 CHAR),'
        ||   'FECHA_VIGENCIA_TEXTO      VARCHAR2(20 CHAR),'             -- D2: texto crudo (siempre)
        ||   'FECHA_VIGENCIA_DATE       DATE,'                         -- D2: derivado nullable
        ||   'IMPONIBLE                 VARCHAR2(2 CHAR),'             -- SI|NO
        ||   'DESCRIPCION_TIPO_CONCEPTO VARCHAR2(80 CHAR),'
        ||   'TIPO_NORMA                VARCHAR2(20 CHAR),'
        ||   'ESTADO                    VARCHAR2(20 CHAR),'            -- valor oficial (Activo)
        ||   'SELECCIONABLE             VARCHAR2(1 CHAR) DEFAULT ''S'' NOT NULL,'
        ||   'ANIO_CATALOGO             NUMBER(4) NOT NULL,'           -- D6: versión anual (2026)
        ||   'VIGENTE                   VARCHAR2(1 CHAR) DEFAULT ''S'' NOT NULL,'
        ||   'FUENTE_CATALOGO           VARCHAR2(120 CHAR),'           -- D6: corte (Conceptos2026.xls)
        ||   'ACTIVO                    NUMBER(1) DEFAULT 1 NOT NULL,'
        ||   'FECHA_REGISTRO            DATE DEFAULT SYSDATE NOT NULL,'
        ||   'FECHA_MODIFICACION        DATE,'
        ||   'CONSTRAINT INDECI_CAT_CONC_MGRH_PK PRIMARY KEY (ID),'
        ||   'CONSTRAINT INDECI_CAT_CONC_MGRH_UK UNIQUE (TIPO, CODIGO_CONCEPTO_MGRH, ANIO_CATALOGO),'
        ||   'CONSTRAINT INDECI_CAT_CONC_MGRH_ACT_CK CHECK (ACTIVO IN (0, 1)),'
        ||   'CONSTRAINT INDECI_CAT_CONC_MGRH_SEL_CK CHECK (SELECCIONABLE IN (''S'', ''N'')),'
        ||   'CONSTRAINT INDECI_CAT_CONC_MGRH_VIG_CK CHECK (VIGENTE IN (''S'', ''N''))'
        || ')' || v_ts_clause
    );

    -- Índice de consulta por defecto (VIGENTE + SELECCIONABLE — buscador ordinario).
    add_index_if_missing(
        'IX_INDECI_CAT_CONC_MGRH_VIGSEL',
        'CREATE INDEX GESTIONRRHH.IX_INDECI_CAT_CONC_MGRH_VIGSEL '
        || 'ON GESTIONRRHH.INDECI_CATALOGO_CONCEPTO_MGRH (VIGENTE, SELECCIONABLE)' || v_ts_clause
    );

    -- Índice por código (búsqueda LIKE / homologación).
    add_index_if_missing(
        'IX_INDECI_CAT_CONC_MGRH_COD',
        'CREATE INDEX GESTIONRRHH.IX_INDECI_CAT_CONC_MGRH_COD '
        || 'ON GESTIONRRHH.INDECI_CATALOGO_CONCEPTO_MGRH (CODIGO_CONCEPTO_MGRH)' || v_ts_clause
    );

    -- ==================================================================
    -- 2) INDECI_CONCEPTO_PLANILLA — homologación = FK NULLABLE única
    -- ==================================================================
    add_column_if_missing(
        'INDECI_CONCEPTO_PLANILLA', 'CATALOGO_CONCEPTO_MGRH_ID',
        'ALTER TABLE GESTIONRRHH.INDECI_CONCEPTO_PLANILLA '
        || 'ADD (CATALOGO_CONCEPTO_MGRH_ID NUMBER)'
    );
    add_constraint_if_missing(
        'INDECI_CONCEPTO_MGRH_FK',
        'ALTER TABLE GESTIONRRHH.INDECI_CONCEPTO_PLANILLA '
        || 'ADD CONSTRAINT INDECI_CONCEPTO_MGRH_FK '
        || 'FOREIGN KEY (CATALOGO_CONCEPTO_MGRH_ID) '
        || 'REFERENCES GESTIONRRHH.INDECI_CATALOGO_CONCEPTO_MGRH (ID)'
    );

    -- ==================================================================
    -- COMMENTS
    -- ==================================================================
    EXECUTE IMMEDIATE 'COMMENT ON TABLE GESTIONRRHH.INDECI_CATALOGO_CONCEPTO_MGRH IS '
        || '''SPEC_HOMOLOGACION_MGRH — Catálogo Único de Conceptos MGRH/MEF (DGGRP). '
        || 'Clave negocio TIPO+CODIGO_CONCEPTO_MGRH+ANIO_CATALOGO. CODIGO es TEXTO (preserva ceros). '
        || 'VIGENTE=S es la versión anual más reciente; SELECCIONABLE=N excluye GASTOS POR ENCARGO.''';
    EXECUTE IMMEDIATE 'COMMENT ON COLUMN GESTIONRRHH.INDECI_CATALOGO_CONCEPTO_MGRH.FECHA_VIGENCIA_TEXTO IS '
        || '''Texto crudo del Excel (siempre conservado). FECHA_VIGENCIA_DATE es el derivado nullable.''';
    EXECUTE IMMEDIATE 'COMMENT ON COLUMN GESTIONRRHH.INDECI_CONCEPTO_PLANILLA.CATALOGO_CONCEPTO_MGRH_ID IS '
        || '''Homologación: FK nullable a INDECI_CATALOGO_CONCEPTO_MGRH(ID). NULL=Pendiente; con valor=Homologado.''';

    DBMS_OUTPUT.PUT_LINE('V010_103 finalizado.');

    SELECT COUNT(*) INTO v_count
      FROM ALL_TAB_COLUMNS
     WHERE OWNER = 'GESTIONRRHH'
       AND TABLE_NAME = 'INDECI_CONCEPTO_PLANILLA'
       AND COLUMN_NAME = 'CATALOGO_CONCEPTO_MGRH_ID';
    DBMS_OUTPUT.PUT_LINE('FK homologación en INDECI_CONCEPTO_PLANILLA: ' || v_count || ' / 1.');
END;
/
