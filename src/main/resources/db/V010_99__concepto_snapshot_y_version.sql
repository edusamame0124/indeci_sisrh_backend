-- ============================================================================
-- SPEC_CONCEPTOS_PLANILLA §12 / P3 / V010_99 — Snapshot histórico + versionado
--
-- OBJETIVO (D5): proteger la trazabilidad de planillas cerradas sin tocar el
-- cálculo. Dos mecanismos, ambos SOLO ADITIVOS:
--   1. SNAPSHOT — el detalle de movimiento congela nombre/código/tipo del concepto
--      al grabarse, para que renombrar/reconfigurar el concepto NO altere la boleta
--      histórica. Columnas nullable → filas viejas hacen fallback al nombre vivo.
--   2. VERSIONADO — INDECI_CONCEPTO_PLANILLA gana VERSION (n.º de versión por
--      CÓDIGO). Permite emitir una nueva configuración vigente hacia adelante sin
--      editar la usada en planilla cerrada (prohibición §8/D5).
--
-- DECISIONES DE DISEÑO:
--   - El motor NO cambia: grabarDetalle setea el snapshot, nada más.
--   - VERSION DEFAULT 1 NOT NULL → conceptos existentes quedan en versión 1
--     (sin backfill adicional).
--   - Invariante "1 fila ACTIVO por CÓDIGO" lo mantiene la aplicación (activar()
--     supersede a la versión previa); este script solo añade la columna VERSION.
--   - Naming INDECI_*; sin SEQUENCE (VERSION lo calcula la app: max(codigo)+1).
--
-- DEFENSA EN PROFUNDIDAD: idempotente + tablespace-safe (patrón V010_97/85/86).
-- Ejecutar en esquema GESTIONRRHH / Oracle 19c+.
-- ============================================================================

SET SERVEROUTPUT ON;

DECLARE
    v_count NUMBER;

    PROCEDURE add_column_if_missing(
        p_table_name  VARCHAR2,
        p_column_name VARCHAR2,
        p_alter_ddl   VARCHAR2
    ) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_exists
          FROM ALL_TAB_COLUMNS
         WHERE OWNER = 'GESTIONRRHH'
           AND TABLE_NAME = p_table_name
           AND COLUMN_NAME = p_column_name;
        IF v_exists = 0 THEN
            EXECUTE IMMEDIATE p_alter_ddl;
            DBMS_OUTPUT.PUT_LINE(p_table_name || '.' || p_column_name || ' -> añadida.');
        ELSE
            DBMS_OUTPUT.PUT_LINE(p_table_name || '.' || p_column_name
                || ' ya existe. Sin cambios.');
        END IF;
    END;
BEGIN
    -- ==================================================================
    -- 1) SNAPSHOT en INDECI_MOVIMIENTO_PLANILLA_DET (nullable → fallback vivo)
    -- ==================================================================
    add_column_if_missing(
        'INDECI_MOVIMIENTO_PLANILLA_DET', 'CONCEPTO_CODIGO',
        'ALTER TABLE GESTIONRRHH.INDECI_MOVIMIENTO_PLANILLA_DET '
        || 'ADD (CONCEPTO_CODIGO VARCHAR2(40 CHAR))'
    );
    add_column_if_missing(
        'INDECI_MOVIMIENTO_PLANILLA_DET', 'CONCEPTO_NOMBRE',
        'ALTER TABLE GESTIONRRHH.INDECI_MOVIMIENTO_PLANILLA_DET '
        || 'ADD (CONCEPTO_NOMBRE VARCHAR2(200 CHAR))'
    );
    add_column_if_missing(
        'INDECI_MOVIMIENTO_PLANILLA_DET', 'CONCEPTO_TIPO',
        'ALTER TABLE GESTIONRRHH.INDECI_MOVIMIENTO_PLANILLA_DET '
        || 'ADD (CONCEPTO_TIPO VARCHAR2(30 CHAR))'
    );

    -- ==================================================================
    -- 2) VERSION en INDECI_CONCEPTO_PLANILLA (n.º de versión por CÓDIGO)
    --    DEFAULT 1 NOT NULL → backfill implícito a versión 1 para lo existente.
    -- ==================================================================
    add_column_if_missing(
        'INDECI_CONCEPTO_PLANILLA', 'VERSION',
        'ALTER TABLE GESTIONRRHH.INDECI_CONCEPTO_PLANILLA '
        || 'ADD (VERSION NUMBER(4) DEFAULT 1 NOT NULL)'
    );

    -- ==================================================================
    -- COMMENTS
    -- ==================================================================
    EXECUTE IMMEDIATE 'COMMENT ON COLUMN GESTIONRRHH.INDECI_MOVIMIENTO_PLANILLA_DET.CONCEPTO_CODIGO IS '
        || '''SPEC_CONCEPTOS_PLANILLA P3 — snapshot del CODIGO del concepto al grabar (trazabilidad historica). NULL en filas previas a V010_99: fallback al concepto vivo.''';
    EXECUTE IMMEDIATE 'COMMENT ON COLUMN GESTIONRRHH.INDECI_MOVIMIENTO_PLANILLA_DET.CONCEPTO_NOMBRE IS '
        || '''SPEC_CONCEPTOS_PLANILLA P3 — snapshot del NOMBRE del concepto al grabar. NULL = fallback al nombre vivo.''';
    EXECUTE IMMEDIATE 'COMMENT ON COLUMN GESTIONRRHH.INDECI_MOVIMIENTO_PLANILLA_DET.CONCEPTO_TIPO IS '
        || '''SPEC_CONCEPTOS_PLANILLA P3 — snapshot del TIPO_CONCEPTO al grabar. NULL = fallback al concepto vivo.''';
    EXECUTE IMMEDIATE 'COMMENT ON COLUMN GESTIONRRHH.INDECI_CONCEPTO_PLANILLA.VERSION IS '
        || '''SPEC_CONCEPTOS_PLANILLA P3 — n.º de version por CODIGO. crearNuevaVersion() clona con max(version)+1. DEFAULT 1.''';

    DBMS_OUTPUT.PUT_LINE('V010_99 finalizado.');

    -- Verificación final.
    SELECT COUNT(*) INTO v_count
      FROM ALL_TAB_COLUMNS
     WHERE OWNER = 'GESTIONRRHH'
       AND TABLE_NAME = 'INDECI_MOVIMIENTO_PLANILLA_DET'
       AND COLUMN_NAME IN ('CONCEPTO_CODIGO', 'CONCEPTO_NOMBRE', 'CONCEPTO_TIPO');
    DBMS_OUTPUT.PUT_LINE('Columnas snapshot en INDECI_MOVIMIENTO_PLANILLA_DET: ' || v_count || ' / 3.');

    SELECT COUNT(*) INTO v_count
      FROM ALL_TAB_COLUMNS
     WHERE OWNER = 'GESTIONRRHH'
       AND TABLE_NAME = 'INDECI_CONCEPTO_PLANILLA'
       AND COLUMN_NAME = 'VERSION';
    DBMS_OUTPUT.PUT_LINE('Columna VERSION en INDECI_CONCEPTO_PLANILLA: ' || v_count || ' / 1.');
END;
/
