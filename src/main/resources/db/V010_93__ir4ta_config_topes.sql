-- ============================================================================
-- V010_93 — Tope anual de suspensión IR4ta: columnas en INDECI_IR4TA_CONFIG_ANUAL
--
-- CONTEXTO (plan ir4ta-tope-anual / pendiente B2):
--   La config anual (V010_76) ya define UIT, tasa y base inafecta por año fiscal.
--   Aquí se agregan los LÍMITES DE SUSPENSIÓN y las reglas de alerta, como
--   columnas en la MISMA fila anual (no se duplica la fila por tipo de tope:
--   eso rompería GeneradorPlanillaService.resolverPorAnio, que espera una sola
--   config/año para UIT/tasa/base inafecta).
--
--   El tipo de tope aplicable a cada trabajador es un FLAG MANUAL que vive en
--   INDECI_IR4TA_CONTROL_ANUAL (V010_94), no aquí.
--
-- REGLA-02: los montos (48125 / 38500) son parámetros, NO se hardcodean en
--   código Java/TS; el motor y la UI los leen de esta tabla.
--
-- IDEMPOTENTE: columnas, constraints y backfill defensivos.
-- Ejecutar conectado como GESTIONRRHH / Oracle 19c+.
-- Rollback: V010_93__ir4ta_config_topes_rollback.sql
-- ============================================================================

SET SERVEROUTPUT ON;

DECLARE
    v_rows_2026 NUMBER;

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
           AND TABLE_NAME  = 'INDECI_IR4TA_CONFIG_ANUAL'
           AND COLUMN_NAME = p_col_name;

        IF v_exists = 0 THEN
            EXECUTE IMMEDIATE
                'ALTER TABLE GESTIONRRHH.INDECI_IR4TA_CONFIG_ANUAL ADD ('
                || p_col_ddl || ')';
            DBMS_OUTPUT.PUT_LINE(p_col_name || ' -> agregada.');
        ELSE
            DBMS_OUTPUT.PUT_LINE(p_col_name || ' ya existe. Sin cambios.');
        END IF;
    END;

    PROCEDURE add_check_if_missing(
        p_constraint_name VARCHAR2,
        p_check_ddl       VARCHAR2
    ) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*)
          INTO v_exists
          FROM ALL_CONSTRAINTS
         WHERE OWNER           = 'GESTIONRRHH'
           AND TABLE_NAME      = 'INDECI_IR4TA_CONFIG_ANUAL'
           AND CONSTRAINT_NAME = p_constraint_name;

        IF v_exists = 0 THEN
            EXECUTE IMMEDIATE
                'ALTER TABLE GESTIONRRHH.INDECI_IR4TA_CONFIG_ANUAL ADD CONSTRAINT '
                || p_constraint_name || ' CHECK (' || p_check_ddl || ')';
            DBMS_OUTPUT.PUT_LINE(p_constraint_name || ' -> agregado.');
        ELSE
            DBMS_OUTPUT.PUT_LINE(p_constraint_name || ' ya existe. Sin cambios.');
        END IF;
    END;
