-- ============================================================================
-- Spec 013 / B3 / V010_33 — Configuración institucional (RUC + cód entidad)
--                          (Etapa 3 · B3 · M09/M14)
--
-- No existía una tabla de entidad en el schema. PLAME necesita el RUC para el
-- nombre del archivo (0601 + YYYY + MM + RUC) y MCPP el código de entidad
-- (000009, presente en todos los PLL*.TXT). Esta tabla centraliza esa config.
--
-- DECISIONES DE DISEÑO:
--   - PK natural COD_ENTIDAD (config estable, single-tenant hoy).
--   - RUC nullable: se siembra el valor DERIVADO del nombre del archivo PLAME
--     real (060120260320135890031 → RUC 20135890031). RRHH debe CONFIRMARLO
--     antes de la primera declaración productiva (LEY-01: no usar identificadores
--     sin validar).
--
-- ESTRUCTURA: el CREATE va en un bloque PL/SQL (idempotente) y el seed MERGE va
-- FUERA del bloque, como sentencia independiente. Motivo: PL/SQL compila el bloque
-- completo antes de ejecutarlo; un MERGE estático contra una tabla creada por
-- EXECUTE IMMEDIATE en el mismo bloque fallaría con ORA-00942 (la tabla aún no
-- existe en tiempo de compilación). Misma lección que V010_25.
--
-- IDEMPOTENTE: tabla con add_table_if_missing; fila con MERGE WHEN NOT MATCHED.
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
        'INDECI_ENTIDAD',
        'CREATE TABLE GESTIONRRHH.INDECI_ENTIDAD ('
        ||   'COD_ENTIDAD   VARCHAR2(6 CHAR)   NOT NULL,'
        ||   'RUC           VARCHAR2(11 CHAR),'
        ||   'RAZON_SOCIAL  VARCHAR2(150 CHAR),'
        ||   'ACTIVO        NUMBER(1) DEFAULT 1 NOT NULL,'
        ||   'CREATED_AT    TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,'
        ||   'CONSTRAINT INDECI_ENTIDAD_PK PRIMARY KEY (COD_ENTIDAD),'
        ||   'CONSTRAINT INDECI_ENTIDAD_ACTIVO_CK CHECK (ACTIVO IN (0, 1))'
        || ')'
    );

    EXECUTE IMMEDIATE 'COMMENT ON TABLE GESTIONRRHH.INDECI_ENTIDAD IS '
        || '''B3 — Config institucional: COD_ENTIDAD (MCPP) y RUC (nombre archivo PLAME).''';
    EXECUTE IMMEDIATE 'COMMENT ON COLUMN GESTIONRRHH.INDECI_ENTIDAD.RUC IS '
        || '''RUC para el nombre del archivo PLAME (0601+YYYY+MM+RUC). Valor sembrado derivado del archivo real; confirmar con RRHH.''';

    DBMS_OUTPUT.PUT_LINE('V010_33 tabla lista.');
END;
/

-- ----------------------------------------------------------------------------
-- Seed idempotente de la entidad INDECI (FUERA del bloque: la tabla ya existe).
-- RUC DERIVADO del nombre del archivo PLAME real — CONFIRMAR con RRHH.
-- WHEN MATCHED omitido a propósito: no pisar un RUC ya corregido por RRHH.
-- ----------------------------------------------------------------------------
MERGE INTO GESTIONRRHH.INDECI_ENTIDAD d
USING (
    SELECT '000009'                              AS COD_ENTIDAD,
           '20135890031'                         AS RUC,
           'INSTITUTO NACIONAL DE DEFENSA CIVIL' AS RAZON_SOCIAL
      FROM DUAL
) s
ON (d.COD_ENTIDAD = s.COD_ENTIDAD)
WHEN NOT MATCHED THEN
    INSERT (COD_ENTIDAD, RUC, RAZON_SOCIAL, ACTIVO, CREATED_AT)
    VALUES (s.COD_ENTIDAD, s.RUC, s.RAZON_SOCIAL, 1, SYSTIMESTAMP);

COMMIT;
