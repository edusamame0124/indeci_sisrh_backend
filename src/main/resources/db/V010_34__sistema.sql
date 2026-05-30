-- ============================================================================
-- Fase 3 / SSO / V010_34 — Catálogo INDECI_SISTEMA
--                          (Auth Service central — visión multi-sistema)
--
-- Propósito: registrar los sistemas de la entidad (INDECI) que comparten el
-- Auth Service del SISRH. Hoy son 3:
--   - SISRH       (este sistema, Spring Boot 3.3 + Oracle GESTIONRRHH, :8080)
--   - SISCONV     (Convocatoria CAS, Spring Boot 3.3 + Oracle SISCONV,  :8081)
--   - GDR         (Gestión de Rendimiento, Spring Boot + Oracle GDR,    :8082)
--
-- Cada fila la consume el Portal Selector (post-login) para pintar la card
-- correspondiente con su URL base, ícono y orden. La asignación efectiva
-- usuario × sistema vive en INDECI_USUARIO_SISTEMA (V010_35).
--
-- DECISIONES DE DISEÑO:
--   - PK numérica IDENTITY (consistente con USERS y demás tablas auth del
--     SISRH: REGLA real del repo, no la de CLAUDE.md aspiracional).
--   - CODIGO como UK (estable, lo usa el claim "sistemas" del JWT como key).
--   - URL_BASE nullable: para SISRH puede ser NULL (el selector vive dentro
--     del propio SISRH); para sistemas externos es obligatoria en la práctica
--     pero no la enforce el schema (la valida el frontend al renderizar la
--     card de redirección).
--   - URLs sembradas con localhost de dev — TI debe AJUSTAR en deploy productivo.
--
-- ESTRUCTURA: CREATE en bloque PL/SQL idempotente; MERGE de seed FUERA del
-- bloque por la lección de V010_25/V010_33 (ORA-00942 si MERGE estático
-- referencia tabla recién creada en el mismo bloque).
--
-- IDEMPOTENTE: tabla con add_table_if_missing; filas con MERGE WHEN NOT MATCHED.
-- Ejecutar en esquema GESTIONRRHH / Oracle 19c+.
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
         WHERE OWNER = 'GESTIONRRHH' AND TABLE_NAME = p_table_name;
        IF v_exists = 0 THEN
            EXECUTE IMMEDIATE p_create_ddl;
            DBMS_OUTPUT.PUT_LINE(p_table_name || ' -> creada.');
        ELSE
            DBMS_OUTPUT.PUT_LINE(p_table_name || ' ya existe. Sin cambios.');
        END IF;
    END;
BEGIN
    add_table_if_missing(
        'INDECI_SISTEMA',
        'CREATE TABLE GESTIONRRHH.INDECI_SISTEMA ('
        ||   'ID            NUMBER GENERATED ALWAYS AS IDENTITY,'
        ||   'CODIGO        VARCHAR2(30 CHAR)  NOT NULL,'
        ||   'NOMBRE        VARCHAR2(150 CHAR) NOT NULL,'
        ||   'DESCRIPCION   VARCHAR2(500 CHAR),'
        ||   'URL_BASE      VARCHAR2(255 CHAR),'
        ||   'ICONO         VARCHAR2(50 CHAR),'
        ||   'ORDEN         NUMBER(3) DEFAULT 0 NOT NULL,'
        ||   'ACTIVO        NUMBER(1) DEFAULT 1 NOT NULL,'
        ||   'CREATED_AT    TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,'
        ||   'CONSTRAINT INDECI_SISTEMA_PK PRIMARY KEY (ID),'
        ||   'CONSTRAINT INDECI_SISTEMA_CODIGO_UK UNIQUE (CODIGO),'
        ||   'CONSTRAINT INDECI_SISTEMA_ACTIVO_CK CHECK (ACTIVO IN (0, 1))'
        || ')'
    );

    EXECUTE IMMEDIATE 'COMMENT ON TABLE GESTIONRRHH.INDECI_SISTEMA IS '
        || '''Fase 3 SSO: catálogo de sistemas de INDECI que comparten el Auth Service del SISRH. Hoy: SISRH, SISCONV, GDR.''';
    EXECUTE IMMEDIATE 'COMMENT ON COLUMN GESTIONRRHH.INDECI_SISTEMA.CODIGO IS '
        || '''Identificador estable usado como key del claim "sistemas" del JWT. Mantener en minúsculas snake_case si crece.''';
    EXECUTE IMMEDIATE 'COMMENT ON COLUMN GESTIONRRHH.INDECI_SISTEMA.URL_BASE IS '
        || '''URL base del frontend del sistema. NULL para SISRH (selector vive dentro). AJUSTAR en deploy productivo.''';
    EXECUTE IMMEDIATE 'COMMENT ON COLUMN GESTIONRRHH.INDECI_SISTEMA.ICONO IS '
        || '''Nombre de ícono Material para la card del selector (briefcase, badge, chart_bar, etc.).''';
    EXECUTE IMMEDIATE 'COMMENT ON COLUMN GESTIONRRHH.INDECI_SISTEMA.ORDEN IS '
        || '''Orden de aparición en el Portal Selector (ASC). Por defecto 0.''';

    DBMS_OUTPUT.PUT_LINE('V010_34 tabla lista.');
END;
/

-- ----------------------------------------------------------------------------
-- Seed idempotente de los 3 sistemas. WHEN MATCHED omitido a propósito: no
-- pisar URLs/iconos que TI haya corregido en producción.
-- URLs de dev — TI DEBE AJUSTAR en deploy productivo (variable de entorno
-- o UPDATE manual posterior).
-- ----------------------------------------------------------------------------
MERGE INTO GESTIONRRHH.INDECI_SISTEMA d
USING (
    SELECT 'sisrh'        AS CODIGO,
           'SISRH'        AS NOMBRE,
           'Sistema integrador de RRHH y planillas' AS DESCRIPCION,
           CAST(NULL AS VARCHAR2(255 CHAR))         AS URL_BASE,
           'badge'        AS ICONO,
           1              AS ORDEN
      FROM DUAL
    UNION ALL
    SELECT 'convocatoria',
           'Convocatoria',
           'Gestion de procesos de seleccion CAS',
           'http://localhost:4201',
           'assignment_ind',
           2
      FROM DUAL
    UNION ALL
    SELECT 'rendimiento',
           'Rendimiento',
           'Gestion y evaluacion del desempeno',
           'http://localhost:4202',
           'insights',
           3
      FROM DUAL
) s
ON (d.CODIGO = s.CODIGO)
WHEN NOT MATCHED THEN
    INSERT (CODIGO, NOMBRE, DESCRIPCION, URL_BASE, ICONO, ORDEN, ACTIVO, CREATED_AT)
    VALUES (s.CODIGO, s.NOMBRE, s.DESCRIPCION, s.URL_BASE, s.ICONO, s.ORDEN, 1, SYSTIMESTAMP);

COMMIT;
