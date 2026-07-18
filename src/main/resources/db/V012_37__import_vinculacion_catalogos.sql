-- ============================================================================
-- V012_37 — Catálogos y campos para el Import de Vinculación (Registro
--           Integrado de Personal).
--
-- Origen: docs/PLANTILLA_IMPORT_VINCULACION_oficial.xlsx (hoja VINCULACION,
--         617 filas reales entregadas por el especialista de RR.HH.).
--         Ver INFORME_VERIFICACION_EXCEL_OFICIAL.md.
--
-- Principio: el Excel es la verdad; el sistema se adapta para que los datos
--            migren tal cual.
--
-- IDEMPOTENTE: MERGE / WHERE NOT EXISTS / chequeo en USER_TAB_COLUMNS.
--              Seguro de re-ejecutar. Naming REGLA-05.
-- ============================================================================
SET SERVEROUTPUT ON;

-- ----------------------------------------------------------------------------
-- 1) INDECI_REGIMEN_PENSIONARIO — alta de CPMP
--    Excel col BD trae 'PENSIONISTA CPMP' (Caja de Pensiones Militar-Policial,
--    D.L. 19846 / D.Leg. 1133). Coincide con TIPO_PERSONA_MEF código 7.
-- ----------------------------------------------------------------------------
MERGE INTO GESTIONRRHH.INDECI_REGIMEN_PENSIONARIO dst
USING (
    SELECT 'CPMP' AS CODIGO, 'Pensionista CPMP (D.L. 19846 / D.Leg. 1133)' AS NOMBRE, 'CPMP' AS TIPO FROM DUAL
) src ON (dst.CODIGO = src.CODIGO)
WHEN NOT MATCHED THEN
    INSERT (CODIGO, NOMBRE, TIPO, ACTIVO)
    VALUES (src.CODIGO, src.NOMBRE, src.TIPO, 1);

-- ----------------------------------------------------------------------------
-- 2) INDECI_TIPO_CONTRATO — alta de CONFIANZA
--    Excel col AD trae 'CONFIANZA' además de PLAZO DETERMINADO/INDETERMINADO.
--    NOTA: RR.HH. debe confirmar si CONFIANZA es tipo de contrato o modalidad;
--          se da de alta para que el dato migre tal cual.
-- ----------------------------------------------------------------------------
MERGE INTO GESTIONRRHH.INDECI_TIPO_CONTRATO dst
USING (
    SELECT 'CONFIANZA' AS CODIGO, 'CONFIANZA' AS NOMBRE FROM DUAL
) src ON (dst.CODIGO = src.CODIGO)
WHEN NOT MATCHED THEN
    INSERT (CODIGO, NOMBRE, ACTIVO)
    VALUES (src.CODIGO, src.NOMBRE, 1);

-- ----------------------------------------------------------------------------
-- 3) BANKS — bancos canónicos presentes en el Excel (col BN)
--    El Excel trae 13 variantes que colapsan en 7 bancos reales:
--      NACION / BANCO DE LA NACIÓN / 'BANCO DE LA BANCO DE LA NACION'
--        (residuo de buscar-y-reemplazar)      -> BANCO DE LA NACION
--      BCP                                      -> BANCO DE CREDITO DEL PERU
--      BBVA / CONTINENTAL                       -> BBVA PERU
--      BANBINF (typo)                           -> BANBIF
--
--    OJO: BANKS es una tabla legacy SIN IDENTITY (a diferencia de las INDECI_*):
--    su ID es NOT NULL y no se autogenera —un INSERT que lo omita falla con
--    ORA-01400—, pero sí existe la secuencia SEQ_BANKS, que es la que se usa.
--
--    Idempotente: solo inserta el banco si no existe ya un nombre equivalente
--    (comparación case-insensitive y sin espacios sobrantes).
-- ----------------------------------------------------------------------------
-- ⏸ DIFERIDO A V012_38 — no se insertan bancos en esta migración.
--
-- Motivo: BANKS es una tabla legacy cuya estructura real no coincide con la entidad
-- Bank.java (que solo mapea ID, NAME y ACTIVO). Al intentar el INSERT aparecieron
-- dos columnas obligatorias que la entidad ignora:
--     · ID   NOT NULL sin IDENTITY  -> ORA-01400 (se resolvería con SEQ_BANKS)
--     · CODE NOT NULL               -> ORA-01400 (formato desconocido)
-- No se inventa un CODE: BANKS ya está en uso por las cuentas de abono y un valor
-- equivocado sería peor que la ausencia del banco.
--
-- Impacto medido sobre el Excel real: solo 3 de 616 trabajadores usan estos bancos
--     2 → BANBIF (el Excel lo escribe 'BANBINF')
--     1 → BANCO PICHINCHA
-- Los otros 613 usan bancos que YA existen (BCP 216, BBVA PERU 175, BANCO DE LA
-- NACION 116, INTERBANK 59, SCOTIABANK PERU 47), así que la carga no se bloquea.
--
-- Siguiente paso: ejecutar scripts/diagnostico-banks.sql y, con el formato real de
-- CODE, dar de alta ambos bancos en V012_38.

