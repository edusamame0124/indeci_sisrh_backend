-- ============================================================================
-- SPEC_CONCEPTOS_PLANILLA §10.1 / P1 / V010_97 — Catálogo RTPS (PDT 601) + estado
--
-- OBJETIVO: soporte de datos del ciclo de vida del concepto y de la clasificación
-- externa RTPS, SIN tocar el motor ni el cálculo. El motor en P1 sigue leyendo
-- ACTIVO (no ESTADO).
--
-- TABLAS / CAMBIOS:
--   1. INDECI_CONCEPTO_RTPS — catálogo PDT 601 (PK natural = CODIGO, preserva ceros).
--      Modelo espejo de INDECI_CAT_SUSPENSION_SUNAT (catálogo con PK natural).
--   2. INDECI_CONCEPTO_PLANILLA — ADD ESTADO (ciclo de vida) + RTPS_CODIGO (FK).
--      Backfill ESTADO desde ACTIVO en la MISMA migración (crítico: evita apagar
--      conceptos ya sembrados al introducir el DEFAULT 'BORRADOR').
--
-- DECISIONES DE DISEÑO:
--   - Naming INDECI_*; PK natural (sin SEQUENCE). Sin USUARIO_CREO (auditoría
--     centralizada en AuditoriaAspect — D.L. 1451).
--   - ESTADO: BORRADOR | EN_REVISION | ACTIVO | CERRADO | ANULADO (§8/D1).
--   - ES_GRUPO='S' = cabecera de grupo (NO seleccionable como RTPS de un concepto).
--   - El alias CAS≡1057 se mantiene en código (RegimenAplicableHelper); este script
--     NO toca el CHECK de REGIMEN_APLICABLE (§8/D7).
--
-- DEFENSA EN PROFUNDIDAD: idempotente. El seed del catálogo va en V010_98.
-- Ejecutar en esquema GESTIONRRHH / Oracle 19c+.
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
    -- 0) Resolver un TABLESPACE válido (evita ORA-00959 cuando el default
    --    del esquema, TBS_RRHH, no existe). Ancla: INDECI_EMPLEADO →
    --    default del usuario → sin cláusula. Mismo patrón que V010_85/86.
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
        DBMS_OUTPUT.PUT_LINE('Tablespace para INDECI_CONCEPTO_RTPS / índices: ' || v_ts);
    ELSE
        v_ts_clause := '';
    END IF;

    -- ==================================================================
    -- 1) INDECI_CONCEPTO_RTPS — catálogo PDT 601 (PK natural = CODIGO)
    -- ==================================================================
    add_table_if_missing(
        'INDECI_CONCEPTO_RTPS',
        'CREATE TABLE GESTIONRRHH.INDECI_CONCEPTO_RTPS ('
        ||   'CODIGO              VARCHAR2(6 CHAR)   NOT NULL,'   -- preserva ceros (0703, 0704)
        ||   'DESCRIPCION         VARCHAR2(150 CHAR) NOT NULL,'
        ||   'GRUPO_CODIGO        VARCHAR2(6 CHAR),'
        ||   'GRUPO_DESCRIPCION   VARCHAR2(150 CHAR),'
        ||   'ES_GRUPO            VARCHAR2(1 CHAR) DEFAULT ''N'' NOT NULL,'
        ||   'ORDEN               NUMBER(5),'
        ||   'ACTIVO              NUMBER(1) DEFAULT 1 NOT NULL,'
        ||   'FUENTE              VARCHAR2(60 CHAR) DEFAULT ''PDT601_INDECI'','
        ||   'FECHA_CORTE_FUENTE  DATE,'
        ||   'CONSTRAINT INDECI_CONCEPTO_RTPS_PK PRIMARY KEY (CODIGO),'
        ||   'CONSTRAINT INDECI_CONCEPTO_RTPS_GRUPO_CK CHECK (ES_GRUPO IN (''S'', ''N'')),'
        ||   'CONSTRAINT INDECI_CONCEPTO_RTPS_ACTIVO_CK CHECK (ACTIVO IN (0, 1))'
        || ')' || v_ts_clause
    );

    add_index_if_missing(
        'IX_INDECI_CONCEPTO_RTPS_ORDEN',
        'CREATE INDEX GESTIONRRHH.IX_INDECI_CONCEPTO_RTPS_ORDEN '
        || 'ON GESTIONRRHH.INDECI_CONCEPTO_RTPS (ACTIVO, ORDEN)' || v_ts_clause
    );

    -- ==================================================================
    -- 2) INDECI_CONCEPTO_PLANILLA — ESTADO + RTPS_CODIGO (vía add_column_if_missing)
    -- ==================================================================
    add_column_if_missing(
        'INDECI_CONCEPTO_PLANILLA', 'ESTADO',
        'ALTER TABLE GESTIONRRHH.INDECI_CONCEPTO_PLANILLA '
        || 'ADD (ESTADO VARCHAR2(12 CHAR) DEFAULT ''BORRADOR'' NOT NULL)'
    );
    add_constraint_if_missing(
        'INDECI_CONCEPTO_ESTADO_CK',
        'ALTER TABLE GESTIONRRHH.INDECI_CONCEPTO_PLANILLA '
        || 'ADD CONSTRAINT INDECI_CONCEPTO_ESTADO_CK '
        || 'CHECK (ESTADO IN (''BORRADOR'', ''EN_REVISION'', ''ACTIVO'', ''CERRADO'', ''ANULADO''))'
    );

    add_column_if_missing(
        'INDECI_CONCEPTO_PLANILLA', 'RTPS_CODIGO',
        'ALTER TABLE GESTIONRRHH.INDECI_CONCEPTO_PLANILLA '
        || 'ADD (RTPS_CODIGO VARCHAR2(6 CHAR))'
    );
    add_constraint_if_missing(
        'INDECI_CONCEPTO_RTPS_FK',
        'ALTER TABLE GESTIONRRHH.INDECI_CONCEPTO_PLANILLA '
        || 'ADD CONSTRAINT INDECI_CONCEPTO_RTPS_FK '
        || 'FOREIGN KEY (RTPS_CODIGO) '
        || 'REFERENCES GESTIONRRHH.INDECI_CONCEPTO_RTPS (CODIGO)'
    );

    -- ==================================================================
    -- 3) BACKFILL ESTADO (misma migración — crítico)
    --    ACTIVO=1 -> ACTIVO ; ACTIVO=0/NULL -> CERRADO.
    --    Solo toca filas que aún quedaron en el DEFAULT 'BORRADOR' (idempotente:
    --    una re-ejecución no revierte transiciones de estado ya aplicadas por la app).
    -- ==================================================================
    -- SQL DINÁMICO obligatorio: la columna ESTADO se crea en runtime (EXECUTE
    -- IMMEDIATE dentro de add_column_if_missing); un UPDATE estático referenciando
    -- ESTADO no compilaría el bloque (ORA-00904 en tiempo de parseo).
    EXECUTE IMMEDIATE
        'UPDATE GESTIONRRHH.INDECI_CONCEPTO_PLANILLA '
        || 'SET ESTADO = CASE WHEN ACTIVO = 1 THEN ''ACTIVO'' ELSE ''CERRADO'' END '
        || 'WHERE ESTADO = ''BORRADOR''';
    DBMS_OUTPUT.PUT_LINE('Backfill ESTADO: ' || SQL%ROWCOUNT || ' fila(s) actualizada(s).');

    -- ==================================================================
    -- 4) Índice de consulta por ESTADO
    -- ==================================================================
    add_index_if_missing(
        'IX_INDECI_CONCEPTO_ESTADO',
        'CREATE INDEX GESTIONRRHH.IX_INDECI_CONCEPTO_ESTADO '
        || 'ON GESTIONRRHH.INDECI_CONCEPTO_PLANILLA (ESTADO)' || v_ts_clause
    );

    -- ==================================================================
    -- COMMENTS
    -- ==================================================================
    EXECUTE IMMEDIATE 'COMMENT ON TABLE GESTIONRRHH.INDECI_CONCEPTO_RTPS IS '
        || '''SPEC_CONCEPTOS_PLANILLA P1 — Catálogo Tipo Concepto RTPS (PDT 601 INDECI). '
        || 'PK natural CODIGO (preserva ceros). ES_GRUPO=S es cabecera no seleccionable.''';
    EXECUTE IMMEDIATE 'COMMENT ON COLUMN GESTIONRRHH.INDECI_CONCEPTO_RTPS.ES_GRUPO IS '
        || '''S = cabecera de grupo (NO seleccionable por un concepto); N = item seleccionable.''';
    EXECUTE IMMEDIATE 'COMMENT ON COLUMN GESTIONRRHH.INDECI_CONCEPTO_PLANILLA.ESTADO IS '
        || '''Ciclo de vida (P1): BORRADOR | EN_REVISION | ACTIVO | CERRADO | ANULADO. '
        || 'El motor sigue leyendo ACTIVO en P1; ESTADO sincroniza ACTIVO en las transiciones.''';
    EXECUTE IMMEDIATE 'COMMENT ON COLUMN GESTIONRRHH.INDECI_CONCEPTO_PLANILLA.RTPS_CODIGO IS '
        || '''FK a INDECI_CONCEPTO_RTPS(CODIGO) — clasificacion externa PDT 601.''';

    DBMS_OUTPUT.PUT_LINE('V010_97 finalizado.');

    SELECT COUNT(*) INTO v_count
      FROM ALL_TAB_COLUMNS
     WHERE OWNER = 'GESTIONRRHH'
       AND TABLE_NAME = 'INDECI_CONCEPTO_PLANILLA'
       AND COLUMN_NAME IN ('ESTADO', 'RTPS_CODIGO');
    DBMS_OUTPUT.PUT_LINE('Columnas nuevas en INDECI_CONCEPTO_PLANILLA: ' || v_count || ' / 2.');
END;
/
