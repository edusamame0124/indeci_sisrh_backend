-- ============================================================================
-- F1.2-fix / V010_38 — Ampliar UK INDECI_PARAM_REM_UK para vigencia mensual
--
-- CONTEXTO:
--   V010_37 falló al final con ORA-00001 al intentar insertar la segunda fila
--   de TOPE_SEGURO_AFP (vigencia mayo 2026). Causa: el UK actual es
--   (CODIGO_PARAMETRO, ANIO_FISCAL, REGIMEN_LABORAL_ID) y NO incluye
--   FECHA_VIG_INI, así que sólo permite una fila por año por código.
--
--   Decisión C4 RRHH exige parametrizar el tope prima AFP por período de
--   devengue. Eso requiere múltiples filas por año, una por vigencia. Hay
--   que ampliar el UK a (CODIGO_PARAMETRO, ANIO_FISCAL, REGIMEN_LABORAL_ID,
--   FECHA_VIG_INI).
--
-- ESTRATEGIA:
--   1) Leer la definición actual del UK (USER_CONS_COLUMNS).
--   2) Si ya incluye FECHA_VIG_INI → nada que hacer (idempotente).
--   3) Si no, DROP del constraint + DROP del índice asociado si quedó huérfano
--      + ADD del constraint nuevo.
--   4) Insertar la fila TOPE_SEGURO_AFP mayo 2026 que V010_37 no pudo meter.
--      INSERT con NOT EXISTS por idempotencia.
--
-- NOTA: no se hardcodea schema. Lookups con USER_*.
-- IDEMPOTENTE: ejecutar varias veces deja el mismo estado.
-- ============================================================================

SET SERVEROUTPUT ON;

-- ----------------------------------------------------------------------------
-- 1) Migrar el UK si falta FECHA_VIG_INI.
-- ----------------------------------------------------------------------------
DECLARE
    v_uk_cols VARCHAR2(500);
BEGIN
    -- Concateno las columnas del UK actual.
    SELECT LISTAGG(COLUMN_NAME, ',') WITHIN GROUP (ORDER BY POSITION)
      INTO v_uk_cols
      FROM USER_CONS_COLUMNS
     WHERE CONSTRAINT_NAME = 'INDECI_PARAM_REM_UK';

    IF v_uk_cols IS NULL THEN
        DBMS_OUTPUT.PUT_LINE('INDECI_PARAM_REM_UK no existe. Sin cambios (caso raro).');
    ELSIF INSTR(UPPER(v_uk_cols), 'FECHA_VIG_INI') > 0 THEN
        DBMS_OUTPUT.PUT_LINE('INDECI_PARAM_REM_UK ya incluye FECHA_VIG_INI: ' || v_uk_cols);
    ELSE
        DBMS_OUTPUT.PUT_LINE('UK actual: ' || v_uk_cols || ' -> migrar.');

        EXECUTE IMMEDIATE
            'ALTER TABLE INDECI_PARAMETRO_REMUNERATIVO '
            || 'DROP CONSTRAINT INDECI_PARAM_REM_UK';
        DBMS_OUTPUT.PUT_LINE('Constraint INDECI_PARAM_REM_UK dropeado.');

        -- Por si el índice asociado al UK quedó huérfano (cuando el UK se
        -- crea sobre un índice preexistente, DROP CONSTRAINT no elimina el
        -- índice). Lo intentamos limpiar; ignoramos error si no aplica.
        BEGIN
            EXECUTE IMMEDIATE 'DROP INDEX INDECI_PARAM_REM_UK';
            DBMS_OUTPUT.PUT_LINE('Índice INDECI_PARAM_REM_UK huérfano -> eliminado.');
        EXCEPTION
            WHEN OTHERS THEN
                -- ORA-01418: índice no existe → bien, no quedó huérfano.
                IF SQLCODE = -1418 THEN
                    DBMS_OUTPUT.PUT_LINE('Sin índice huérfano (limpio).');
                ELSE
                    RAISE;
                END IF;
        END;

        EXECUTE IMMEDIATE
            'ALTER TABLE INDECI_PARAMETRO_REMUNERATIVO '
            || 'ADD CONSTRAINT INDECI_PARAM_REM_UK '
            || 'UNIQUE (CODIGO_PARAMETRO, ANIO_FISCAL, REGIMEN_LABORAL_ID, FECHA_VIG_INI)';
        DBMS_OUTPUT.PUT_LINE('UK recreado con FECHA_VIG_INI.');
    END IF;
