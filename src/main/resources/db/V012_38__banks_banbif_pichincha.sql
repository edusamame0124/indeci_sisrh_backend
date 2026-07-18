-- ============================================================================
-- V012_38 — BANKS: alta de BANBIF y BANCO PICHINCHA
--
-- Completa lo que quedó diferido de V012_37. Origen: docs/PLANTILLA_IMPORT_
-- VINCULACION_oficial.xlsx (col BN), que trae 3 trabajadores en estos bancos:
--     2 → BANBIF (el Excel lo escribe con typo: 'BANBINF')
--     1 → BANCO PICHINCHA
-- Los otros 613 usan bancos que ya existen.
--
-- Estructura real de BANKS (verificada con scripts/diagnostico-banks.sql). Es una
-- tabla legacy cuyo esquema NO coincide con la entidad Bank.java —que solo mapea
-- ID, NAME y ACTIVO—; tiene 4 columnas obligatorias que la entidad ignora:
--     ID         NUMBER        NOT NULL, sin IDENTITY -> se usa SEQ_BANKS
--     CODE       VARCHAR2(20)  NOT NULL  -> código corto (BCP, BN, SCOTIA...)
--     STATUS     VARCHAR2(20)  NOT NULL  -> 'ACTIVE'
--     CREATED_AT TIMESTAMP(6)  NOT NULL
-- Convención observada en las filas existentes:
--     ID 1 | BCP       | BANCO DE CREDITO DEL PERU | BCP          | ACTIVE
--     ID 3 | BN        | BANCO DE LA NACION        | BANCO NACION | ACTIVE
--     ID 5 | SCOTIA    | SCOTIABANK PERU           | SCOTIABANK   | ACTIVE
--
-- Los NAME deben coincidir con los canónicos de DiccionarioEquivalencias: el
-- importador resuelve el banco por nombre y BANCO es un catálogo CERRADO.
--
-- IDEMPOTENTE: solo inserta si no existe el nombre (case-insensitive).
-- ============================================================================
SET SERVEROUTPUT ON;

DECLARE
    TYPE t_banco IS RECORD (
        code       VARCHAR2(20),
        name       VARCHAR2(150),
        short_name VARCHAR2(50));
    TYPE t_bancos IS TABLE OF t_banco;

    v_bancos t_bancos := t_bancos(
        t_banco('BANBIF',    'BANBIF',          'BANBIF'),
        t_banco('PICHINCHA', 'BANCO PICHINCHA', 'PICHINCHA'));

    v_existe NUMBER;
    v_id     NUMBER;
    v_max_id NUMBER;
BEGIN
    FOR i IN 1 .. v_bancos.COUNT LOOP
        SELECT COUNT(*) INTO v_existe
          FROM GESTIONRRHH.BANKS
         WHERE UPPER(TRIM(NAME)) = UPPER(TRIM(v_bancos(i).name))
            OR UPPER(TRIM(CODE)) = UPPER(TRIM(v_bancos(i).code));

        IF v_existe = 0 THEN
            SELECT GESTIONRRHH.SEQ_BANKS.NEXTVAL INTO v_id FROM DUAL;

            -- Si la secuencia quedó desfasada respecto de los datos (las 5 filas
            -- existentes se cargaron manualmente), se avanza hasta un valor libre.
            -- La subconsulta va a una variable: PL/SQL no la admite dentro del WHILE.
            SELECT NVL(MAX(ID), 0) INTO v_max_id FROM GESTIONRRHH.BANKS;
            WHILE v_id <= v_max_id LOOP
                SELECT GESTIONRRHH.SEQ_BANKS.NEXTVAL INTO v_id FROM DUAL;
            END LOOP;

            INSERT INTO GESTIONRRHH.BANKS (ID, CODE, NAME, SHORT_NAME, STATUS, CREATED_AT, ACTIVO)
            VALUES (v_id, v_bancos(i).code, v_bancos(i).name, v_bancos(i).short_name,
                    'ACTIVE', SYSTIMESTAMP, 1);

            DBMS_OUTPUT.PUT_LINE('Banco agregado: ' || v_bancos(i).name
                    || ' (ID ' || v_id || ', CODE ' || v_bancos(i).code || ')');
        ELSE
            DBMS_OUTPUT.PUT_LINE('Banco ya existe: ' || v_bancos(i).name);
        END IF;
    END LOOP;
END;
/

COMMIT;

-- Verificación: deben quedar 7 bancos activos.
SELECT ID, CODE, NAME, SHORT_NAME, STATUS, ACTIVO
  FROM GESTIONRRHH.BANKS
 ORDER BY ID;
