-- ============================================================================
-- F1.1 / V010_36 — INDECI_EMPLEADO_PLANILLA: MONTO_CONTRATO + FECHA_VENC_CONTRATO
--                                            + DIAS_LABORADOS_DEFAULT
--
-- CONTEXTO (Motor v3 / Spec §17.5):
--   El Excel "PLANILLA CAS CONSOLIDADA" maneja MONTO_CONTRATO como base del
--   cálculo (distinto de AIRHSP_MONTO que ya existe en INDECI_EMPLEADO).
--   El cliente lo necesita para el prorrateo por días laborados (PASO 3).
--
--   Hoy el motor lee "remuneración" como suma de EmpleadoConcepto manuales
--   ([[motor-remunerativos-deuda-tecnica]]). Con esta columna podremos
--   parametrizar la base contractual por empleado/período y prorratearla
--   correctamente en F1.3.
--
-- DECISIONES DE DISEÑO:
--   - MONTO_CONTRATO NUMBER(12,2) NULL: nullable para no romper filas
--     existentes (backfill se hará en F1.3 cuando se sepa qué tomar como
--     fuente: AIRHSP_MONTO, SUELDO_BASICO o EmpleadoConcepto agregado).
--   - FECHA_VENC_CONTRATO DATE NULL: vencimiento del contrato vigente (CAS
--     típicamente firma por 3/6/12 meses). Distinto de:
--         FECHA_FIN     → vigencia del registro INDECI_EMPLEADO_PLANILLA
--         FECHA_CESE    → salida laboral del empleado (vinculo terminado)
--         FECHA_INGRESO → inicio del vínculo laboral
--     Se nombra VENC_CONTRATO (no TERMINO) para no chocar con FECHA_FIN.
--   - DIAS_LABORADOS_DEFAULT NUMBER(3) DEFAULT 30 NOT NULL: días estándar
--     del período (30 mes completo). Permite a futuro casos especiales
--     (p.ej. empleados a tiempo parcial con 15 días default). El motor
--     calcula los días reales restando eventos del período (faltas,
--     licencias sin goce) sobre este default.
--
-- IDEMPOTENTE: ALTER ADD COLUMN sólo si la columna no existe.
-- Las tablas viven en TS_GESTIONRRHH_DATA (ver [[claude-md-aspiracional]]).
-- ============================================================================

SET SERVEROUTPUT ON;

DECLARE
    PROCEDURE add_column_if_missing(
        p_table_name  VARCHAR2,
        p_column_name VARCHAR2,
        p_column_ddl  VARCHAR2
    ) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_exists
          FROM USER_TAB_COLUMNS
         WHERE TABLE_NAME = p_table_name
           AND COLUMN_NAME = p_column_name;
        IF v_exists = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE ' || p_table_name
                || ' ADD ' || p_column_ddl;
            DBMS_OUTPUT.PUT_LINE(p_table_name || '.' || p_column_name || ' -> agregada.');
        ELSE
            DBMS_OUTPUT.PUT_LINE(p_table_name || '.' || p_column_name || ' ya existe. Sin cambios.');
        END IF;
    END;
BEGIN
    add_column_if_missing(
        'INDECI_EMPLEADO_PLANILLA',
        'MONTO_CONTRATO',
        'MONTO_CONTRATO NUMBER(12,2)'
    );
    add_column_if_missing(
        'INDECI_EMPLEADO_PLANILLA',
        'FECHA_VENC_CONTRATO',
        'FECHA_VENC_CONTRATO DATE'
    );
    add_column_if_missing(
        'INDECI_EMPLEADO_PLANILLA',
        'DIAS_LABORADOS_DEFAULT',
        'DIAS_LABORADOS_DEFAULT NUMBER(3) DEFAULT 30 NOT NULL'
    );

    -- Comentarios documentales (idempotentes — COMMENT no falla si ya existe).
    EXECUTE IMMEDIATE 'COMMENT ON COLUMN INDECI_EMPLEADO_PLANILLA.MONTO_CONTRATO IS '
        || '''F1 Motor v3 — Monto base mensual del contrato del empleado (S/). Distinto de AIRHSP_MONTO. Usado como base de prorrateo PASO 3.''';
    EXECUTE IMMEDIATE 'COMMENT ON COLUMN INDECI_EMPLEADO_PLANILLA.FECHA_VENC_CONTRATO IS '
        || '''F1 Motor v3 — Fecha de vencimiento del contrato vigente. CAS firma por períodos (3/6/12 meses); el Centro de Validaciones alerta cuando esta fecha pasó del período.''';
    EXECUTE IMMEDIATE 'COMMENT ON COLUMN INDECI_EMPLEADO_PLANILLA.DIAS_LABORADOS_DEFAULT IS '
        || '''F1 Motor v3 — Días estándar del período (default 30). El motor PASO 3 calcula días reales restando eventos sobre este valor.''';

    DBMS_OUTPUT.PUT_LINE('V010_36 listo.');
END;
/

COMMIT;
