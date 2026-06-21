-- ============================================================================
-- Spec M04 / V010_80 — Carga de asistencia F1: columnas completas en staging
--                       + auditoría de importación
--
-- OBJETIVO (decisiones P aprobadas — ver SpecAsistencia.md):
--   1. INDECI_ASISTENCIA_IMPORTACION_FILA (staging): persistir TODAS las columnas
--      del CSV del marcador + duraciones como minutos NUMBER (P2, P7, req 21/22).
--   2. INDECI_ASISTENCIA_IMPORTACION (cabecera import): columnas de auditoría de
--      validación y confirmación + tamaño + periodo detectado (req 19, P4).
--   3. Índice de filtro para paginación server-side del detalle (req 11/12, P8).
--
-- DECISIONES:
--   - NO se agrega UNIQUE(EMPLEADO_ID, FECHA) en staging: por P1 los duplicados
--     por empleado+fecha DEBEN almacenarse (marcados OBSERVADA/ERROR) para poder
--     revisarlos. La unicidad empleado+fecha se garantiza en el modelo FINAL
--     (UK cabecera EMPLEADO_ID+PERIODO + UK detalle CABECERA_ID+DIA).
--   - Naming INDECI_*; idempotente (add_column/index_if_missing) al estilo V010_67.
--   - TABLESPACE ancla de INDECI_EMPLEADO (evita ORA-00959).
--   - Sin USUARIO_CREO: auditoría de acción centralizada en AuditoriaAspect; aquí
--     se persisten columnas consultables del CICLO de la importación.
--
-- Ejecutar en esquema GESTIONRRHH / Oracle 19c+.
-- ============================================================================

SET SERVEROUTPUT ON;

DECLARE
    v_count NUMBER;
    v_ts    VARCHAR2(30);

    PROCEDURE add_index_if_missing(
        p_index_name VARCHAR2,
        p_create_ddl VARCHAR2
    ) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*)
          INTO v_exists
          FROM ALL_INDEXES
         WHERE OWNER      = 'GESTIONRRHH'
           AND INDEX_NAME = p_index_name;

        IF v_exists = 0 THEN
            EXECUTE IMMEDIATE p_create_ddl || ' TABLESPACE ' || v_ts;
            DBMS_OUTPUT.PUT_LINE(p_index_name || ' -> creado en TBS ' || v_ts || '.');
        ELSE
            DBMS_OUTPUT.PUT_LINE(p_index_name || ' ya existe. Sin cambios.');
        END IF;
    END;

    PROCEDURE add_column_if_missing(
        p_table_name VARCHAR2,
        p_col_name   VARCHAR2,
        p_col_ddl    VARCHAR2
    ) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*)
          INTO v_exists
          FROM ALL_TAB_COLUMNS
         WHERE OWNER       = 'GESTIONRRHH'
           AND TABLE_NAME  = p_table_name
           AND COLUMN_NAME = p_col_name;

        IF v_exists = 0 THEN
            EXECUTE IMMEDIATE
                'ALTER TABLE GESTIONRRHH.' || p_table_name || ' ADD ('
                || p_col_ddl || ')';
            DBMS_OUTPUT.PUT_LINE(p_table_name || '.' || p_col_name || ' -> agregada.');
        ELSE
            DBMS_OUTPUT.PUT_LINE(p_table_name || '.' || p_col_name || ' ya existe. Sin cambios.');
        END IF;
    END;

    PROCEDURE add_constraint_if_missing(
        p_table_name      VARCHAR2,
        p_constraint_name VARCHAR2,
        p_constraint_ddl  VARCHAR2
    ) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*)
          INTO v_exists
          FROM ALL_CONSTRAINTS
         WHERE OWNER           = 'GESTIONRRHH'
           AND TABLE_NAME      = p_table_name
           AND CONSTRAINT_NAME = p_constraint_name;

        IF v_exists = 0 THEN
            EXECUTE IMMEDIATE
                'ALTER TABLE GESTIONRRHH.' || p_table_name
                || ' ADD CONSTRAINT ' || p_constraint_name || ' ' || p_constraint_ddl;
            DBMS_OUTPUT.PUT_LINE(p_constraint_name || ' -> agregado.');
        ELSE
            DBMS_OUTPUT.PUT_LINE(p_constraint_name || ' ya existe. Sin cambios.');
        END IF;
    END;

