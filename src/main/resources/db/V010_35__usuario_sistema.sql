-- ============================================================================
-- Fase 3 / SSO / V010_35 — Asignacion usuario x sistema (INDECI_USUARIO_SISTEMA)
--                          (Auth Service central — visión multi-sistema)
--
-- Propósito: registrar qué sistemas externos puede usar un USER del SISRH
-- y con qué roles dentro de cada uno. Habilita:
--   1) El claim "sistemas" del JWT (mapa codigo -> roles[]).
--   2) El Portal Selector post-login (card activa vs. card bloqueada).
--   3) La autorización dentro de SISCONV y GDR (sus filtros leen el claim).
--
-- Importante: los roles del SISRH propio NO se almacenan aquí — siguen en
-- INDECI_USUARIO_ROL + INDECI_ROL (intactos, Fase 1/2). Esta tabla es solo
-- para roles externos opacos al SISRH (EVALUADOR, JEFE_AREA, etc., cuyo
-- significado lo define cada sistema externo).
--
-- DECISIONES DE DISEÑO:
--   - PK numérica IDENTITY (consistente con USERS y resto del schema).
--   - FK USER_ID -> USERS.ID (USERS vive en GESTIONRRHH, mismo schema).
--   - FK SISTEMA_ID -> INDECI_SISTEMA.ID.
--   - ROLES_EXTERNOS como VARCHAR2(500) JSON (p. ej. ["EVALUADOR","CONSULTA"]).
--     Justificación: opacos al SISRH, no se hace JOIN ni búsqueda SQL contra
--     ellos, solo se serializan al claim. Tabla puente sería sobreingeniería.
--   - CK shape JSON básico: empieza con '[' y termina con ']' o es NULL
--     (rol externo "sin roles aún" = card bloqueada). Validación profunda
--     la hace el backend Java al deserializar.
--   - UK (USER_ID, SISTEMA_ID): un usuario tiene una sola entrada por sistema.
--
-- ESTRUCTURA: CREATE en bloque PL/SQL idempotente. Sin seed: TI/ADMIN asigna
-- usuarios mediante endpoint admin (Fase posterior). Para smoke test manual
-- el operador puede insertar directamente.
--
-- IDEMPOTENTE: tabla con add_table_if_missing.
-- Ejecutar en esquema GESTIONRRHH / Oracle 19c+. Requiere V010_34 aplicada.
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
        'INDECI_USUARIO_SISTEMA',
        'CREATE TABLE GESTIONRRHH.INDECI_USUARIO_SISTEMA ('
        ||   'ID              NUMBER GENERATED ALWAYS AS IDENTITY,'
        ||   'USER_ID         NUMBER NOT NULL,'
        ||   'SISTEMA_ID      NUMBER NOT NULL,'
        ||   'ROLES_EXTERNOS  VARCHAR2(500 CHAR),'
        ||   'ACTIVO          NUMBER(1) DEFAULT 1 NOT NULL,'
        ||   'CREATED_AT      TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,'
        ||   'CONSTRAINT INDECI_USUARIO_SISTEMA_PK PRIMARY KEY (ID),'
        ||   'CONSTRAINT INDECI_USUARIO_SISTEMA_UK UNIQUE (USER_ID, SISTEMA_ID),'
        ||   'CONSTRAINT INDECI_USUARIO_SISTEMA_USER_FK '
        ||       'FOREIGN KEY (USER_ID) REFERENCES GESTIONRRHH.USERS(ID),'
        ||   'CONSTRAINT INDECI_USUARIO_SISTEMA_SISTEMA_FK '
        ||       'FOREIGN KEY (SISTEMA_ID) REFERENCES GESTIONRRHH.INDECI_SISTEMA(ID),'
        ||   'CONSTRAINT INDECI_USUARIO_SISTEMA_ACTIVO_CK CHECK (ACTIVO IN (0, 1)),'
        ||   'CONSTRAINT INDECI_USUARIO_SISTEMA_ROLES_CK CHECK ('
        ||       'ROLES_EXTERNOS IS NULL OR ('
        ||           'SUBSTR(ROLES_EXTERNOS, 1, 1) = ''['' '
        ||           'AND SUBSTR(ROLES_EXTERNOS, -1, 1) = '']'''
        ||       ')'
        ||   ')'
        || ')'
    );

    EXECUTE IMMEDIATE 'COMMENT ON TABLE GESTIONRRHH.INDECI_USUARIO_SISTEMA IS '
        || '''Fase 3 SSO: asignacion usuario x sistema con roles externos. Roles propios del SISRH siguen en INDECI_USUARIO_ROL.''';
    EXECUTE IMMEDIATE 'COMMENT ON COLUMN GESTIONRRHH.INDECI_USUARIO_SISTEMA.ROLES_EXTERNOS IS '
        || '''Array JSON de roles externos (p.ej. ["EVALUADOR","CONSULTA"]). Opaco al SISRH: lo interpreta cada sistema externo. NULL = card bloqueada en el selector.''';
    EXECUTE IMMEDIATE 'COMMENT ON COLUMN GESTIONRRHH.INDECI_USUARIO_SISTEMA.ACTIVO IS '
        || '''0 = asignacion revocada (no aparece en el claim "sistemas" del JWT).''';

    DBMS_OUTPUT.PUT_LINE('V010_35 tabla lista.');
END;
/
