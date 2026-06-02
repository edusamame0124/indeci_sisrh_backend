SET SERVEROUTPUT ON;

-- ============================================================================
-- Spec 015 / SSO / V010_47 - Catalogo de areas por sistema + campo AREA_CODIGO
--
-- Proposito: permite asignar desde la consola de Gestion de Usuarios el area
-- organizacional del usuario en sistemas externos (p. ej. Convocatoria).
-- La lista de areas de Convocatoria refleja TBL_AREA_ORGANIZACIONAL de SISCONV.
-- ============================================================================

DECLARE
    v_ts     VARCHAR2(30);
    v_exists NUMBER;

    PROCEDURE add_col_if_missing(p_table VARCHAR2, p_col VARCHAR2, p_ddl VARCHAR2) IS
        v_col_exists NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_col_exists FROM USER_TAB_COLUMNS
         WHERE TABLE_NAME = p_table AND COLUMN_NAME = p_col;
        IF v_col_exists = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE ' || p_table || ' ADD ' || p_col || ' ' || p_ddl;
            DBMS_OUTPUT.PUT_LINE(p_table || '.' || p_col || ' -> columna agregada.');
        ELSE
            DBMS_OUTPUT.PUT_LINE(p_table || '.' || p_col || ' ya existe. Sin cambios.');
        END IF;
    END;

BEGIN
    SELECT TABLESPACE_NAME INTO v_ts
      FROM USER_TABLES WHERE TABLE_NAME = 'INDECI_EMPLEADO';

    -- 1) Tabla catalogo de areas por sistema
    SELECT COUNT(*) INTO v_exists FROM USER_TABLES WHERE TABLE_NAME = 'INDECI_SISTEMA_AREA';
    IF v_exists = 0 THEN
        EXECUTE IMMEDIATE
            'CREATE TABLE INDECI_SISTEMA_AREA ('
            ||   'ID           NUMBER GENERATED ALWAYS AS IDENTITY,'
            ||   'SISTEMA_ID   NUMBER NOT NULL,'
            ||   'CODIGO_AREA  VARCHAR2(40 CHAR) NOT NULL,'
            ||   'NOMBRE_AREA  VARCHAR2(150 CHAR) NOT NULL,'
            ||   'SIGLA        VARCHAR2(20 CHAR),'
            ||   'ORDEN        NUMBER(3) DEFAULT 0,'
            ||   'ACTIVO       NUMBER(1) DEFAULT 1 NOT NULL,'
            ||   'CONSTRAINT INDECI_SISTEMA_AREA_PK PRIMARY KEY (ID),'
            ||   'CONSTRAINT INDECI_SISTEMA_AREA_UK UNIQUE (SISTEMA_ID, CODIGO_AREA),'
            ||   'CONSTRAINT INDECI_SISTEMA_AREA_SIS_FK '
            ||       'FOREIGN KEY (SISTEMA_ID) REFERENCES INDECI_SISTEMA(ID),'
            ||   'CONSTRAINT INDECI_SISTEMA_AREA_ACTIVO_CK CHECK (ACTIVO IN (0, 1))'
            || ') TABLESPACE ' || v_ts;
        DBMS_OUTPUT.PUT_LINE('INDECI_SISTEMA_AREA -> creada en TBS ' || v_ts);
    ELSE
        DBMS_OUTPUT.PUT_LINE('INDECI_SISTEMA_AREA ya existe. Sin cambios.');
    END IF;

    EXECUTE IMMEDIATE 'COMMENT ON TABLE INDECI_SISTEMA_AREA IS '
        || '''Spec 015: catalogo de areas organizacionales asignables por sistema externo.''';

    -- 2) Columna AREA_CODIGO en INDECI_USUARIO_SISTEMA
    add_col_if_missing('INDECI_USUARIO_SISTEMA', 'AREA_CODIGO', 'VARCHAR2(40 CHAR)');

    EXECUTE IMMEDIATE 'COMMENT ON COLUMN INDECI_USUARIO_SISTEMA.AREA_CODIGO IS '
        || '''Spec 015: area organizacional del usuario en el sistema externo (INDECI_SISTEMA_AREA.CODIGO_AREA).''';

    DBMS_OUTPUT.PUT_LINE('V010_47 estructura lista.');
END;
/

-- Seed areas de Convocatoria (refleja TBL_AREA_ORGANIZACIONAL de SISCONV)
MERGE INTO INDECI_SISTEMA_AREA d
USING (
    SELECT s.ID AS SISTEMA_ID,
           'DAP'       AS CODIGO_AREA, 'Direccion de Adquisiciones y Programacion'  AS NOMBRE_AREA, 'DAP'       AS SIGLA, 1 AS ORDEN FROM INDECI_SISTEMA s WHERE s.CODIGO = 'convocatoria'
    UNION ALL
    SELECT s.ID, 'JEFATURA',  'Jefatura de la ACFFAA',                  'JEFATURA',  2 FROM INDECI_SISTEMA s WHERE s.CODIGO = 'convocatoria'
    UNION ALL
    SELECT s.ID, 'OGA',       'Oficina General de Administracion',       'OGA',       3 FROM INDECI_SISTEMA s WHERE s.CODIGO = 'convocatoria'
    UNION ALL
    SELECT s.ID, 'OI',        'Oficina de Informatica',                  'OI',        4 FROM INDECI_SISTEMA s WHERE s.CODIGO = 'convocatoria'
    UNION ALL
    SELECT s.ID, 'OPP',       'Oficina de Planeamiento y Presupuesto',   'OPP',       5 FROM INDECI_SISTEMA s WHERE s.CODIGO = 'convocatoria'
    UNION ALL
    SELECT s.ID, 'ORH',       'Oficina de Recursos Humanos',             'ORH',       6 FROM INDECI_SISTEMA s WHERE s.CODIGO = 'convocatoria'
) s
ON (d.SISTEMA_ID = s.SISTEMA_ID AND d.CODIGO_AREA = s.CODIGO_AREA)
WHEN NOT MATCHED THEN
    INSERT (SISTEMA_ID, CODIGO_AREA, NOMBRE_AREA, SIGLA, ORDEN, ACTIVO)
    VALUES (s.SISTEMA_ID, s.CODIGO_AREA, s.NOMBRE_AREA, s.SIGLA, s.ORDEN, 1);

COMMIT;
