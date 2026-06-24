-- ============================================================================
-- V010_92 subsidio_base_manual (P0-F2 / Base histórica editable)
--
-- Habilita la captura MANUAL/MIXTA de la base histórica del subsidio para un
-- sistema sin 12 meses de planilla propia (greenfield). El motor de subsidio
-- diario ya calcula sobre INDECI_SUBSIDIO_BASE_DETALLE, así que basta con poder
-- crear/editar esas filas a mano.
--
-- COLUMNAS NUEVAS (INDECI_SUBSIDIO_BASE_DETALLE):
--   INCIDENCIA  VARCHAR2(20)  → etiqueta de trazabilidad del mes
--                               (NORMAL | LSGR | FALTA | REINTEGRO | PARCIAL).
--                               NO fuerza el monto: LSGR puede ser 0 o parcial;
--                               el monto real lo ingresa RR. HH.
--   ES_MANUAL   CHAR(1)       → 'S' si la fila fue ingresada/ajustada a mano.
--
-- FUENTE (INDECI_SUBSIDIO_BASE_HISTORICA): admite ahora 'MANUAL' y 'MIXTA'
--   además de 'PLANILLA' / 'PARAMETRO'. La columna es VARCHAR2 sin CHECK, así
--   que no requiere DDL de constraint.
--
-- PARÁMETRO NUEVO: SUBSIDIO_DIVISOR_MODO (TEXTO) = 'FIJO_360' (default) |
--   'PROPORCIONAL'. Controla el divisor cuando hay afiliación < 12 meses.
--   Por defecto FIJO_360 (comportamiento actual: el divisor sigue siendo 360
--   aunque existan meses en LSGR/0 dentro de los 12).
--
-- Idempotente (add_column_if_missing + MERGE). Ejecutar en GESTIONRRHH / Oracle 19c+.
-- ============================================================================

SET SERVEROUTPUT ON;
SET DEFINE OFF;

DECLARE
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
           AND TABLE_NAME  = 'INDECI_SUBSIDIO_BASE_DETALLE'
           AND COLUMN_NAME = p_col_name;

        IF v_exists = 0 THEN
            EXECUTE IMMEDIATE
                'ALTER TABLE GESTIONRRHH.INDECI_SUBSIDIO_BASE_DETALLE ADD ('
                || p_col_ddl || ')';
            DBMS_OUTPUT.PUT_LINE(p_col_name || ' -> agregada.');
        ELSE
            DBMS_OUTPUT.PUT_LINE(p_col_name || ' ya existe. Sin cambios.');
        END IF;
    END;
BEGIN
    add_column_if_missing('INCIDENCIA', 'INCIDENCIA VARCHAR2(20)');
    add_column_if_missing('ES_MANUAL',  'ES_MANUAL CHAR(1) DEFAULT ''N''');
END;
/

-- Parámetro: modo de divisor para afiliación < 12 meses ----------------------
MERGE INTO INDECI_SUBSIDIO_PARAMETRO d
USING (
    SELECT 'SUBSIDIO_DIVISOR_MODO' AS CODIGO,
           'Modo de divisor base (FIJO_360 | PROPORCIONAL)' AS NOMBRE,
           'TEXTO' AS TIPO_VALOR
      FROM DUAL
) s ON (d.CODIGO = s.CODIGO)
WHEN NOT MATCHED THEN
    INSERT (CODIGO, NOMBRE, TIPO_VALOR, ACTIVO)
    VALUES (s.CODIGO, s.NOMBRE, s.TIPO_VALOR, 1);

MERGE INTO INDECI_SUBSIDIO_PARAMETRO_VERSION d
USING (
    SELECT p.ID AS PARAMETRO_ID,
           CAST(NULL AS NUMBER) AS VALOR_NUMERICO,
           'FIJO_360' AS VALOR_TEXTO,
           DATE '2026-01-01' AS FECHA_VIG_INI,
           CAST(NULL AS DATE) AS FECHA_VIG_FIN,
           'VIGENTE' AS ESTADO,
           'Política INDECI (transición greenfield)' AS FUENTE_NORMATIVA
      FROM INDECI_SUBSIDIO_PARAMETRO p
     WHERE p.CODIGO = 'SUBSIDIO_DIVISOR_MODO'
) s ON (d.PARAMETRO_ID = s.PARAMETRO_ID AND d.ESTADO = 'VIGENTE'
        AND d.FECHA_VIG_INI = s.FECHA_VIG_INI)
WHEN NOT MATCHED THEN
    INSERT (PARAMETRO_ID, VALOR_NUMERICO, VALOR_TEXTO, FECHA_VIG_INI, FECHA_VIG_FIN,
            ESTADO, FUENTE_NORMATIVA, CREATED_BY)
    VALUES (s.PARAMETRO_ID, s.VALOR_NUMERICO, s.VALOR_TEXTO, s.FECHA_VIG_INI, s.FECHA_VIG_FIN,
            s.ESTADO, s.FUENTE_NORMATIVA, 'SEED_V010_92');

COMMIT;

BEGIN
    DBMS_OUTPUT.PUT_LINE('V010_92 base manual subsidio aplicada.');
END;
/
