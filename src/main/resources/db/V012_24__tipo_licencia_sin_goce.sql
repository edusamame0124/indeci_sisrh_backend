-- ============================================================================
-- SPEC_VACACIONES F9.1 — Clasificación de licencias sin goce (Opción A).
--
-- Agrega a INDECI_TIPO_LICENCIA los flags que permiten a RR.HH. clasificar la
-- ausencia y al motor descontar récord/días laborados:
--   ES_SIN_GOCE          1 = sin goce de haber (día no remunerado, resta récord)
--   REQUIERE_RESOLUCION  1 = exige Resolución Directoral (auditoría Contraloría/SERVIR)
--   COD_PLAME_SUNAT      Tabla 21 PLAME (SUNAT) — sin goce se agrupan en '05'
--
-- Siembra SOLO los subtipos SIN GOCE (los CON GOCE se sembrarán en la papeleta con goce;
-- por DEFAULT quedan en 0 = seguro). UPSERT por CODIGO: NO borra ni duplica.
--
-- IMPORTANTE (ORA-00904): PL/SQL compila TODO el bloque antes de ejecutarlo. Como las
-- columnas se agregan en runtime, los DML que las referencian deben ser DINÁMICOS
-- (EXECUTE IMMEDIATE) para no fallar en compilación. Idempotente. Oracle 19c+ / GESTIONRRHH.
-- ============================================================================

SET SERVEROUTPUT ON;

DECLARE
    PROCEDURE add_column_if_missing(p_column VARCHAR2, p_ddl VARCHAR2) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_exists FROM ALL_TAB_COLUMNS
         WHERE OWNER='GESTIONRRHH' AND TABLE_NAME='INDECI_TIPO_LICENCIA' AND COLUMN_NAME=p_column;
        IF v_exists = 0 THEN
            EXECUTE IMMEDIATE p_ddl;
            DBMS_OUTPUT.PUT_LINE('INDECI_TIPO_LICENCIA.'||p_column||' -> agregada.');
        ELSE
            DBMS_OUTPUT.PUT_LINE('INDECI_TIPO_LICENCIA.'||p_column||' ya existe.');
        END IF;
    END;

    -- DML dinámico: no se valida contra las columnas en compile-time.
    PROCEDURE upsert_tipo(
        p_codigo VARCHAR2, p_nombre VARCHAR2,
        p_sin_goce NUMBER, p_req_reso NUMBER, p_cod_plame VARCHAR2
    ) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_exists
          FROM GESTIONRRHH.INDECI_TIPO_LICENCIA WHERE CODIGO = p_codigo;
        IF v_exists = 0 THEN
            EXECUTE IMMEDIATE
                'INSERT INTO GESTIONRRHH.INDECI_TIPO_LICENCIA '
                || '(NOMBRE, ACTIVO, CODIGO, ES_SIN_GOCE, REQUIERE_RESOLUCION, COD_PLAME_SUNAT) '
                || 'VALUES (:1, 1, :2, :3, :4, :5)'
                USING p_nombre, p_codigo, p_sin_goce, p_req_reso, p_cod_plame;
        ELSE
            EXECUTE IMMEDIATE
                'UPDATE GESTIONRRHH.INDECI_TIPO_LICENCIA '
                || 'SET ES_SIN_GOCE = :1, REQUIERE_RESOLUCION = :2, COD_PLAME_SUNAT = :3 '
                || 'WHERE CODIGO = :4'
                USING p_sin_goce, p_req_reso, p_cod_plame, p_codigo;
        END IF;
    END;
BEGIN
    -- 1) Columnas nuevas (idempotente).
    add_column_if_missing('ES_SIN_GOCE',
        'ALTER TABLE GESTIONRRHH.INDECI_TIPO_LICENCIA ADD (ES_SIN_GOCE NUMBER(1) DEFAULT 0)');
    add_column_if_missing('REQUIERE_RESOLUCION',
        'ALTER TABLE GESTIONRRHH.INDECI_TIPO_LICENCIA ADD (REQUIERE_RESOLUCION NUMBER(1) DEFAULT 0)');
    add_column_if_missing('COD_PLAME_SUNAT',
        'ALTER TABLE GESTIONRRHH.INDECI_TIPO_LICENCIA ADD (COD_PLAME_SUNAT VARCHAR2(2 CHAR))');

    -- 2) Subtipos SIN GOCE (ES_SIN_GOCE=1, Tabla 21 PLAME = '05'). Los CON GOCE van en su
    --    propia migración (papeleta con goce); por DEFAULT quedan en 0 (seguro).
    upsert_tipo('LIC_SIN_PAR', 'Licencia Sin Goce - Motivos Particulares / Personales',    1, 1, '05');
    upsert_tipo('LIC_SIN_CAP', 'Licencia Sin Goce - Capacitación / Estudios no auspiciados',1, 1, '05');
    upsert_tipo('LIC_SIN_CON', 'Licencia Sin Goce - Cargo de Confianza en otra Entidad',   1, 1, '05');
    upsert_tipo('LIC_SIN_ENF', 'Licencia Sin Goce - Cuidado Familiar Enfermo (exceso Ley 30012)', 1, 1, '05');
    upsert_tipo('LIC_SIN_PUB', 'Licencia Sin Goce - Cargo Público / Elección Cívica',      1, 1, '05');

    -- 3) Anti pago-indebido: filas legacy que ya dicen "SIN GOCE" (dinámico).
    EXECUTE IMMEDIATE q'[UPDATE GESTIONRRHH.INDECI_TIPO_LICENCIA
                            SET ES_SIN_GOCE = 1
                          WHERE UPPER(NOMBRE) LIKE '%SIN GOCE%'
                            AND (ES_SIN_GOCE IS NULL OR ES_SIN_GOCE = 0)]';

    -- 4) Normalizar NULLs a 0 en filas preexistentes (dinámico).
    EXECUTE IMMEDIATE 'UPDATE GESTIONRRHH.INDECI_TIPO_LICENCIA SET ES_SIN_GOCE = 0 WHERE ES_SIN_GOCE IS NULL';
    EXECUTE IMMEDIATE 'UPDATE GESTIONRRHH.INDECI_TIPO_LICENCIA SET REQUIERE_RESOLUCION = 0 WHERE REQUIERE_RESOLUCION IS NULL';

    COMMIT;
    DBMS_OUTPUT.PUT_LINE('V012_24 finalizado.');
END;
/