BEGIN
    -- Topes de suspensión (nullable: años cerrados pueden no tenerlos).
    add_column_if_missing('TOPE_ANUAL_GENERAL',  'TOPE_ANUAL_GENERAL  NUMBER(12,2)');
    add_column_if_missing('TOPE_ANUAL_DIRECTOR', 'TOPE_ANUAL_DIRECTOR NUMBER(12,2)');

    -- ¿Cada tope aplica a CAS? (Wireframe A: GENERAL sí, DIRECTOR no por defecto).
    add_column_if_missing('APLICA_CAS_GENERAL',  'APLICA_CAS_GENERAL  NUMBER(1) DEFAULT 1 NOT NULL');
    add_column_if_missing('APLICA_CAS_DIRECTOR', 'APLICA_CAS_DIRECTOR NUMBER(1) DEFAULT 0 NOT NULL');

    -- Umbrales de alerta (porcentaje del tope).
    add_column_if_missing('PCT_ALERTA_PREV', 'PCT_ALERTA_PREV NUMBER(5,2) DEFAULT 80 NOT NULL');
    add_column_if_missing('PCT_ALERTA_CRIT', 'PCT_ALERTA_CRIT NUMBER(5,2) DEFAULT 90 NOT NULL');

    -- Código tributo SUNAT/PLAME (Wireframe A, sección 2). Referencial: 3042.
    add_column_if_missing('CODIGO_SUNAT_PLAME', 'CODIGO_SUNAT_PLAME VARCHAR2(10) DEFAULT ''3042'' NOT NULL');

    -- Reglas automáticas (Wireframe A, sección 4). RETENCION_AUTO = 0 por defecto:
    -- nunca retener sin validación de RR. HH.
    add_column_if_missing('FLG_CALC_ACUMULADO',    'FLG_CALC_ACUMULADO    NUMBER(1) DEFAULT 1 NOT NULL');
    add_column_if_missing('FLG_ALERTA_80',         'FLG_ALERTA_80         NUMBER(1) DEFAULT 1 NOT NULL');
    add_column_if_missing('FLG_ALERTA_90',         'FLG_ALERTA_90         NUMBER(1) DEFAULT 1 NOT NULL');
    add_column_if_missing('FLG_MARCAR_VALIDACION', 'FLG_MARCAR_VALIDACION NUMBER(1) DEFAULT 1 NOT NULL');
    add_column_if_missing('FLG_RETENCION_AUTO',    'FLG_RETENCION_AUTO    NUMBER(1) DEFAULT 0 NOT NULL');

    -- CHECKs.
    add_check_if_missing('INDECI_IR4TA_CFG_TOPEGEN_CK', 'TOPE_ANUAL_GENERAL  IS NULL OR TOPE_ANUAL_GENERAL  >= 0');
    add_check_if_missing('INDECI_IR4TA_CFG_TOPEDIR_CK', 'TOPE_ANUAL_DIRECTOR IS NULL OR TOPE_ANUAL_DIRECTOR >= 0');
    add_check_if_missing('INDECI_IR4TA_CFG_APLGEN_CK',  'APLICA_CAS_GENERAL  IN (0,1)');
    add_check_if_missing('INDECI_IR4TA_CFG_APLDIR_CK',  'APLICA_CAS_DIRECTOR IN (0,1)');
    add_check_if_missing('INDECI_IR4TA_CFG_PCTPREV_CK', 'PCT_ALERTA_PREV BETWEEN 0 AND 100');
    add_check_if_missing('INDECI_IR4TA_CFG_PCTCRIT_CK', 'PCT_ALERTA_CRIT BETWEEN 0 AND 100');
    add_check_if_missing('INDECI_IR4TA_CFG_FLGCALC_CK', 'FLG_CALC_ACUMULADO    IN (0,1)');
    add_check_if_missing('INDECI_IR4TA_CFG_FLGA80_CK',  'FLG_ALERTA_80         IN (0,1)');
    add_check_if_missing('INDECI_IR4TA_CFG_FLGA90_CK',  'FLG_ALERTA_90         IN (0,1)');
    add_check_if_missing('INDECI_IR4TA_CFG_FLGVAL_CK',  'FLG_MARCAR_VALIDACION IN (0,1)');
    add_check_if_missing('INDECI_IR4TA_CFG_FLGAUTO_CK', 'FLG_RETENCION_AUTO    IN (0,1)');

    -- Seed de topes 2026 (D.S. parametrizado): GENERAL_CAS=48125, DIRECTOR=38500.
    -- Solo se setea si están NULL (no pisa valores ya ajustados por RR. HH.).
    -- DINÁMICO: las columnas se agregan arriba vía EXECUTE IMMEDIATE; un UPDATE
    -- estático aquí no compilaría (ORA-00904) porque el bloque PL/SQL se compila
    -- antes de ejecutarse, cuando las columnas aún no existen.
    EXECUTE IMMEDIATE
        'UPDATE GESTIONRRHH.INDECI_IR4TA_CONFIG_ANUAL '
        || '   SET TOPE_ANUAL_GENERAL = 48125.00, '
        || '       TOPE_ANUAL_DIRECTOR = 38500.00 '
        || ' WHERE ANIO_FISCAL = 2026 '
        || '   AND (TOPE_ANUAL_GENERAL IS NULL OR TOPE_ANUAL_DIRECTOR IS NULL)';
    v_rows_2026 := SQL%ROWCOUNT;
    DBMS_OUTPUT.PUT_LINE('Seed topes 2026: ' || v_rows_2026 || ' fila(s) actualizada(s).');

    COMMIT;
    DBMS_OUTPUT.PUT_LINE('V010_93 finalizado correctamente.');
EXCEPTION
    WHEN OTHERS THEN
        ROLLBACK;
        DBMS_OUTPUT.PUT_LINE('ERROR V010_93: ' || SQLERRM);
        RAISE;
END;
/

-- Verificación
SELECT ANIO_FISCAL, ESTADO,
       TOPE_ANUAL_GENERAL, TOPE_ANUAL_DIRECTOR,
       APLICA_CAS_GENERAL, APLICA_CAS_DIRECTOR,
       PCT_ALERTA_PREV, PCT_ALERTA_CRIT,
       CODIGO_SUNAT_PLAME, FLG_RETENCION_AUTO
  FROM GESTIONRRHH.INDECI_IR4TA_CONFIG_ANUAL
 ORDER BY ANIO_FISCAL;
