-- ============================================================================
-- V010_50 — Columnas de catálogos demográficos/académicos en INDECI_EMPLEADO
--
-- PROBLEMA: la entidad Empleado.java mapea PROFESION_ID, GRADO_ACADEMICO_ID,
-- TIPO_PERSONAL_ID y CONADIS_CODIGO (Spec 009 / T134), pero ninguna migración
-- V010_* las agrega. En entornos cuya tabla INDECI_EMPLEADO se creó antes de
-- esos campos, cualquier SELECT de Empleado falla con:
--   ORA-00904: "E1_0"."PROFESION_ID": identificador no válido
-- (rompe persona, selección de empleado, conceptos, etc.).
--
-- SOLUCIÓN: agregar las columnas faltantes (nullable, additivas). Idempotente:
-- cada ALTER se ejecuta solo si la columna no existe. ALTER ADD de columna NO
-- requiere TABLESPACE. Ejecutar en GESTIONRRHH / Oracle 19c+.
-- ============================================================================

SET SERVEROUTPUT ON;

DECLARE
    PROCEDURE add_column_if_missing(p_col VARCHAR2, p_ddl VARCHAR2) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_exists
          FROM ALL_TAB_COLUMNS
         WHERE OWNER = 'GESTIONRRHH'
           AND TABLE_NAME = 'INDECI_EMPLEADO'
           AND COLUMN_NAME = p_col;
        IF v_exists = 0 THEN
            EXECUTE IMMEDIATE
                'ALTER TABLE GESTIONRRHH.INDECI_EMPLEADO ADD (' || p_ddl || ')';
            DBMS_OUTPUT.PUT_LINE(p_col || ' -> agregada.');
        ELSE
            DBMS_OUTPUT.PUT_LINE(p_col || ' ya existe. Sin cambios.');
        END IF;
    END;
BEGIN
    add_column_if_missing('PROFESION_ID',        'PROFESION_ID        NUMBER');
    add_column_if_missing('GRADO_ACADEMICO_ID',  'GRADO_ACADEMICO_ID  NUMBER');
    add_column_if_missing('TIPO_PERSONAL_ID',    'TIPO_PERSONAL_ID    NUMBER');
    add_column_if_missing('CONADIS_CODIGO',      'CONADIS_CODIGO      VARCHAR2(20 CHAR)');

    EXECUTE IMMEDIATE 'COMMENT ON COLUMN GESTIONRRHH.INDECI_EMPLEADO.PROFESION_ID IS '
        || '''FK lógica a INDECI_PROFESION.ID (Spec 009 / T134).''';
    EXECUTE IMMEDIATE 'COMMENT ON COLUMN GESTIONRRHH.INDECI_EMPLEADO.GRADO_ACADEMICO_ID IS '
        || '''FK lógica a INDECI_GRADO_ACADEMICO.ID (Spec 009 / T134).''';
    EXECUTE IMMEDIATE 'COMMENT ON COLUMN GESTIONRRHH.INDECI_EMPLEADO.TIPO_PERSONAL_ID IS '
        || '''FK lógica a INDECI_TIPO_PERSONAL.ID (Spec 009 / T134).''';
    EXECUTE IMMEDIATE 'COMMENT ON COLUMN GESTIONRRHH.INDECI_EMPLEADO.CONADIS_CODIGO IS '
        || '''Código de inscripción CONADIS (persona con discapacidad).''';

    DBMS_OUTPUT.PUT_LINE('V010_50 finalizado.');
END;
/
