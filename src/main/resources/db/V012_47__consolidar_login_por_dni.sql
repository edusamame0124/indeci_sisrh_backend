-- ============================================================================
-- V012_47 — Consolidación de login: "todo debe ser por DNI" (decisión de dirección).
--
-- Contexto: se detectó que una alta masiva previa creó una cuenta con username=DNI
-- para gran parte de la planilla, pero NUNCA quedó vinculada a su Empleado
-- (USERS.EMPLEADO_ID = NULL). La cuenta realmente vinculada sigue siendo una
-- cuenta antigua con nombre de usuario libre (ej. 'amunares'). Resultado: el
-- empleado entra con su DNI y el sistema responde 400 "no tiene un empleado
-- vinculado" en /persona/me, /legajo/resumen/me, etc.
--
-- Decisiones de dirección (confirmadas):
--   1. La cuenta antigua se INACTIVA (no se borra) una vez vinculada la de DNI.
--   2. Las cuentas-DNI sin ningún Empleado detrás (Persona sin fila en
--      INDECI_EMPLEADO) NO se tocan — quedan reportadas al final para revisión
--      manual de RR.HH. (puede faltar registrar su vínculo laboral).
--
-- Por cada par (cuenta-DNI huérfana, cuenta-antigua vinculada):
--   1. Unifica en la cuenta-DNI los roles/permisos directos/denegaciones que
--      tuviera la cuenta antigua (para que NADIE pierda una capacidad que ya
--      tenía al consolidar — ej. si la antigua era además JEFE).
--   2. Vincula la cuenta-DNI al Empleado (USERS.EMPLEADO_ID).
--   3. Mueve INDECI_PERSONA.USER_ID a la cuenta-DNI (pasa a ser "la" cuenta
--      institucional de esa persona).
--   4. Limpia EMPLEADO_ID de la cuenta antigua ANTES de inactivarla (evita
--      cualquier conflicto si USERS.EMPLEADO_ID tuviera unicidad a nivel BD —
--      no se verificó ese constraint, se opera defensivo).
--   5. Inactiva la cuenta antigua (STATUS='INACTIVE').
--
-- PASO 0 — el diagnóstico se congela en una tabla de trabajo la primera vez
-- que corre el script; las corridas siguientes reutilizan esa misma lista
-- (no vuelve a derivar candidatos), así que es seguro reejecutar.
--
-- Idempotente. Ejecutar en GESTIONRRHH.
-- ============================================================================

SET SERVEROUTPUT ON;
SET DEFINE OFF;

-- ── PASO 0: tablas de trabajo y respaldo (tablespace heredado de USERS) ────
DECLARE
    v_tbs    VARCHAR2(128);
    v_clause VARCHAR2(160) := '';

    PROCEDURE crear(p_ddl VARCHAR2) IS
    BEGIN
        EXECUTE IMMEDIATE p_ddl;
    EXCEPTION
        WHEN OTHERS THEN
            IF SQLCODE != -955 THEN RAISE; END IF;  -- -955 = ya existe
    END;
