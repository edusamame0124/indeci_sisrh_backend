-- ============================================================================
-- Spec M04 / V010_81 — Carga de asistencia F5: versionado + aceptación observadas
--
-- OBJETIVO (decisiones P3/P4 aprobadas — ver SpecAsistencia.md):
--   1. INDECI_ASISTENCIA_CABECERA: VERSION + auditoría de rectificación.
--      - DROP UK (EMPLEADO_ID, PERIODO) — impide conservar versiones.
--      - Índice ÚNICO FUNCIONAL "single-active": permite N versiones inactivas
--        (ACTIVO=0) y exactamente 1 activa (ACTIVO=1) por empleado+periodo.
--      - Índices de apoyo (EMPLEADO_ID, PERIODO, ACTIVO) y (.., VERSION).
--   2. INDECI_ASISTENCIA_IMPORTACION_FILA: auditoría de aceptación de OBSERVADAS
--      (usuario, fecha, motivo) — la columna ACEPTADA_OBSERVADA viene de V010_80.
--   3. INDECI_ASISTENCIA_IMPORTACION: ampliar CHECK ESTADO con 'ANULADA'.
--
-- INVARIANTES:
--   - El motor M05 sigue leyendo la cabecera ACTIVO=1 con ESTADO='VALIDADA'.
--   - NO se borra detalle histórico: cada versión tiene su propio CABECERA_ID.
--   - Idempotente; TABLESPACE ancla de INDECI_EMPLEADO.
--
-- Ejecutar en esquema GESTIONRRHH / Oracle 19c+.
-- ============================================================================

SET SERVEROUTPUT ON;

DECLARE
    v_count NUMBER;
    v_ts    VARCHAR2(30);

    PROCEDURE add_index_if_missing(p_index_name VARCHAR2, p_create_ddl VARCHAR2) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_exists FROM ALL_INDEXES
         WHERE OWNER = 'GESTIONRRHH' AND INDEX_NAME = p_index_name;
        IF v_exists = 0 THEN
            EXECUTE IMMEDIATE p_create_ddl || ' TABLESPACE ' || v_ts;
            DBMS_OUTPUT.PUT_LINE(p_index_name || ' -> creado en TBS ' || v_ts || '.');
        ELSE
            DBMS_OUTPUT.PUT_LINE(p_index_name || ' ya existe. Sin cambios.');
        END IF;
    END;

    PROCEDURE add_column_if_missing(p_table_name VARCHAR2, p_col_name VARCHAR2, p_col_ddl VARCHAR2) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_exists FROM ALL_TAB_COLUMNS
         WHERE OWNER = 'GESTIONRRHH' AND TABLE_NAME = p_table_name AND COLUMN_NAME = p_col_name;
        IF v_exists = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE GESTIONRRHH.' || p_table_name || ' ADD (' || p_col_ddl || ')';
            DBMS_OUTPUT.PUT_LINE(p_table_name || '.' || p_col_name || ' -> agregada.');
        ELSE
            DBMS_OUTPUT.PUT_LINE(p_table_name || '.' || p_col_name || ' ya existe. Sin cambios.');
        END IF;
    END;

    PROCEDURE drop_constraint_if_exists(p_table_name VARCHAR2, p_constraint_name VARCHAR2) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_exists FROM ALL_CONSTRAINTS
         WHERE OWNER = 'GESTIONRRHH' AND TABLE_NAME = p_table_name AND CONSTRAINT_NAME = p_constraint_name;
        IF v_exists > 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE GESTIONRRHH.' || p_table_name
                || ' DROP CONSTRAINT ' || p_constraint_name;
            DBMS_OUTPUT.PUT_LINE(p_constraint_name || ' -> eliminado.');
        ELSE
            DBMS_OUTPUT.PUT_LINE(p_constraint_name || ' no existe. Sin cambios.');
        END IF;
    END;

    PROCEDURE add_constraint_if_missing(p_table_name VARCHAR2, p_constraint_name VARCHAR2, p_ddl VARCHAR2) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_exists FROM ALL_CONSTRAINTS
         WHERE OWNER = 'GESTIONRRHH' AND TABLE_NAME = p_table_name AND CONSTRAINT_NAME = p_constraint_name;
        IF v_exists = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE GESTIONRRHH.' || p_table_name
                || ' ADD CONSTRAINT ' || p_constraint_name || ' ' || p_ddl;
            DBMS_OUTPUT.PUT_LINE(p_constraint_name || ' -> agregado.');
        ELSE
            DBMS_OUTPUT.PUT_LINE(p_constraint_name || ' ya existe. Sin cambios.');
        END IF;
    END;

