-- ============================================================
-- V010_72 — Estado ANULADO + campos de anulación en vigencias previsionales
--
-- Propósito:
--   Habilitar la anulación lógica de vigencias AFP/ONP ("Eliminar" en UI).
--   NUNCA se borra físicamente un registro. El flujo es:
--     Eliminar (UI) → ESTADO = 'ANULADO' (BD)
--
-- Cambios:
--   1. Recrea CK de ESTADO en AFP y ONP incluyendo 'ANULADO'.
--   2. Agrega MOTIVO_ANULACION / ANULADO_POR / ANULADO_EN a ambas tablas.
--   3. Recrea CK de ACCION en INDECI_PREVISIONAL_LOG incluyendo 'ANULAR'.
--
-- Idempotente: usa verificación por nombre de constraint / columna antes de
-- cada ALTER para que re-ejecuciones no fallen.
-- ============================================================

SET SERVEROUTPUT ON;

DECLARE
    v_count NUMBER;

    PROCEDURE drop_constraint_si_existe(p_tabla VARCHAR2, p_constraint VARCHAR2) IS
    BEGIN
        SELECT COUNT(*) INTO v_count
          FROM USER_CONSTRAINTS
         WHERE TABLE_NAME      = UPPER(p_tabla)
           AND CONSTRAINT_NAME = UPPER(p_constraint);
        IF v_count > 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE GESTIONRRHH.' || p_tabla
                              || ' DROP CONSTRAINT ' || p_constraint;
            DBMS_OUTPUT.PUT_LINE('Constraint ' || p_constraint || ' eliminado.');
        ELSE
            DBMS_OUTPUT.PUT_LINE('Constraint ' || p_constraint || ' no existe. Sin accion.');
        END IF;
    END;

    PROCEDURE add_column_si_falta(p_tabla VARCHAR2, p_col VARCHAR2, p_ddl VARCHAR2) IS
    BEGIN
        SELECT COUNT(*) INTO v_count
          FROM USER_TAB_COLUMNS
         WHERE TABLE_NAME  = UPPER(p_tabla)
           AND COLUMN_NAME = UPPER(p_col);
        IF v_count = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE GESTIONRRHH.' || p_tabla || ' ADD ' || p_ddl;
            DBMS_OUTPUT.PUT_LINE(p_tabla || '.' || p_col || ' -> agregada.');
        ELSE
            DBMS_OUTPUT.PUT_LINE(p_tabla || '.' || p_col || ' ya existe. Sin accion.');
        END IF;
    END;

    PROCEDURE add_ck_si_falta(p_constraint VARCHAR2, p_tabla VARCHAR2, p_check_expr VARCHAR2) IS
    BEGIN
        SELECT COUNT(*) INTO v_count
          FROM USER_CONSTRAINTS
         WHERE TABLE_NAME      = UPPER(p_tabla)
           AND CONSTRAINT_NAME = UPPER(p_constraint);
        IF v_count = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE GESTIONRRHH.' || p_tabla
                              || ' ADD CONSTRAINT ' || p_constraint
                              || ' CHECK (' || p_check_expr || ')';
            DBMS_OUTPUT.PUT_LINE('Constraint ' || p_constraint || ' creado.');
        ELSE
            DBMS_OUTPUT.PUT_LINE('Constraint ' || p_constraint || ' ya existe. Sin accion.');
        END IF;
    END;

BEGIN
    -- ── 1. AFP PARAMETRO VIGENCIA — CK ESTADO con ANULADO ────
    drop_constraint_si_existe('INDECI_AFP_PARAMETRO_VIGENCIA', 'INDECI_AFP_PV_ESTADO_CK');
    add_ck_si_falta(
        'INDECI_AFP_PV_ESTADO_CK',
        'INDECI_AFP_PARAMETRO_VIGENCIA',
        'ESTADO IN (''VIGENTE'',''PROGRAMADO'',''CERRADO'',''INACTIVO'',''ANULADO'')'
    );

    -- ── 2. AFP — campos de anulacion ─────────────────────────
    add_column_si_falta('INDECI_AFP_PARAMETRO_VIGENCIA', 'MOTIVO_ANULACION',
                        'MOTIVO_ANULACION VARCHAR2(1000)');
    add_column_si_falta('INDECI_AFP_PARAMETRO_VIGENCIA', 'ANULADO_POR',
                        'ANULADO_POR VARCHAR2(100)');
    add_column_si_falta('INDECI_AFP_PARAMETRO_VIGENCIA', 'ANULADO_EN',
                        'ANULADO_EN TIMESTAMP');

    -- ── 3. ONP PARAMETRO VIGENCIA — CK ESTADO con ANULADO ────
    drop_constraint_si_existe('INDECI_ONP_PARAMETRO_VIGENCIA', 'INDECI_ONP_PV_ESTADO_CK');
    add_ck_si_falta(
        'INDECI_ONP_PV_ESTADO_CK',
        'INDECI_ONP_PARAMETRO_VIGENCIA',
        'ESTADO IN (''VIGENTE'',''PROGRAMADO'',''CERRADO'',''INACTIVO'',''ANULADO'')'
    );

    -- ── 4. ONP — campos de anulacion ─────────────────────────
    add_column_si_falta('INDECI_ONP_PARAMETRO_VIGENCIA', 'MOTIVO_ANULACION',
                        'MOTIVO_ANULACION VARCHAR2(1000)');
    add_column_si_falta('INDECI_ONP_PARAMETRO_VIGENCIA', 'ANULADO_POR',
                        'ANULADO_POR VARCHAR2(100)');
    add_column_si_falta('INDECI_ONP_PARAMETRO_VIGENCIA', 'ANULADO_EN',
                        'ANULADO_EN TIMESTAMP');

    -- ── 5. LOG — CK ACCION con ANULAR ────────────────────────
    drop_constraint_si_existe('INDECI_PREVISIONAL_LOG', 'INDECI_PREV_LOG_ACCION_CK');
    add_ck_si_falta(
        'INDECI_PREV_LOG_ACCION_CK',
        'INDECI_PREVISIONAL_LOG',
        'ACCION IN (''CREAR'',''EDITAR'',''CERRAR'',''DUPLICAR'',''ANULAR'')'
    );

    COMMIT;
    DBMS_OUTPUT.PUT_LINE('V010_72 finalizado correctamente.');

EXCEPTION
    WHEN OTHERS THEN
        ROLLBACK;
        DBMS_OUTPUT.PUT_LINE('ERROR en V010_72: ' || SQLERRM);
        RAISE;
END;
/
