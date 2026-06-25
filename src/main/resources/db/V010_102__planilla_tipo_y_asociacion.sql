-- ============================================================================
-- SPEC_CONCEPTOS_PLANILLA §15 / Fase A / V010_102 — Tipo de Planilla + asociación M:N
--
-- OBJETIVO (Fase A = METADATA/clasificación, CERO cambios en el motor):
--   1. INDECI_PLANILLA_TIPO — catálogo administrable de tipos de planilla/cédula
--      (CAS, CAS TEMPORAL, CAS ADICIONAL; la entidad agrega más; SIN 728). PK natural.
--   2. INDECI_CONCEPTO_PLANILLA_TIPO — asociación M:N concepto ↔ tipo de planilla.
--      Un concepto declara ≥1 tipo de planilla (la regla ≥1 se valida en la app;
--      la BD garantiza la integridad referencial + unicidad del par).
--
-- DECISIONES DE DISEÑO:
--   - Naming INDECI_*; catálogo con PK natural (sin SEQUENCE); asociación con
--     ID IDENTITY (transaccional). Sin USUARIO_CREO (auditoría centralizada en
--     AuditoriaAspect — D.L. 1451).
--   - La generación de planilla NO filtra por tipo en Fase A (eso es Fase B).
--
-- DEFENSA EN PROFUNDIDAD: idempotente + tablespace-safe (réplica del bloque de
-- resolución de TABLESPACE de V010_97/100). Ejecutar en GESTIONRRHH / Oracle 19c+.
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
    --    default del usuario → sin cláusula. Mismo patrón que V010_97/100.
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
        DBMS_OUTPUT.PUT_LINE('Tablespace para INDECI_PLANILLA_TIPO / asociación / índices: ' || v_ts);
    ELSE
        v_ts_clause := '';
    END IF;

    -- ==================================================================
    -- 1) INDECI_PLANILLA_TIPO — catálogo tipos de planilla (PK natural = CODIGO)
    -- ==================================================================
    add_table_if_missing(
        'INDECI_PLANILLA_TIPO',
        'CREATE TABLE GESTIONRRHH.INDECI_PLANILLA_TIPO ('
        ||   'CODIGO  VARCHAR2(20 CHAR) NOT NULL,'
        ||   'NOMBRE  VARCHAR2(60 CHAR) NOT NULL,'
        ||   'ORDEN   NUMBER(3),'
        ||   'ACTIVO  NUMBER(1) DEFAULT 1 NOT NULL,'
        ||   'CONSTRAINT INDECI_PLANILLA_TIPO_PK PRIMARY KEY (CODIGO),'
        ||   'CONSTRAINT INDECI_PLANILLA_TIPO_ACT_CK CHECK (ACTIVO IN (0, 1))'
        || ')' || v_ts_clause
    );

    add_index_if_missing(
        'IX_INDECI_PLANILLA_TIPO_ORDEN',
        'CREATE INDEX GESTIONRRHH.IX_INDECI_PLANILLA_TIPO_ORDEN '
        || 'ON GESTIONRRHH.INDECI_PLANILLA_TIPO (ACTIVO, ORDEN)' || v_ts_clause
    );

    -- ==================================================================
    -- 2) INDECI_CONCEPTO_PLANILLA_TIPO — asociación M:N (ID IDENTITY)
    -- ==================================================================
    add_table_if_missing(
        'INDECI_CONCEPTO_PLANILLA_TIPO',
        'CREATE TABLE GESTIONRRHH.INDECI_CONCEPTO_PLANILLA_TIPO ('
        ||   'ID                   NUMBER GENERATED BY DEFAULT AS IDENTITY,'
        ||   'CONCEPTO_PLANILLA_ID NUMBER             NOT NULL,'
        ||   'PLANILLA_TIPO_CODIGO VARCHAR2(20 CHAR)  NOT NULL,'
        ||   'CONSTRAINT INDECI_CONC_PLA_TIPO_PK PRIMARY KEY (ID),'
        ||   'CONSTRAINT INDECI_CONC_PLA_TIPO_UK '
        ||     'UNIQUE (CONCEPTO_PLANILLA_ID, PLANILLA_TIPO_CODIGO),'
        ||   'CONSTRAINT INDECI_CONC_PLA_TIPO_CONC_FK '
        ||     'FOREIGN KEY (CONCEPTO_PLANILLA_ID) '
        ||     'REFERENCES GESTIONRRHH.INDECI_CONCEPTO_PLANILLA (ID),'
        ||   'CONSTRAINT INDECI_CONC_PLA_TIPO_TIPO_FK '
        ||     'FOREIGN KEY (PLANILLA_TIPO_CODIGO) '
        ||     'REFERENCES GESTIONRRHH.INDECI_PLANILLA_TIPO (CODIGO)'
        || ')' || v_ts_clause
    );

    add_index_if_missing(
        'IX_INDECI_CONC_PLA_TIPO_CONC',
        'CREATE INDEX GESTIONRRHH.IX_INDECI_CONC_PLA_TIPO_CONC '
        || 'ON GESTIONRRHH.INDECI_CONCEPTO_PLANILLA_TIPO (CONCEPTO_PLANILLA_ID)' || v_ts_clause
    );

    -- ==================================================================
    -- COMMENTS
    -- ==================================================================
    EXECUTE IMMEDIATE 'COMMENT ON TABLE GESTIONRRHH.INDECI_PLANILLA_TIPO IS '
        || '''SPEC_CONCEPTOS_PLANILLA §15 / Fase A — Catálogo administrable de tipos de '
        || 'planilla/cédula (CAS, CAS TEMPORAL, CAS ADICIONAL, ...). Metadata; el motor no filtra por tipo en Fase A.''';
    EXECUTE IMMEDIATE 'COMMENT ON TABLE GESTIONRRHH.INDECI_CONCEPTO_PLANILLA_TIPO IS '
        || '''SPEC_CONCEPTOS_PLANILLA §15 / Fase A — Asociación M:N concepto ↔ tipo de planilla. '
        || 'Un concepto declara >=1 tipo (validado en la app).''';

    DBMS_OUTPUT.PUT_LINE('V010_102 DDL finalizado.');
END;
/

-- ============================================================================
-- 3) SEED del catálogo de tipos de planilla (MERGE idempotente).
--    Valores iniciales §15: CAS, CAS TEMPORAL, CAS ADICIONAL. La entidad agrega más.
-- ============================================================================
MERGE INTO GESTIONRRHH.INDECI_PLANILLA_TIPO d
USING (
    SELECT 'CAS'      AS CODIGO, 'CAS'           AS NOMBRE, 10 AS ORDEN FROM DUAL UNION ALL
    SELECT 'CAS_TEMP',          'CAS TEMPORAL',          20 FROM DUAL UNION ALL
    SELECT 'CAS_ADIC',          'CAS ADICIONAL',         30 FROM DUAL
) s
ON (d.CODIGO = s.CODIGO)
WHEN MATCHED THEN UPDATE SET
    d.NOMBRE = s.NOMBRE,
    d.ORDEN  = s.ORDEN,
    d.ACTIVO = 1
WHEN NOT MATCHED THEN INSERT
    (CODIGO, NOMBRE, ORDEN, ACTIVO)
VALUES
    (s.CODIGO, s.NOMBRE, s.ORDEN, 1);

COMMIT;