BEGIN
    BEGIN
        SELECT tablespace_name INTO v_tbs
          FROM all_tables WHERE owner = 'GESTIONRRHH' AND table_name = 'USERS';
    EXCEPTION
        WHEN NO_DATA_FOUND THEN v_tbs := NULL;
        WHEN TOO_MANY_ROWS THEN v_tbs := NULL;
    END;
    IF v_tbs IS NOT NULL THEN
        v_clause := ' TABLESPACE ' || v_tbs;
    END IF;

    crear('CREATE TABLE GESTIONRRHH.BKP_V01247_CANDIDATOS
             (DNI_USER_ID NUMBER, DNI_USERNAME VARCHAR2(100),
              LEGACY_USER_ID NUMBER, LEGACY_USERNAME VARCHAR2(100),
              PERSONA_ID NUMBER, EMPLEADO_ID NUMBER,
              PROCESADO CHAR(1) DEFAULT ''N'')' || v_clause);

    crear('CREATE TABLE GESTIONRRHH.BKP_V01247_USERS
             (ID NUMBER, USERNAME VARCHAR2(100), EMPLEADO_ID NUMBER, STATUS VARCHAR2(20))'
          || v_clause);
    crear('CREATE TABLE GESTIONRRHH.BKP_V01247_PERSONA
             (ID NUMBER, DNI VARCHAR2(20), USER_ID NUMBER)' || v_clause);
    crear('CREATE TABLE GESTIONRRHH.BKP_V01247_USR_ROL
             (ID_USER NUMBER, ID_ROL NUMBER, SISTEMA VARCHAR2(50))' || v_clause);
    crear('CREATE TABLE GESTIONRRHH.BKP_V01247_USR_PERM
             (ID_USER NUMBER, ID_PERMISO NUMBER)' || v_clause);
    crear('CREATE TABLE GESTIONRRHH.BKP_V01247_USR_PERM_DENY
             (ID_USER NUMBER, ID_PERMISO NUMBER)' || v_clause);

    DBMS_OUTPUT.PUT_LINE('0. Tablas de trabajo y respaldo listas.');
END;
/

-- ── PASO 1: congelar la lista de candidatos (solo la primera vez) ─────────
DECLARE
    v_filas NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_filas FROM GESTIONRRHH.BKP_V01247_CANDIDATOS;
    IF v_filas > 0 THEN
        DBMS_OUTPUT.PUT_LINE('1. Lista de candidatos ya existía (' || v_filas
            || ' fila(s)) — se reutiliza, no se vuelve a derivar.');
        RETURN;
    END IF;

    INSERT INTO GESTIONRRHH.BKP_V01247_CANDIDATOS
        (DNI_USER_ID, DNI_USERNAME, LEGACY_USER_ID, LEGACY_USERNAME, PERSONA_ID, EMPLEADO_ID)
    SELECT u.ID, u.USERNAME, p.USER_ID, u2.USERNAME, p.ID, e.ID
      FROM GESTIONRRHH.USERS u
      JOIN GESTIONRRHH.INDECI_PERSONA p  ON p.DNI = u.USERNAME
      JOIN GESTIONRRHH.INDECI_EMPLEADO e ON e.PERSONA_ID = p.ID
      LEFT JOIN GESTIONRRHH.USERS u2     ON u2.ID = p.USER_ID
     WHERE REGEXP_LIKE(u.USERNAME, '^[0-9]{8}$')
       AND u.EMPLEADO_ID IS NULL;

    DBMS_OUTPUT.PUT_LINE('1. Candidatos congelados: ' || SQL%ROWCOUNT || ' fila(s).');
    COMMIT;
END;
/

-- ── PASO 2: respaldo de USERS/roles/permisos de TODAS las cuentas involucradas
--    (la de DNI y, si existe, la antigua) — antes de tocar nada ─────────────
DECLARE
    v_filas NUMBER;
    v_total_users NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_filas FROM GESTIONRRHH.BKP_V01247_USERS;
    IF v_filas > 0 THEN
        DBMS_OUTPUT.PUT_LINE('2. Respaldo ya existía — se conserva el original.');
        RETURN;
    END IF;

    INSERT INTO GESTIONRRHH.BKP_V01247_USERS (ID, USERNAME, EMPLEADO_ID, STATUS)
    SELECT u.ID, u.USERNAME, u.EMPLEADO_ID, u.STATUS
      FROM GESTIONRRHH.USERS u
     WHERE u.ID IN (SELECT DNI_USER_ID FROM GESTIONRRHH.BKP_V01247_CANDIDATOS)
        OR u.ID IN (SELECT LEGACY_USER_ID FROM GESTIONRRHH.BKP_V01247_CANDIDATOS
                     WHERE LEGACY_USER_ID IS NOT NULL);

    INSERT INTO GESTIONRRHH.BKP_V01247_PERSONA (ID, DNI, USER_ID)
    SELECT p.ID, p.DNI, p.USER_ID
      FROM GESTIONRRHH.INDECI_PERSONA p
     WHERE p.ID IN (SELECT PERSONA_ID FROM GESTIONRRHH.BKP_V01247_CANDIDATOS);

    INSERT INTO GESTIONRRHH.BKP_V01247_USR_ROL (ID_USER, ID_ROL, SISTEMA)
    SELECT ur.ID_USER, ur.ID_ROL, ur.SISTEMA
      FROM GESTIONRRHH.SS_USUARIO_ROL ur
     WHERE ur.ID_USER IN (SELECT DNI_USER_ID FROM GESTIONRRHH.BKP_V01247_CANDIDATOS)
        OR ur.ID_USER IN (SELECT LEGACY_USER_ID FROM GESTIONRRHH.BKP_V01247_CANDIDATOS
                           WHERE LEGACY_USER_ID IS NOT NULL);

    INSERT INTO GESTIONRRHH.BKP_V01247_USR_PERM (ID_USER, ID_PERMISO)
    SELECT up.ID_USER, up.ID_PERMISO
      FROM GESTIONRRHH.SS_USUARIO_PERMISO up
     WHERE up.ID_USER IN (SELECT DNI_USER_ID FROM GESTIONRRHH.BKP_V01247_CANDIDATOS)
        OR up.ID_USER IN (SELECT LEGACY_USER_ID FROM GESTIONRRHH.BKP_V01247_CANDIDATOS
                           WHERE LEGACY_USER_ID IS NOT NULL);

    INSERT INTO GESTIONRRHH.BKP_V01247_USR_PERM_DENY (ID_USER, ID_PERMISO)
    SELECT upd.ID_USER, upd.ID_PERMISO
      FROM GESTIONRRHH.SS_USUARIO_PERMISO_DENY upd
     WHERE upd.ID_USER IN (SELECT DNI_USER_ID FROM GESTIONRRHH.BKP_V01247_CANDIDATOS)
        OR upd.ID_USER IN (SELECT LEGACY_USER_ID FROM GESTIONRRHH.BKP_V01247_CANDIDATOS
                            WHERE LEGACY_USER_ID IS NOT NULL);

    SELECT COUNT(*) INTO v_total_users FROM GESTIONRRHH.BKP_V01247_USERS;
    DBMS_OUTPUT.PUT_LINE('2. Respaldo tomado: ' || v_total_users || ' cuenta(s).');
    COMMIT;
END;
/

-- ── PASO 3: consolidación por candidato ─────────────────────────────────────
DECLARE
    v_procesados NUMBER := 0;
    v_roles_migrados NUMBER := 0;

    PROCEDURE unir_roles(p_destino NUMBER, p_origen NUMBER) IS
    BEGIN
        INSERT INTO GESTIONRRHH.SS_USUARIO_ROL (ID_USER, ID_ROL, SISTEMA)
        SELECT p_destino, ur.ID_ROL, ur.SISTEMA
          FROM GESTIONRRHH.SS_USUARIO_ROL ur
         WHERE ur.ID_USER = p_origen
           AND NOT EXISTS (
                 SELECT 1 FROM GESTIONRRHH.SS_USUARIO_ROL x
                  WHERE x.ID_USER = p_destino AND x.ID_ROL = ur.ID_ROL
                    AND UPPER(TRIM(x.SISTEMA)) = UPPER(TRIM(ur.SISTEMA)));
        v_roles_migrados := v_roles_migrados + SQL%ROWCOUNT;
    END;

    PROCEDURE unir_permisos(p_destino NUMBER, p_origen NUMBER) IS
    BEGIN
        INSERT INTO GESTIONRRHH.SS_USUARIO_PERMISO (ID_USER, ID_PERMISO)
        SELECT p_destino, up.ID_PERMISO
          FROM GESTIONRRHH.SS_USUARIO_PERMISO up
         WHERE up.ID_USER = p_origen
           AND NOT EXISTS (
                 SELECT 1 FROM GESTIONRRHH.SS_USUARIO_PERMISO x
                  WHERE x.ID_USER = p_destino AND x.ID_PERMISO = up.ID_PERMISO);
    END;

    PROCEDURE unir_denies(p_destino NUMBER, p_origen NUMBER) IS
    BEGIN
        INSERT INTO GESTIONRRHH.SS_USUARIO_PERMISO_DENY (ID_USER, ID_PERMISO)
        SELECT p_destino, upd.ID_PERMISO
          FROM GESTIONRRHH.SS_USUARIO_PERMISO_DENY upd
         WHERE upd.ID_USER = p_origen
           AND NOT EXISTS (
                 SELECT 1 FROM GESTIONRRHH.SS_USUARIO_PERMISO_DENY x
                  WHERE x.ID_USER = p_destino AND x.ID_PERMISO = upd.ID_PERMISO);
    END;

BEGIN
    FOR c IN (
        SELECT * FROM GESTIONRRHH.BKP_V01247_CANDIDATOS WHERE PROCESADO = 'N'
    ) LOOP
        IF c.LEGACY_USER_ID IS NOT NULL THEN
            -- Nadie pierde una capacidad que ya tenía en la cuenta antigua.
            unir_roles(c.DNI_USER_ID, c.LEGACY_USER_ID);
            unir_permisos(c.DNI_USER_ID, c.LEGACY_USER_ID);
            unir_denies(c.DNI_USER_ID, c.LEGACY_USER_ID);

            -- Limpia EMPLEADO_ID de la antigua ANTES de asignarlo a la nueva
            -- (defensivo: evita choque si hubiera unicidad a nivel BD).
            UPDATE GESTIONRRHH.USERS SET EMPLEADO_ID = NULL WHERE ID = c.LEGACY_USER_ID;
            UPDATE GESTIONRRHH.USERS SET STATUS = 'INACTIVE'
             WHERE ID = c.LEGACY_USER_ID AND STATUS != 'INACTIVE';
        END IF;

        UPDATE GESTIONRRHH.USERS SET EMPLEADO_ID = c.EMPLEADO_ID WHERE ID = c.DNI_USER_ID;
        UPDATE GESTIONRRHH.INDECI_PERSONA SET USER_ID = c.DNI_USER_ID WHERE ID = c.PERSONA_ID;

        UPDATE GESTIONRRHH.BKP_V01247_CANDIDATOS SET PROCESADO = 'S'
         WHERE DNI_USER_ID = c.DNI_USER_ID;

        v_procesados := v_procesados + 1;
    END LOOP;

    COMMIT;
    DBMS_OUTPUT.PUT_LINE('3. Consolidados: ' || v_procesados || ' empleado(s). '
        || 'Roles migrados desde cuentas antiguas: ' || v_roles_migrados || '.');
END;
/

-- ── Verificación posterior (no modifica nada) ───────────────────────────────
SET LINESIZE 200
SET PAGESIZE 100

PROMPT
PROMPT === Pendientes vs corregidos ===
SELECT
    (SELECT COUNT(*) FROM GESTIONRRHH.BKP_V01247_CANDIDATOS WHERE PROCESADO = 'S') AS corregidos,
    (SELECT COUNT(*)
       FROM GESTIONRRHH.USERS u
       JOIN GESTIONRRHH.INDECI_PERSONA p  ON p.DNI = u.USERNAME
       JOIN GESTIONRRHH.INDECI_EMPLEADO e ON e.PERSONA_ID = p.ID
      WHERE REGEXP_LIKE(u.USERNAME, '^[0-9]{8}$')
        AND u.EMPLEADO_ID IS NULL) AS aun_pendientes_con_empleado
  FROM DUAL;

PROMPT
PROMPT === Cuentas-DNI SIN ningún Empleado detrás — fuera de este script, revisar aparte ===
COLUMN username FORMAT A20
SELECT u.ID, u.USERNAME
  FROM GESTIONRRHH.USERS u
  JOIN GESTIONRRHH.INDECI_PERSONA p ON p.DNI = u.USERNAME
 WHERE REGEXP_LIKE(u.USERNAME, '^[0-9]{8}$')
   AND u.EMPLEADO_ID IS NULL
   AND NOT EXISTS (SELECT 1 FROM GESTIONRRHH.INDECI_EMPLEADO e WHERE e.PERSONA_ID = p.ID)
 ORDER BY u.USERNAME;

PROMPT
PROMPT === Cuentas-DNI cuyo USERNAME ni siquiera tiene una Persona registrada ===
SELECT u.ID, u.USERNAME
  FROM GESTIONRRHH.USERS u
 WHERE REGEXP_LIKE(u.USERNAME, '^[0-9]{8}$')
   AND u.EMPLEADO_ID IS NULL
   AND NOT EXISTS (SELECT 1 FROM GESTIONRRHH.INDECI_PERSONA p WHERE p.DNI = u.USERNAME)
 ORDER BY u.USERNAME;

PROMPT V012_47 — consolidación de login por DNI aplicada.