-- ----------------------------------------------------------------------------
-- 3b) INDECI_REGIMEN_PENSIONARIO — alta de HABITAT
--     El Excel (col BE) tiene afiliados a HABITAT, pero el catálogo solo trae
--     INTEGRA, PRIMA y PROFUTURO. Al ser un catálogo CERRADO (no se inventan AFP),
--     sin esta fila esos trabajadores quedarían sin régimen pensionario.
-- ----------------------------------------------------------------------------
MERGE INTO GESTIONRRHH.INDECI_REGIMEN_PENSIONARIO dst
USING (
    SELECT 'HABITAT' AS CODIGO, 'HABITAT' AS NOMBRE, 'AFP' AS TIPO FROM DUAL
) src ON (dst.CODIGO = src.CODIGO)
WHEN NOT MATCHED THEN
    INSERT (CODIGO, NOMBRE, TIPO, ACTIVO)
    VALUES (src.CODIGO, src.NOMBRE, src.TIPO, 1);

-- ----------------------------------------------------------------------------
-- 4) INDECI_ESTADO_CIVIL — valores canónicos
--    Excel col F trae 11 variantes (Casada/Casado, Concuvina/Concuvino,
--    Soltera/Soltero, '0'...). Se siembran los canónicos; el diccionario de
--    equivalencias lo aplica el importador.
-- ----------------------------------------------------------------------------
MERGE INTO GESTIONRRHH.INDECI_ESTADO_CIVIL dst
USING (
    SELECT 'SOLTERO'     AS CODIGO, 'Soltero(a)'     AS NOMBRE FROM DUAL UNION ALL
    SELECT 'CASADO'      AS CODIGO, 'Casado(a)'      AS NOMBRE FROM DUAL UNION ALL
    SELECT 'CONVIVIENTE' AS CODIGO, 'Conviviente'    AS NOMBRE FROM DUAL UNION ALL
    SELECT 'DIVORCIADO'  AS CODIGO, 'Divorciado(a)'  AS NOMBRE FROM DUAL UNION ALL
    SELECT 'VIUDO'       AS CODIGO, 'Viudo(a)'       AS NOMBRE FROM DUAL
) src ON (dst.CODIGO = src.CODIGO)
WHEN NOT MATCHED THEN
    INSERT (CODIGO, NOMBRE) VALUES (src.CODIGO, src.NOMBRE);

