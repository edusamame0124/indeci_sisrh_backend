-- ============================================================================
-- Spec 010 / V010_08 — Columna CODIGO_SISPER en INDECI_CONCEPTO_PLANILLA
--
-- Puente entre el catálogo MEF (CODIGO_MEF, ya obligatorio desde V010_06) y
-- los códigos SISPER del Excel operativo. Necesario para que V010_11 pueda
-- sembrar los 12 descuentos voluntarios SISPER (§6.4) y los conceptos
-- SERVIR (§6.3) sin perder su identificador en el sistema fuente.
--
-- DECISIONES DE DISEÑO:
--   - Nullable: no todo concepto MEF tiene equivalente SISPER (ej: aportes
--     internos, conceptos exclusivos régimen 276/728).
--   - Sin índice ni UNIQUE: el lookup por SISPER lo hacen seeds y pantallas
--     de mantenimiento; el motor de cálculo sigue usando CODIGO_MEF como llave.
--   - Sin CHECK constraint sobre formato: SISPER del Excel mezcla numéricos
--     ("905", "071") y futuros alfanuméricos. Validación queda a nivel app.
--
-- DEFENSA EN PROFUNDIDAD:
--   - Idempotente: si la columna ya existe (parche manual previo) salta el ALTER.
--   - Solo metadata: ninguna fila se modifica.
--   - Re-ejecución segura.
--
-- Ejecutar en esquema GESTIONRRHH / Oracle 19c+.
-- ============================================================================

SET SERVEROUTPUT ON;

DECLARE
    PROCEDURE add_column_if_missing(
        p_col_name  VARCHAR2,
        p_col_ddl   VARCHAR2
    ) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*)
          INTO v_exists
          FROM ALL_TAB_COLUMNS
         WHERE OWNER       = 'GESTIONRRHH'
           AND TABLE_NAME  = 'INDECI_CONCEPTO_PLANILLA'
           AND COLUMN_NAME = p_col_name;

        IF v_exists = 0 THEN
            EXECUTE IMMEDIATE
                'ALTER TABLE GESTIONRRHH.INDECI_CONCEPTO_PLANILLA ADD ('
                || p_col_ddl || ')';
            DBMS_OUTPUT.PUT_LINE(p_col_name || ' -> agregada.');
        ELSE
            DBMS_OUTPUT.PUT_LINE(p_col_name || ' ya existe. Sin cambios.');
        END IF;
    END;
BEGIN
    -- ------------------------------------------------------------------
    -- CODIGO_SISPER — identificador del concepto en SISPER (§6.3 / §6.4)
    -- ------------------------------------------------------------------
    add_column_if_missing(
        'CODIGO_SISPER',
        'CODIGO_SISPER VARCHAR2(10 CHAR)');

    -- ------------------------------------------------------------------
    -- COMMENT — idempotente (Oracle sobreescribe)
    -- ------------------------------------------------------------------
    EXECUTE IMMEDIATE
        'COMMENT ON COLUMN GESTIONRRHH.INDECI_CONCEPTO_PLANILLA.CODIGO_SISPER IS ' ||
        '''Código del concepto en sistema SISPER (Excel operativo). Ejemplos: ''''905'''' (ESSALUD sin EPS), ''''907'''' (ESSALUD con EPS 6.75%), ''''725'''' (copago EPS 2.25%), ''''071'''' (incremento DS 265-2024-EF), ''''820'''' (IR 5ta total). Puente entre CODIGO_MEF (MEF/AIRHSP) y los seeds de V010_11.''';

    DBMS_OUTPUT.PUT_LINE('V010_08 finalizado.');
END;
/
