SET SERVEROUTPUT ON;

-- Tabla hereda TABLESPACE de INDECI_EMPLEADO (ancla estable del schema).
-- Evita ORA-00959 cuando el default tablespace del usuario no existe (TBS_RRHH).
-- FKs se agregan por separado para evitar ORA-00942 si SS_USUARIO/SS_PERMISO
-- aun no existen al momento de ejecutar esta migracion.
DECLARE
    v_ts     VARCHAR2(30);
    v_exists NUMBER;

    PROCEDURE add_fk_if_missing(
        p_table_name      VARCHAR2,
        p_constraint_name VARCHAR2,
        p_ref_table       VARCHAR2,
        p_constraint_ddl  VARCHAR2
    ) IS
        v_ref_exists NUMBER;
        v_fk_exists  NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_ref_exists FROM USER_TABLES WHERE TABLE_NAME = p_ref_table;
        IF v_ref_exists = 0 THEN
            DBMS_OUTPUT.PUT_LINE(p_constraint_name || ' -> omitido (' || p_ref_table || ' no existe aun).');
            RETURN;
        END IF;
        SELECT COUNT(*) INTO v_fk_exists FROM USER_CONSTRAINTS
         WHERE TABLE_NAME = p_table_name AND CONSTRAINT_NAME = p_constraint_name;
        IF v_fk_exists = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE ' || p_table_name
                || ' ADD CONSTRAINT ' || p_constraint_name || ' ' || p_constraint_ddl;
            DBMS_OUTPUT.PUT_LINE(p_constraint_name || ' -> creada.');
        ELSE
            DBMS_OUTPUT.PUT_LINE(p_constraint_name || ' ya existe. Sin cambios.');
        END IF;
    END;

BEGIN
    SELECT TABLESPACE_NAME INTO v_ts
      FROM USER_TABLES
     WHERE TABLE_NAME = 'INDECI_EMPLEADO';

    -- 1) Crear tabla (solo PK; FKs se agregan abajo con verificacion)
    SELECT COUNT(*) INTO v_exists FROM USER_TABLES WHERE TABLE_NAME = 'SS_USUARIO_PERMISO';
    IF v_exists = 0 THEN
        EXECUTE IMMEDIATE
            'CREATE TABLE SS_USUARIO_PERMISO ('
            || 'ID_USER    NUMBER NOT NULL,'
            || 'ID_PERMISO NUMBER NOT NULL,'
            || 'CONSTRAINT SS_USUARIO_PERMISO_PK PRIMARY KEY (ID_USER, ID_PERMISO)'
            || ') TABLESPACE ' || v_ts;
        DBMS_OUTPUT.PUT_LINE('SS_USUARIO_PERMISO -> creada en TBS ' || v_ts);
    ELSE
        DBMS_OUTPUT.PUT_LINE('SS_USUARIO_PERMISO ya existe. Sin cambios.');
    END IF;

    -- 2) FKs (omitidas silenciosamente si la tabla referenciada aun no existe)
    add_fk_if_missing(
        'SS_USUARIO_PERMISO', 'SS_USUARIO_PERMISO_USR_FK', 'USERS',
        'FOREIGN KEY (ID_USER) REFERENCES USERS(ID)'
    );
    add_fk_if_missing(
        'SS_USUARIO_PERMISO', 'SS_USUARIO_PERMISO_PRM_FK', 'SS_PERMISO',
        'FOREIGN KEY (ID_PERMISO) REFERENCES SS_PERMISO(ID_PERMISO)'
    );

    -- 3) Comentario
    EXECUTE IMMEDIATE 'COMMENT ON TABLE SS_USUARIO_PERMISO IS '
        || '''Spec 015: permisos SISRH otorgados directamente al usuario (independiente del rol).''';

    DBMS_OUTPUT.PUT_LINE('V010_46 lista.');
END;
/

COMMIT;
