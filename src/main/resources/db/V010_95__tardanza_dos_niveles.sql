-- ============================================================================
-- V010_95 — Descuento de tardanzas de dos niveles (Descuento 1 / Descuento 2)
--
-- CONTEXTO (mejora asistencia — modelo Excel institucional INDECI):
--   * DESCUENTO 1 (diario): día con tardanza > umbral diario → se descuenta el
--     total de minutos de ese día.
--   * DESCUENTO 2 (mensual): días con tardanza ≤ umbral se acumulan; si la suma
--     supera el tope mensual, se descuenta el exceso.
--
--   1) Config en INDECI_JORNADA_REGIMEN: umbral diario (10) + tope mensual (60).
--   2) Agregados en INDECI_ASISTENCIA_CABECERA para reflejar D1/D2 en pantalla.
--      DESCUENTO_TARDANZA (existente) sigue siendo el TOTAL (D1+D2): el motor de
--      planilla lo lee sin cambios.
--
-- ADITIVO e IDEMPOTENTE. Ejecutar conectado como GESTIONRRHH / Oracle 19c+.
-- Rollback: V010_95__tardanza_dos_niveles_rollback.sql
-- ============================================================================

SET SERVEROUTPUT ON;

DECLARE
    PROCEDURE add_column_if_missing(
        p_table    VARCHAR2,
        p_col_name VARCHAR2,
        p_col_ddl  VARCHAR2
    ) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*)
          INTO v_exists
          FROM ALL_TAB_COLUMNS
         WHERE OWNER       = 'GESTIONRRHH'
           AND TABLE_NAME  = p_table
           AND COLUMN_NAME = p_col_name;

        IF v_exists = 0 THEN
            EXECUTE IMMEDIATE
                'ALTER TABLE GESTIONRRHH.' || p_table || ' ADD (' || p_col_ddl || ')';
            DBMS_OUTPUT.PUT_LINE(p_table || '.' || p_col_name || ' -> agregada.');
        ELSE
            DBMS_OUTPUT.PUT_LINE(p_table || '.' || p_col_name || ' ya existe. Sin cambios.');
        END IF;
    END;
BEGIN
    -- 1) Config de reglas de descuento por tardanza (por régimen).
    add_column_if_missing('INDECI_JORNADA_REGIMEN',
        'UMBRAL_TARDANZA_DIARIA_MIN', 'UMBRAL_TARDANZA_DIARIA_MIN NUMBER(4) DEFAULT 10 NOT NULL');
    add_column_if_missing('INDECI_JORNADA_REGIMEN',
        'TOPE_TARDANZA_MENSUAL_MIN', 'TOPE_TARDANZA_MENSUAL_MIN  NUMBER(5) DEFAULT 60 NOT NULL');

    -- 2) Agregados de la cabecera (reflejo D1/D2). DESCUENTO_TARDANZA sigue = total.
    add_column_if_missing('INDECI_ASISTENCIA_CABECERA',
        'MIN_TARDANZA_DIARIA',        'MIN_TARDANZA_DIARIA        NUMBER(7)     DEFAULT 0 NOT NULL');
    add_column_if_missing('INDECI_ASISTENCIA_CABECERA',
        'MIN_TARDANZA_MENOR_ACUM',    'MIN_TARDANZA_MENOR_ACUM    NUMBER(7)     DEFAULT 0 NOT NULL');
    add_column_if_missing('INDECI_ASISTENCIA_CABECERA',
        'MIN_TARDANZA_EXCESO_MES',    'MIN_TARDANZA_EXCESO_MES    NUMBER(7)     DEFAULT 0 NOT NULL');
    add_column_if_missing('INDECI_ASISTENCIA_CABECERA',
        'DESCUENTO_TARDANZA_DIARIA',  'DESCUENTO_TARDANZA_DIARIA  NUMBER(14,2)  DEFAULT 0 NOT NULL');
    add_column_if_missing('INDECI_ASISTENCIA_CABECERA',
        'DESCUENTO_TARDANZA_MENSUAL', 'DESCUENTO_TARDANZA_MENSUAL NUMBER(14,2)  DEFAULT 0 NOT NULL');

    -- Comentarios.
    EXECUTE IMMEDIATE
        'COMMENT ON COLUMN GESTIONRRHH.INDECI_JORNADA_REGIMEN.UMBRAL_TARDANZA_DIARIA_MIN IS '
        || '''Minutos: día con tardanza > este umbral se descuenta completo (Descuento 1).''';
    EXECUTE IMMEDIATE
        'COMMENT ON COLUMN GESTIONRRHH.INDECI_JORNADA_REGIMEN.TOPE_TARDANZA_MENSUAL_MIN IS '
        || '''Minutos: tope mensual de tardanzas <= umbral; el exceso se descuenta (Descuento 2).''';

    DBMS_OUTPUT.PUT_LINE('V010_95 finalizado correctamente.');
EXCEPTION
    WHEN OTHERS THEN
        DBMS_OUTPUT.PUT_LINE('ERROR V010_95: ' || SQLERRM);
        RAISE;
END;
/

-- Verificación
SELECT REGIMEN_LABORAL_ID, HORA_INGRESO, TOLERANCIA_INGRESO_MIN,
       UMBRAL_TARDANZA_DIARIA_MIN, TOPE_TARDANZA_MENSUAL_MIN
  FROM GESTIONRRHH.INDECI_JORNADA_REGIMEN
 ORDER BY REGIMEN_LABORAL_ID;