BEGIN
    SELECT TABLESPACE_NAME
      INTO v_ts
      FROM USER_TABLES
     WHERE TABLE_NAME = 'INDECI_EMPLEADO';

    DBMS_OUTPUT.PUT_LINE('Tablespace ancla (INDECI_EMPLEADO): ' || v_ts);

    -- ==================================================================
    -- 1) STAGING: columnas completas del CSV + minutos NUMBER
    -- ==================================================================
    add_column_if_missing('INDECI_ASISTENCIA_IMPORTACION_FILA', 'NOMBRE_CSV',            'NOMBRE_CSV VARCHAR2(150 CHAR)');
    add_column_if_missing('INDECI_ASISTENCIA_IMPORTACION_FILA', 'NOMBRE_SISTEMA',        'NOMBRE_SISTEMA VARCHAR2(150 CHAR)');
    add_column_if_missing('INDECI_ASISTENCIA_IMPORTACION_FILA', 'DIA_SEMANA',            'DIA_SEMANA VARCHAR2(12 CHAR)');
    add_column_if_missing('INDECI_ASISTENCIA_IMPORTACION_FILA', 'ENTRADA_PROG',          'ENTRADA_PROG VARCHAR2(8 CHAR)');
    add_column_if_missing('INDECI_ASISTENCIA_IMPORTACION_FILA', 'SALIDA_PROG',           'SALIDA_PROG VARCHAR2(8 CHAR)');
    add_column_if_missing('INDECI_ASISTENCIA_IMPORTACION_FILA', 'MARCA3',                'MARCA3 VARCHAR2(16 CHAR)');
    add_column_if_missing('INDECI_ASISTENCIA_IMPORTACION_FILA', 'MARCA4',                'MARCA4 VARCHAR2(16 CHAR)');
    add_column_if_missing('INDECI_ASISTENCIA_IMPORTACION_FILA', 'TARDANZA_MIN',          'TARDANZA_MIN NUMBER(5) DEFAULT 0 NOT NULL');
    add_column_if_missing('INDECI_ASISTENCIA_IMPORTACION_FILA', 'REFRIGERIO_MIN',        'REFRIGERIO_MIN NUMBER(5) DEFAULT 0 NOT NULL');
    add_column_if_missing('INDECI_ASISTENCIA_IMPORTACION_FILA', 'EXCESO_REFRIG_MIN',     'EXCESO_REFRIG_MIN NUMBER(5) DEFAULT 0 NOT NULL');
    add_column_if_missing('INDECI_ASISTENCIA_IMPORTACION_FILA', 'TIEMPO_REFRIG_MIN',     'TIEMPO_REFRIG_MIN NUMBER(5) DEFAULT 0 NOT NULL');
    add_column_if_missing('INDECI_ASISTENCIA_IMPORTACION_FILA', 'TIEMPO_ANTES_SAL_MIN',  'TIEMPO_ANTES_SAL_MIN NUMBER(5) DEFAULT 0 NOT NULL');
    add_column_if_missing('INDECI_ASISTENCIA_IMPORTACION_FILA', 'HORAS_TRAB_MIN',        'HORAS_TRAB_MIN NUMBER(6) DEFAULT 0 NOT NULL');
    add_column_if_missing('INDECI_ASISTENCIA_IMPORTACION_FILA', 'HORAS_EXTRA_25_MIN',    'HORAS_EXTRA_25_MIN NUMBER(6) DEFAULT 0 NOT NULL');
    add_column_if_missing('INDECI_ASISTENCIA_IMPORTACION_FILA', 'HORAS_EXTRA_35_MIN',    'HORAS_EXTRA_35_MIN NUMBER(6) DEFAULT 0 NOT NULL');
    add_column_if_missing('INDECI_ASISTENCIA_IMPORTACION_FILA', 'HORAS_EXTRA_100_MIN',   'HORAS_EXTRA_100_MIN NUMBER(6) DEFAULT 0 NOT NULL');
    add_column_if_missing('INDECI_ASISTENCIA_IMPORTACION_FILA', 'HORAS_EXTRA_TOTAL_MIN', 'HORAS_EXTRA_TOTAL_MIN NUMBER(6) DEFAULT 0 NOT NULL');
    add_column_if_missing('INDECI_ASISTENCIA_IMPORTACION_FILA', 'MENSAJE_VALIDACION',    'MENSAJE_VALIDACION VARCHAR2(500 CHAR)');
    add_column_if_missing('INDECI_ASISTENCIA_IMPORTACION_FILA', 'ACEPTADA_OBSERVADA',    'ACEPTADA_OBSERVADA NUMBER(1) DEFAULT 0 NOT NULL');

    add_constraint_if_missing(
        'INDECI_ASISTENCIA_IMPORTACION_FILA',
        'INDECI_ASIST_IMP_FILA_MIN_CK',
        'CHECK (TARDANZA_MIN >= 0 AND REFRIGERIO_MIN >= 0 AND EXCESO_REFRIG_MIN >= 0 '
        || 'AND TIEMPO_REFRIG_MIN >= 0 AND TIEMPO_ANTES_SAL_MIN >= 0 AND HORAS_TRAB_MIN >= 0 '
        || 'AND HORAS_EXTRA_25_MIN >= 0 AND HORAS_EXTRA_35_MIN >= 0 AND HORAS_EXTRA_100_MIN >= 0 '
        || 'AND HORAS_EXTRA_TOTAL_MIN >= 0)');

    add_constraint_if_missing(
        'INDECI_ASISTENCIA_IMPORTACION_FILA',
        'INDECI_ASIST_IMP_FILA_ACEPT_CK',
        'CHECK (ACEPTADA_OBSERVADA IN (0, 1))');

    -- Índice para filtro + paginación server-side del detalle (req 11/12, P8).
    add_index_if_missing(
        'IX_INDECI_ASIST_IMP_FILA_FILTRO',
        'CREATE INDEX GESTIONRRHH.IX_INDECI_ASIST_IMP_FILA_FILTRO '
        || 'ON GESTIONRRHH.INDECI_ASISTENCIA_IMPORTACION_FILA (IMPORTACION_ID, ESTADO_FILA, DNI)'
    );

    -- ==================================================================
    -- 2) CABECERA IMPORT: auditoría de ciclo (validación / confirmación)
    -- ==================================================================
    add_column_if_missing('INDECI_ASISTENCIA_IMPORTACION', 'TAMANO_BYTES',          'TAMANO_BYTES NUMBER(12)');
    add_column_if_missing('INDECI_ASISTENCIA_IMPORTACION', 'EMPLEADOS_DETECTADOS',  'EMPLEADOS_DETECTADOS NUMBER(8) DEFAULT 0 NOT NULL');
    add_column_if_missing('INDECI_ASISTENCIA_IMPORTACION', 'PERIODO_DETECTADO_INI', 'PERIODO_DETECTADO_INI DATE');
    add_column_if_missing('INDECI_ASISTENCIA_IMPORTACION', 'PERIODO_DETECTADO_FIN', 'PERIODO_DETECTADO_FIN DATE');
    add_column_if_missing('INDECI_ASISTENCIA_IMPORTACION', 'USUARIO_VALIDACION',    'USUARIO_VALIDACION VARCHAR2(100 CHAR)');
    add_column_if_missing('INDECI_ASISTENCIA_IMPORTACION', 'FECHA_VALIDACION',      'FECHA_VALIDACION TIMESTAMP');
    add_column_if_missing('INDECI_ASISTENCIA_IMPORTACION', 'USUARIO_CONFIRMACION',  'USUARIO_CONFIRMACION VARCHAR2(100 CHAR)');
    add_column_if_missing('INDECI_ASISTENCIA_IMPORTACION', 'FECHA_CONFIRMACION',    'FECHA_CONFIRMACION TIMESTAMP');

    EXECUTE IMMEDIATE 'COMMENT ON COLUMN GESTIONRRHH.INDECI_ASISTENCIA_IMPORTACION_FILA.TARDANZA_MIN IS ''Tardanza en minutos (numerico) — derivado de TARDANZA_RAW HH:mm. (req 21/22).''';
    EXECUTE IMMEDIATE 'COMMENT ON COLUMN GESTIONRRHH.INDECI_ASISTENCIA_IMPORTACION_FILA.HORAS_EXTRA_TOTAL_MIN IS ''Horas extra reportadas por el reloj (min). NO genera concepto de planilla automatico (P2).''';
    EXECUTE IMMEDIATE 'COMMENT ON COLUMN GESTIONRRHH.INDECI_ASISTENCIA_IMPORTACION_FILA.REFRIGERIO_MIN IS ''Refrigerio en minutos. Informativo, no descuenta hasta regla RRHH (P7).''';
    EXECUTE IMMEDIATE 'COMMENT ON COLUMN GESTIONRRHH.INDECI_ASISTENCIA_IMPORTACION_FILA.ACEPTADA_OBSERVADA IS ''1 = fila OBSERVADA aceptada expresamente por RRHH para confirmar (P3).''';
    EXECUTE IMMEDIATE 'COMMENT ON COLUMN GESTIONRRHH.INDECI_ASISTENCIA_IMPORTACION.PERIODO_DETECTADO_INI IS ''Rango de fechas detectado en el CSV (min/max FECHA de filas).''';

    DBMS_OUTPUT.PUT_LINE('V010_80 finalizado.');

    SELECT COUNT(*) INTO v_count FROM ALL_TAB_COLUMNS
     WHERE OWNER = 'GESTIONRRHH' AND TABLE_NAME = 'INDECI_ASISTENCIA_IMPORTACION_FILA'
       AND COLUMN_NAME IN ('NOMBRE_CSV','DIA_SEMANA','ENTRADA_PROG','SALIDA_PROG','MARCA3','MARCA4',
            'TARDANZA_MIN','REFRIGERIO_MIN','EXCESO_REFRIG_MIN','TIEMPO_REFRIG_MIN','TIEMPO_ANTES_SAL_MIN',
            'HORAS_TRAB_MIN','HORAS_EXTRA_25_MIN','HORAS_EXTRA_35_MIN','HORAS_EXTRA_100_MIN','HORAS_EXTRA_TOTAL_MIN',
            'MENSAJE_VALIDACION','NOMBRE_SISTEMA','ACEPTADA_OBSERVADA');
    DBMS_OUTPUT.PUT_LINE('Columnas staging nuevas: ' || v_count || ' / 19.');

    SELECT COUNT(*) INTO v_count FROM ALL_TAB_COLUMNS
     WHERE OWNER = 'GESTIONRRHH' AND TABLE_NAME = 'INDECI_ASISTENCIA_IMPORTACION'
       AND COLUMN_NAME IN ('TAMANO_BYTES','EMPLEADOS_DETECTADOS','PERIODO_DETECTADO_INI','PERIODO_DETECTADO_FIN',
            'USUARIO_VALIDACION','FECHA_VALIDACION','USUARIO_CONFIRMACION','FECHA_CONFIRMACION');
    DBMS_OUTPUT.PUT_LINE('Columnas cabecera import nuevas: ' || v_count || ' / 8.');
END;
/
