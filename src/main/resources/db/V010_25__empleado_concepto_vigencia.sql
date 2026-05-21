-- ============================================================================
-- Spec 013 / C1 / V010_25 — Vigencia de la asignación de conceptos por empleado
--
-- El modal "Asignar Descuento / Ajuste Manual" exige un período de vigencia:
-- desde cuándo aplica el descuento y, opcionalmente, hasta cuándo. Sin fecha
-- fin = vigencia indefinida.
--
-- COLUMNAS (INDECI_EMPLEADO_CONCEPTO):
--   FECHA_INICIO  DATE  → mes/año desde el que aplica el concepto.
--                         Obligatorio para registros NUEVOS (lo valida el
--                         frontend); nullable en BD para no romper filas legacy.
--   FECHA_FIN     DATE  → mes/año hasta el que aplica. NULL = indefinido.
--
-- BACKFILL: las filas existentes toman FECHA_INICIO = TRUNC(CREATED_AT) — la
-- fecha en que se asignó el concepto — para que queden con vigencia coherente.
--
-- DEFENSA EN PROFUNDIDAD: idempotente (add_column_if_missing); el backfill solo
-- toca filas con FECHA_INICIO IS NULL. Ejecutar en GESTIONRRHH / Oracle 19c+.
-- ============================================================================

SET SERVEROUTPUT ON;

DECLARE
    v_filas NUMBER;

    PROCEDURE add_column_if_missing(
        p_col_name VARCHAR2,
        p_col_ddl  VARCHAR2
    ) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*)
          INTO v_exists
          FROM ALL_TAB_COLUMNS
         WHERE OWNER       = 'GESTIONRRHH'
           AND TABLE_NAME  = 'INDECI_EMPLEADO_CONCEPTO'
           AND COLUMN_NAME = p_col_name;

        IF v_exists = 0 THEN
            EXECUTE IMMEDIATE
                'ALTER TABLE GESTIONRRHH.INDECI_EMPLEADO_CONCEPTO ADD ('
                || p_col_ddl || ')';
            DBMS_OUTPUT.PUT_LINE(p_col_name || ' -> agregada.');
        ELSE
            DBMS_OUTPUT.PUT_LINE(p_col_name || ' ya existe. Sin cambios.');
        END IF;
    END;
BEGIN
    add_column_if_missing('FECHA_INICIO', 'FECHA_INICIO DATE');
    add_column_if_missing('FECHA_FIN',    'FECHA_FIN DATE');

    EXECUTE IMMEDIATE
        'COMMENT ON COLUMN GESTIONRRHH.INDECI_EMPLEADO_CONCEPTO.FECHA_INICIO IS ' ||
        '''Spec 013/C1 — Mes/año desde el que aplica el concepto al empleado.''';
    EXECUTE IMMEDIATE
        'COMMENT ON COLUMN GESTIONRRHH.INDECI_EMPLEADO_CONCEPTO.FECHA_FIN IS ' ||
        '''Spec 013/C1 — Mes/año hasta el que aplica. NULL = vigencia indefinida.''';

    -- Backfill: filas legacy sin vigencia → fecha de creación del registro.
    -- DEBE ser SQL dinámico: todo el bloque PL/SQL se compila ANTES de ejecutar,
    -- y la columna FECHA_INICIO recién la crea el ALTER de arriba. Una sentencia
    -- UPDATE estática fallaría con ORA-00904 en compilación.
    EXECUTE IMMEDIATE
        'UPDATE GESTIONRRHH.INDECI_EMPLEADO_CONCEPTO '
        || '   SET FECHA_INICIO = TRUNC(CREATED_AT) '
        || ' WHERE FECHA_INICIO IS NULL '
        || '   AND CREATED_AT IS NOT NULL';
    v_filas := SQL%ROWCOUNT;
    COMMIT;

    DBMS_OUTPUT.PUT_LINE('Backfill FECHA_INICIO desde CREATED_AT: ' || v_filas || ' filas.');
    DBMS_OUTPUT.PUT_LINE('V010_25 finalizado.');
END;
/