BEGIN
    SELECT TABLESPACE_NAME INTO v_ts FROM USER_TABLES WHERE TABLE_NAME = 'INDECI_EMPLEADO';
    DBMS_OUTPUT.PUT_LINE('Tablespace ancla (INDECI_EMPLEADO): ' || v_ts);

    -- ==================================================================
    -- 1) CABECERA — versionado + auditoría de rectificación
    -- ==================================================================
    add_column_if_missing('INDECI_ASISTENCIA_CABECERA', 'VERSION',               'VERSION NUMBER DEFAULT 1 NOT NULL');
    add_column_if_missing('INDECI_ASISTENCIA_CABECERA', 'MOTIVO_RECTIFICACION',  'MOTIVO_RECTIFICACION VARCHAR2(500 CHAR)');
    add_column_if_missing('INDECI_ASISTENCIA_CABECERA', 'USUARIO_RECTIFICACION', 'USUARIO_RECTIFICACION VARCHAR2(100 CHAR)');
    add_column_if_missing('INDECI_ASISTENCIA_CABECERA', 'FECHA_RECTIFICACION',   'FECHA_RECTIFICACION TIMESTAMP');
    add_column_if_missing('INDECI_ASISTENCIA_CABECERA', 'AUTORIZADO_POR',        'AUTORIZADO_POR VARCHAR2(100 CHAR)');

    -- Guard: no debe haber 2 cabeceras ACTIVO=1 para el mismo empleado+periodo
    -- antes de cambiar la unicidad (la UK actual ya lo garantizaba).
    SELECT COUNT(*) INTO v_count FROM (
        SELECT EMPLEADO_ID, PERIODO
          FROM GESTIONRRHH.INDECI_ASISTENCIA_CABECERA
         WHERE ACTIVO = 1
         GROUP BY EMPLEADO_ID, PERIODO
        HAVING COUNT(*) > 1);
    IF v_count > 0 THEN
        RAISE_APPLICATION_ERROR(-20081,
            'No se puede migrar: existen ' || v_count
            || ' empleado/periodo con mas de una cabecera ACTIVO=1.');
    END IF;

    -- Reemplazar UK (EMPLEADO_ID, PERIODO) por índice único funcional single-active.
    drop_constraint_if_exists('INDECI_ASISTENCIA_CABECERA', 'INDECI_ASIST_CAB_UK');

    add_index_if_missing(
        'INDECI_ASIST_CAB_ACTIVA_UX',
        'CREATE UNIQUE INDEX GESTIONRRHH.INDECI_ASIST_CAB_ACTIVA_UX '
        || 'ON GESTIONRRHH.INDECI_ASISTENCIA_CABECERA '
        || '(CASE WHEN ACTIVO = 1 THEN EMPLEADO_ID END, '
        || ' CASE WHEN ACTIVO = 1 THEN PERIODO END)');

    add_index_if_missing(
        'IX_INDECI_ASIST_CAB_EMP_PER_ACT',
        'CREATE INDEX GESTIONRRHH.IX_INDECI_ASIST_CAB_EMP_PER_ACT '
        || 'ON GESTIONRRHH.INDECI_ASISTENCIA_CABECERA (EMPLEADO_ID, PERIODO, ACTIVO)');

    add_index_if_missing(
        'IX_INDECI_ASIST_CAB_EMP_PER_VER',
        'CREATE INDEX GESTIONRRHH.IX_INDECI_ASIST_CAB_EMP_PER_VER '
        || 'ON GESTIONRRHH.INDECI_ASISTENCIA_CABECERA (EMPLEADO_ID, PERIODO, VERSION)');

    -- ==================================================================
    -- 1b) FIX desfase de esquema: ESTADO debe alojar 'LISTA_PARA_VALIDAR' (18)
    --     y 'PREVALIDADA' (11). En entornos donde V010_67 no amplió ESTADO,
    --     sigue en VARCHAR2(10) -> ORA-12899 al confirmar/versionar.
    -- ==================================================================
    SELECT CHAR_LENGTH INTO v_count
      FROM ALL_TAB_COLUMNS
     WHERE OWNER = 'GESTIONRRHH' AND TABLE_NAME = 'INDECI_ASISTENCIA_CABECERA'
       AND COLUMN_NAME = 'ESTADO';
    IF v_count < 20 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE GESTIONRRHH.INDECI_ASISTENCIA_CABECERA '
            || 'MODIFY (ESTADO VARCHAR2(20 CHAR))';
        DBMS_OUTPUT.PUT_LINE('INDECI_ASISTENCIA_CABECERA.ESTADO -> ampliada a VARCHAR2(20 CHAR).');
    ELSE
        DBMS_OUTPUT.PUT_LINE('INDECI_ASISTENCIA_CABECERA.ESTADO ya tiene tamano suficiente.');
    END IF;

    -- Re-asegura el CHECK de ESTADO con el set completo (por si V010_67 no corrió aquí).
    drop_constraint_if_exists('INDECI_ASISTENCIA_CABECERA', 'INDECI_ASIST_CAB_ESTADO_CK');
    add_constraint_if_missing(
        'INDECI_ASISTENCIA_CABECERA',
        'INDECI_ASIST_CAB_ESTADO_CK',
        'CHECK (ESTADO IN (''BORRADOR'', ''PREVALIDADA'', ''LISTA_PARA_VALIDAR'', ''OBSERVADA'', ''VALIDADA''))');

    -- ==================================================================
    -- 2) STAGING — auditoría de aceptación de OBSERVADAS (P3)
    -- ==================================================================
    add_column_if_missing('INDECI_ASISTENCIA_IMPORTACION_FILA', 'USUARIO_ACEPTA_OBS', 'USUARIO_ACEPTA_OBS VARCHAR2(100 CHAR)');
    add_column_if_missing('INDECI_ASISTENCIA_IMPORTACION_FILA', 'FECHA_ACEPTA_OBS',   'FECHA_ACEPTA_OBS TIMESTAMP');
    add_column_if_missing('INDECI_ASISTENCIA_IMPORTACION_FILA', 'MOTIVO_ACEPTA_OBS',  'MOTIVO_ACEPTA_OBS VARCHAR2(500 CHAR)');

    -- ==================================================================
    -- 3) IMPORTACION — ampliar CHECK ESTADO con 'ANULADA'
    -- ==================================================================
    drop_constraint_if_exists('INDECI_ASISTENCIA_IMPORTACION', 'INDECI_ASIST_IMPORT_ESTADO_CK');
    add_constraint_if_missing(
        'INDECI_ASISTENCIA_IMPORTACION',
        'INDECI_ASIST_IMPORT_ESTADO_CK',
        'CHECK (ESTADO IN (''BORRADOR_PREVIEW'', ''CONFIRMADA'', ''PARCIAL'', ''FALLIDA'', ''ANULADA''))');

    -- Nota: Oracle NO soporta COMMENT ON INDEX (ORA-32594). El propósito del índice
    -- único funcional single-active se documenta en la cabecera de este script.
    EXECUTE IMMEDIATE 'COMMENT ON COLUMN GESTIONRRHH.INDECI_ASISTENCIA_CABECERA.VERSION IS ''Version de la asistencia del empleado+periodo. ACTIVO=1 es la vigente; las anteriores quedan ACTIVO=0 (P4).''';
    EXECUTE IMMEDIATE 'COMMENT ON COLUMN GESTIONRRHH.INDECI_ASISTENCIA_CABECERA.MOTIVO_RECTIFICACION IS ''Motivo de la rectificacion que genero esta version (P4).''';

    DBMS_OUTPUT.PUT_LINE('V010_81 finalizado.');

    SELECT COUNT(*) INTO v_count FROM ALL_TAB_COLUMNS
     WHERE OWNER = 'GESTIONRRHH' AND TABLE_NAME = 'INDECI_ASISTENCIA_CABECERA'
       AND COLUMN_NAME IN ('VERSION','MOTIVO_RECTIFICACION','USUARIO_RECTIFICACION','FECHA_RECTIFICACION','AUTORIZADO_POR');
    DBMS_OUTPUT.PUT_LINE('Columnas cabecera versionado: ' || v_count || ' / 5.');

    SELECT COUNT(*) INTO v_count FROM ALL_INDEXES
     WHERE OWNER = 'GESTIONRRHH'
       AND INDEX_NAME IN ('INDECI_ASIST_CAB_ACTIVA_UX','IX_INDECI_ASIST_CAB_EMP_PER_ACT','IX_INDECI_ASIST_CAB_EMP_PER_VER');
    DBMS_OUTPUT.PUT_LINE('Indices versionado: ' || v_count || ' / 3.');
END;
/