END;
/

-- ----------------------------------------------------------------------------
-- 2) Reintentar el INSERT que V010_37 no pudo meter (TOPE_SEGURO_AFP mayo 2026).
--    Ahora el UK admite múltiples filas por código+año con distinta vigencia.
-- ----------------------------------------------------------------------------
INSERT INTO INDECI_PARAMETRO_REMUNERATIVO
       (CODIGO_PARAMETRO, ANIO_FISCAL, REGIMEN_LABORAL_ID, VALOR_NUMERICO, UNIDAD, FECHA_VIG_INI, FECHA_VIG_FIN, ACTIVO, CREATED_AT)
SELECT 'TOPE_SEGURO_AFP', 2026, NULL, 12598.91, 'PEN', DATE '2026-05-01', NULL, 1, SYSTIMESTAMP
  FROM DUAL
 WHERE NOT EXISTS (
        SELECT 1 FROM INDECI_PARAMETRO_REMUNERATIVO
         WHERE CODIGO_PARAMETRO   = 'TOPE_SEGURO_AFP'
           AND ANIO_FISCAL        = 2026
           AND REGIMEN_LABORAL_ID IS NULL
           AND FECHA_VIG_INI      = DATE '2026-05-01'
       );

COMMIT;

-- ----------------------------------------------------------------------------
-- 3) Verificación: imprime estado final de los parámetros del Motor v3 y de
--    la definición actual del UK.
-- ----------------------------------------------------------------------------
SET SERVEROUTPUT ON;
DECLARE
    v_uk_cols VARCHAR2(500);
BEGIN
    SELECT LISTAGG(COLUMN_NAME, ',') WITHIN GROUP (ORDER BY POSITION)
      INTO v_uk_cols
      FROM USER_CONS_COLUMNS
     WHERE CONSTRAINT_NAME = 'INDECI_PARAM_REM_UK';
    DBMS_OUTPUT.PUT_LINE('UK definitivo INDECI_PARAM_REM_UK: ' || v_uk_cols);
    DBMS_OUTPUT.PUT_LINE('---');

    FOR r IN (
        SELECT CODIGO_PARAMETRO, VALOR_NUMERICO, UNIDAD, FECHA_VIG_INI, FECHA_VIG_FIN, ACTIVO
          FROM INDECI_PARAMETRO_REMUNERATIVO
         WHERE CODIGO_PARAMETRO IN (
                'RMV', 'ASIG_FAMILIAR',
                'TOPE_ESSALUD_PCT_UIT',
                'TASA_IR4TA', 'BASE_INAFECTA_IR4TA', 'DEDUCCION_IR4TA',
                'TOPE_SEGURO_AFP'
               )
           AND ANIO_FISCAL = 2026
         ORDER BY CODIGO_PARAMETRO, FECHA_VIG_INI
    ) LOOP
        DBMS_OUTPUT.PUT_LINE(
            RPAD(r.CODIGO_PARAMETRO, 25) ||
            ' | ' || RPAD(TO_CHAR(r.VALOR_NUMERICO, 'FM999999990.000000'), 18) ||
            ' | ' || RPAD(NVL(r.UNIDAD, '-'), 4) ||
            ' | ' || TO_CHAR(r.FECHA_VIG_INI, 'YYYY-MM-DD') ||
            ' -> ' || NVL(TO_CHAR(r.FECHA_VIG_FIN, 'YYYY-MM-DD'), '   abierto ') ||
            ' | ACTIVO=' || r.ACTIVO
        );
    END LOOP;
    DBMS_OUTPUT.PUT_LINE('V010_38 listo.');
END;
/
