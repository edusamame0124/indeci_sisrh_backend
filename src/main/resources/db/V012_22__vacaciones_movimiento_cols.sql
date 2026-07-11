-- ============================================================================
-- SPEC_VACACIONES D4 — Columnas de movimiento relacional en INDECI_VACACIONES.
--
-- Reubicada desde db/migration/ y DECONFLICTADA: antes estaba como V012_21, que
-- COLISIONABA con V012_21__vacacion_saldo_unique.sql (dos migraciones misma versión
-- en carpetas distintas). Ahora V012_22, en la carpeta canónica db/.
--
-- Hace relacional el historial de goces (D4): la "col X" del Excel se deriva de estas
-- filas, sin texto libre. Las columnas casan 1:1 con la entidad Vacacion.
--
-- Idempotente (add_column_if_missing) — coherente con V012_20. Oracle 19c+ / GESTIONRRHH.
-- ============================================================================

SET SERVEROUTPUT ON;

DECLARE
    PROCEDURE add_column_if_missing(
        p_table  VARCHAR2,
        p_column VARCHAR2,
        p_ddl    VARCHAR2
    ) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_exists
          FROM ALL_TAB_COLUMNS
         WHERE OWNER = 'GESTIONRRHH'
           AND TABLE_NAME = p_table
           AND COLUMN_NAME = p_column;
        IF v_exists = 0 THEN
            EXECUTE IMMEDIATE p_ddl;
            DBMS_OUTPUT.PUT_LINE(p_table || '.' || p_column || ' -> agregada.');
        ELSE
            DBMS_OUTPUT.PUT_LINE(p_table || '.' || p_column || ' ya existe. Sin cambios.');
        END IF;
    END;
BEGIN
    add_column_if_missing('INDECI_VACACIONES', 'ANIO_PERIODO',
        'ALTER TABLE GESTIONRRHH.INDECI_VACACIONES ADD (ANIO_PERIODO NUMBER(4))');
    add_column_if_missing('INDECI_VACACIONES', 'TIPO_GOCE',
        'ALTER TABLE GESTIONRRHH.INDECI_VACACIONES ADD (TIPO_GOCE VARCHAR2(20 CHAR))');
    add_column_if_missing('INDECI_VACACIONES', 'DIAS',
        'ALTER TABLE GESTIONRRHH.INDECI_VACACIONES ADD (DIAS NUMBER(4))');
    add_column_if_missing('INDECI_VACACIONES', 'ESTADO',
        'ALTER TABLE GESTIONRRHH.INDECI_VACACIONES ADD (ESTADO VARCHAR2(15 CHAR))');
    add_column_if_missing('INDECI_VACACIONES', 'ES_ADELANTO',
        'ALTER TABLE GESTIONRRHH.INDECI_VACACIONES ADD (ES_ADELANTO NUMBER(1))');
    add_column_if_missing('INDECI_VACACIONES', 'DOCUMENTO_SUSTENTO',
        'ALTER TABLE GESTIONRRHH.INDECI_VACACIONES ADD (DOCUMENTO_SUSTENTO VARCHAR2(200 CHAR))');
    add_column_if_missing('INDECI_VACACIONES', 'MOTIVO_EXCEPCION',
        'ALTER TABLE GESTIONRRHH.INDECI_VACACIONES ADD (MOTIVO_EXCEPCION VARCHAR2(200 CHAR))');
    add_column_if_missing('INDECI_VACACIONES', 'FECHA_REINCORPORACION',
        'ALTER TABLE GESTIONRRHH.INDECI_VACACIONES ADD (FECHA_REINCORPORACION DATE)');
    add_column_if_missing('INDECI_VACACIONES', 'ORIGEN',
        'ALTER TABLE GESTIONRRHH.INDECI_VACACIONES ADD (ORIGEN VARCHAR2(30 CHAR))');
    add_column_if_missing('INDECI_VACACIONES', 'SOLICITUD_RRHH_ID',
        'ALTER TABLE GESTIONRRHH.INDECI_VACACIONES ADD (SOLICITUD_RRHH_ID NUMBER)');

    EXECUTE IMMEDIATE 'COMMENT ON COLUMN GESTIONRRHH.INDECI_VACACIONES.TIPO_GOCE IS '
        || '''REGULAR|ADELANTO|FRACCION|POOL_MEDIA_JORNADA''';
    EXECUTE IMMEDIATE 'COMMENT ON COLUMN GESTIONRRHH.INDECI_VACACIONES.ESTADO IS '
        || '''SOLICITADO|APROBADO|GOZADO|ANULADO''';

    DBMS_OUTPUT.PUT_LINE('V012_22 finalizado.');
END;
/