-- ----------------------------------------------------------------------------
-- 5) INDECI_EMPLEADO.CLASE_PERSONAL — campo nuevo
--    Excel col J 'PERSONA' trae 4 valores: Civil / Militar / Marina / PNP.
--    NO confundir con TIPO_PERSONA_MEF ni con GRUPO_SERVIDOR_CIVIL (Ley 30057).
-- ----------------------------------------------------------------------------
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count
      FROM ALL_TAB_COLUMNS
     WHERE OWNER = 'GESTIONRRHH'
       AND TABLE_NAME = 'INDECI_EMPLEADO'
       AND COLUMN_NAME = 'CLASE_PERSONAL';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE
            'ALTER TABLE GESTIONRRHH.INDECI_EMPLEADO ADD CLASE_PERSONAL VARCHAR2(10)';
        EXECUTE IMMEDIATE
            'ALTER TABLE GESTIONRRHH.INDECI_EMPLEADO ADD CONSTRAINT INDECI_EMPLEADO_CLASE_PERS_CK '
            || 'CHECK (CLASE_PERSONAL IN (''CIVIL'',''MILITAR'',''MARINA'',''PNP''))';
        DBMS_OUTPUT.PUT_LINE('Columna CLASE_PERSONAL agregada a INDECI_EMPLEADO.');
    ELSE
        DBMS_OUTPUT.PUT_LINE('CLASE_PERSONAL ya existe.');
    END IF;
END;
/

-- ----------------------------------------------------------------------------
-- 6) INDECI_EMPLEADO_PLANILLA — campos nuevos del vínculo
--    NRO_CONVOCATORIA   (Excel col AK): valores hasta 34 chars, incluye textos
--                       como 'R J N° 000193-2024-INDECI/JEF' y 'REPOSICION JUDICIAL'.
--    BASE_LEGAL_VINCULO (Excel col AE): bases legales ('DECRETO URGENCIA Nº 034-2021',
--                       'LEY Nº 31131 - INDETERMINADO (CONTRATO TEMPORAL)'), que NO son
--                       la condición laboral NOMBRADO/CONTRATADO del catálogo.
-- ----------------------------------------------------------------------------
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM ALL_TAB_COLUMNS
     WHERE OWNER='GESTIONRRHH' AND TABLE_NAME='INDECI_EMPLEADO_PLANILLA'
       AND COLUMN_NAME='NRO_CONVOCATORIA';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE GESTIONRRHH.INDECI_EMPLEADO_PLANILLA ADD NRO_CONVOCATORIA VARCHAR2(40)';
        DBMS_OUTPUT.PUT_LINE('Columna NRO_CONVOCATORIA agregada.');
    END IF;

    SELECT COUNT(*) INTO v_count FROM ALL_TAB_COLUMNS
     WHERE OWNER='GESTIONRRHH' AND TABLE_NAME='INDECI_EMPLEADO_PLANILLA'
       AND COLUMN_NAME='BASE_LEGAL_VINCULO';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE GESTIONRRHH.INDECI_EMPLEADO_PLANILLA ADD BASE_LEGAL_VINCULO VARCHAR2(200)';
        DBMS_OUTPUT.PUT_LINE('Columna BASE_LEGAL_VINCULO agregada.');
    END IF;
END;
/

-- ----------------------------------------------------------------------------
-- 7) Ampliar NUMERO_CONTRATO — el Excel trae hasta 39 chars
--    (ej. 'OFICIO N° 02772-2026-MINDEF/VRD-DGRRHH'). Solo amplía si es menor.
-- ----------------------------------------------------------------------------
DECLARE
    v_len NUMBER;
BEGIN
    SELECT CHAR_LENGTH INTO v_len FROM ALL_TAB_COLUMNS
     WHERE OWNER='GESTIONRRHH' AND TABLE_NAME='INDECI_EMPLEADO_PLANILLA'
       AND COLUMN_NAME='NUMERO_CONTRATO';
    IF v_len < 60 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE GESTIONRRHH.INDECI_EMPLEADO_PLANILLA MODIFY NUMERO_CONTRATO VARCHAR2(60)';
        DBMS_OUTPUT.PUT_LINE('NUMERO_CONTRATO ampliado a VARCHAR2(60).');
    END IF;
EXCEPTION
    WHEN NO_DATA_FOUND THEN
        DBMS_OUTPUT.PUT_LINE('NUMERO_CONTRATO no encontrado; revisar esquema.');
END;
/

COMMIT;
