-- ============================================================================
-- F1.2-fix2 / V010_39 — Recrear INDECI_PARAM_REM_UK con TABLESPACE explícito
--
-- CONTEXTO:
--   V010_38 dropeó el UK viejo correctamente, pero el ADD CONSTRAINT nuevo
--   falló con ORA-00959 (tablespace 'TBS_RRHH' no existe). Causa: cuando
--   Oracle crea una UNIQUE constraint, crea implícitamente un índice único
--   asociado; sin TABLESPACE explícito ese índice intenta ir al default del
--   usuario, que está mal configurado. Mismo problema que vimos antes en
--   V010_35 con los CREATE INDEX (resuelto entonces leyendo el tablespace
--   de INDECI_EMPLEADO; aplicamos la misma estrategia aquí).
--
--   La tabla actualmente NO TIENE UK (peligro: doble inserción posible).
--   Este script reconstruye el UK.
--
-- ESTRATEGIA:
--   1) Si INDECI_PARAM_REM_UK ya existe como constraint → nada que hacer.
--   2) Si no, leer el tablespace de INDECI_EMPLEADO (ancla estable).
--   3) Limpiar índice huérfano previo (por si quedó de V010_38).
--   4) CREATE UNIQUE INDEX con TABLESPACE explícito.
--   5) ALTER TABLE ADD CONSTRAINT ... USING INDEX (asocia, no recrea índice).
--
-- IDEMPOTENTE: ejecutar varias veces deja el mismo estado.
-- ============================================================================

SET SERVEROUTPUT ON;

DECLARE
    v_constraint_exists NUMBER;
    v_ts                VARCHAR2(30);
BEGIN
    SELECT COUNT(*) INTO v_constraint_exists
      FROM USER_CONSTRAINTS
     WHERE CONSTRAINT_NAME = 'INDECI_PARAM_REM_UK';

    IF v_constraint_exists > 0 THEN
        DBMS_OUTPUT.PUT_LINE('INDECI_PARAM_REM_UK ya existe como constraint. Sin cambios.');
        RETURN;
    END IF;

    SELECT TABLESPACE_NAME INTO v_ts
      FROM USER_TABLES
     WHERE TABLE_NAME = 'INDECI_EMPLEADO';
    DBMS_OUTPUT.PUT_LINE('Tablespace ancla detectado: ' || v_ts);

    -- Limpiar índice huérfano previo (si V010_38 dejó algo a medias).
    BEGIN
        EXECUTE IMMEDIATE 'DROP INDEX INDECI_PARAM_REM_UK';
        DBMS_OUTPUT.PUT_LINE('Índice huérfano previo INDECI_PARAM_REM_UK -> eliminado.');
    EXCEPTION
        WHEN OTHERS THEN
            IF SQLCODE = -1418 THEN
                DBMS_OUTPUT.PUT_LINE('Sin índice huérfano (limpio).');
            ELSE
                RAISE;
            END IF;
    END;

    -- 1) Crear índice único con TABLESPACE explícito.
    EXECUTE IMMEDIATE
        'CREATE UNIQUE INDEX INDECI_PARAM_REM_UK '
        || 'ON INDECI_PARAMETRO_REMUNERATIVO '
        || '(CODIGO_PARAMETRO, ANIO_FISCAL, REGIMEN_LABORAL_ID, FECHA_VIG_INI) '
        || 'TABLESPACE ' || v_ts;
    DBMS_OUTPUT.PUT_LINE('Índice único INDECI_PARAM_REM_UK -> creado en TBS ' || v_ts);

    -- 2) Asociar el constraint UNIQUE al índice ya creado.
    --    Importante: USING INDEX <nombre> reutiliza el índice; NO crea otro.
    EXECUTE IMMEDIATE
        'ALTER TABLE INDECI_PARAMETRO_REMUNERATIVO '
        || 'ADD CONSTRAINT INDECI_PARAM_REM_UK '
        || 'UNIQUE (CODIGO_PARAMETRO, ANIO_FISCAL, REGIMEN_LABORAL_ID, FECHA_VIG_INI) '
        || 'USING INDEX INDECI_PARAM_REM_UK';
    DBMS_OUTPUT.PUT_LINE('Constraint INDECI_PARAM_REM_UK -> creado.');
END;
/

-- ----------------------------------------------------------------------------
-- Verificación: el UK debe aparecer en USER_CONSTRAINTS con sus 4 columnas
-- y el índice asociado debe estar en TBS distinto a TBS_RRHH.
-- ----------------------------------------------------------------------------
DECLARE
    v_uk_cols VARCHAR2(500);
    v_ix_ts   VARCHAR2(30);
BEGIN
    SELECT LISTAGG(COLUMN_NAME, ',') WITHIN GROUP (ORDER BY POSITION)
      INTO v_uk_cols
      FROM USER_CONS_COLUMNS
     WHERE CONSTRAINT_NAME = 'INDECI_PARAM_REM_UK';

    SELECT TABLESPACE_NAME INTO v_ix_ts
      FROM USER_INDEXES
     WHERE INDEX_NAME = 'INDECI_PARAM_REM_UK';

    DBMS_OUTPUT.PUT_LINE('---');
    DBMS_OUTPUT.PUT_LINE('UK columnas:   ' || NVL(v_uk_cols, '<<NO EXISTE>>'));
    DBMS_OUTPUT.PUT_LINE('Índice TBS:    ' || NVL(v_ix_ts, '<<NO EXISTE>>'));
    DBMS_OUTPUT.PUT_LINE('V010_39 listo.');
EXCEPTION
    WHEN NO_DATA_FOUND THEN
        DBMS_OUTPUT.PUT_LINE('Verificación: alguna pieza falta. Revisar USER_CONSTRAINTS / USER_INDEXES.');
END;
/
