-- ============================================================================
-- Spec 015 / SSO / V010_45 - Catalogo de roles por sistema
--
-- Proposito: centralizar en SISRH los codigos de rol externos que pueden
-- asignarse desde la consola de Gestion de Usuarios. Los significados finos de
-- cada rol siguen viviendo en Convocatoria/GDR.
--
-- Requiere V010_34 (INDECI_SISTEMA). Idempotente para Oracle 19c+.
-- ============================================================================

SET SERVEROUTPUT ON;

DECLARE
    PROCEDURE add_table_if_missing(
        p_table_name VARCHAR2,
        p_create_ddl VARCHAR2
    ) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_exists
          FROM ALL_TABLES
         WHERE OWNER = 'GESTIONRRHH'
           AND TABLE_NAME = p_table_name;

        IF v_exists = 0 THEN
            EXECUTE IMMEDIATE p_create_ddl;
            DBMS_OUTPUT.PUT_LINE(p_table_name || ' -> creada.');
        ELSE
            DBMS_OUTPUT.PUT_LINE(p_table_name || ' ya existe. Sin cambios.');
        END IF;
    END;
BEGIN
    add_table_if_missing(
        'INDECI_SISTEMA_ROL',
        'CREATE TABLE GESTIONRRHH.INDECI_SISTEMA_ROL ('
        ||   'ID           NUMBER GENERATED ALWAYS AS IDENTITY,'
        ||   'SISTEMA_ID   NUMBER NOT NULL,'
        ||   'CODIGO_ROL   VARCHAR2(40 CHAR) NOT NULL,'
        ||   'NOMBRE       VARCHAR2(100 CHAR) NOT NULL,'
        ||   'DESCRIPCION  VARCHAR2(300 CHAR),'
        ||   'ORDEN        NUMBER(3) DEFAULT 0,'
        ||   'ACTIVO       NUMBER(1) DEFAULT 1 NOT NULL,'
        ||   'CONSTRAINT INDECI_SISTEMA_ROL_PK PRIMARY KEY (ID),'
        ||   'CONSTRAINT INDECI_SISTEMA_ROL_UK UNIQUE (SISTEMA_ID, CODIGO_ROL),'
        ||   'CONSTRAINT INDECI_SISTEMA_ROL_SIS_FK '
        ||       'FOREIGN KEY (SISTEMA_ID) REFERENCES GESTIONRRHH.INDECI_SISTEMA(ID),'
        ||   'CONSTRAINT INDECI_SISTEMA_ROL_ACTIVO_CK CHECK (ACTIVO IN (0, 1))'
        || ')'
    );

    EXECUTE IMMEDIATE 'COMMENT ON TABLE GESTIONRRHH.INDECI_SISTEMA_ROL IS '
        || '''Spec 015: catalogo de roles externos asignables desde Gestion de Usuarios.''';
    EXECUTE IMMEDIATE 'COMMENT ON COLUMN GESTIONRRHH.INDECI_SISTEMA_ROL.CODIGO_ROL IS '
        || '''Codigo exacto que viaja en el claim sistemas del JWT.''';
END;
/

MERGE INTO GESTIONRRHH.INDECI_SISTEMA_ROL d
USING (
    SELECT s.ID AS SISTEMA_ID, 'ROLE_ADMIN' AS CODIGO_ROL, 'Administrador' AS NOMBRE,
           'Administrador del sistema de convocatoria' AS DESCRIPCION, 1 AS ORDEN
      FROM GESTIONRRHH.INDECI_SISTEMA s WHERE s.CODIGO = 'convocatoria'
    UNION ALL
    SELECT s.ID, 'ROLE_ORH', 'ORH', 'Oficina de Recursos Humanos', 2
      FROM GESTIONRRHH.INDECI_SISTEMA s WHERE s.CODIGO = 'convocatoria'
    UNION ALL
    SELECT s.ID, 'ROLE_OPP', 'OPP', 'Oficina de Planeamiento y Presupuesto', 3
      FROM GESTIONRRHH.INDECI_SISTEMA s WHERE s.CODIGO = 'convocatoria'
    UNION ALL
    SELECT s.ID, 'ROLE_AREA_SOLICITANTE', 'Area Solicitante', 'Area solicitante del proceso CAS', 4
      FROM GESTIONRRHH.INDECI_SISTEMA s WHERE s.CODIGO = 'convocatoria'
    UNION ALL
    SELECT s.ID, 'ROLE_COMITE', 'Comite de Seleccion', 'Comite de seleccion CAS', 5
      FROM GESTIONRRHH.INDECI_SISTEMA s WHERE s.CODIGO = 'convocatoria'
    UNION ALL
    SELECT s.ID, 'ROLE_POSTULANTE', 'Postulante', 'Postulante registrado en convocatoria', 6
      FROM GESTIONRRHH.INDECI_SISTEMA s WHERE s.CODIGO = 'convocatoria'
    UNION ALL
    SELECT s.ID, 'ADMIN_SISTEMA', 'Admin Sistema', 'Administrador del sistema GDR', 1
      FROM GESTIONRRHH.INDECI_SISTEMA s WHERE s.CODIGO = 'rendimiento'
    UNION ALL
    SELECT s.ID, 'GDR_ORH', 'ORH', 'Oficina de Recursos Humanos en GDR', 2
      FROM GESTIONRRHH.INDECI_SISTEMA s WHERE s.CODIGO = 'rendimiento'
    UNION ALL
    SELECT s.ID, 'GDR_JUNTA_DIRECTIVOS', 'Junta de Directivos', 'Junta de directivos GDR', 3
      FROM GESTIONRRHH.INDECI_SISTEMA s WHERE s.CODIGO = 'rendimiento'
    UNION ALL
    SELECT s.ID, 'GDR_USUARIO', 'Usuario GDR', 'Usuario operativo GDR', 4
      FROM GESTIONRRHH.INDECI_SISTEMA s WHERE s.CODIGO = 'rendimiento'
    UNION ALL
    SELECT s.ID, 'GDR_CONSULTA', 'Consulta', 'Consulta de informacion GDR', 5
      FROM GESTIONRRHH.INDECI_SISTEMA s WHERE s.CODIGO = 'rendimiento'
) s
ON (d.SISTEMA_ID = s.SISTEMA_ID AND d.CODIGO_ROL = s.CODIGO_ROL)
WHEN NOT MATCHED THEN
    INSERT (SISTEMA_ID, CODIGO_ROL, NOMBRE, DESCRIPCION, ORDEN, ACTIVO)
    VALUES (s.SISTEMA_ID, s.CODIGO_ROL, s.NOMBRE, s.DESCRIPCION, s.ORDEN, 1);

COMMIT;
