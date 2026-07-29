-- ============================================================================
-- V012_45 — Reestructuración RBAC: PLANILLA / VINCULACION / ASISTENCIA
--
-- Objetivo (aprobado por dirección):
--   · PLANILLA    → único rol funcional con acceso a TODO el módulo de planilla
--                   (incluida gestión de asistencia y subsidios) + Reportes.
--   · VINCULACION → único rol de Módulo Vinculación + Legajo Personal.
--   · ASISTENCIA  → solo carga/corrección de asistencia + CONSULTA de períodos.
--   · RRHH_ADMIN  → se reduce a Catálogos (lectura+escritura) y Reportes.
--   · SUPER_ADMIN → sin cambios (bypass técnico vía ROLE_SUPER_ADMIN).
--
-- Se retiran 5 roles: PLANILLA_ANALISTA, PLANILLA_APROBADOR, RRHH_JEFE,
-- RRHH_ANALISTA, RRHH_CONSULTA (soft delete: ACTIVO='N', se preserva la fila
-- para no romper el historial de auditoría — D.L. 1451).
--
-- PASO 0 — SNAPSHOT: antes de tocar nada, copia el estado actual de los roles
-- afectados a tres tablas BKP_V01245_*. El rollback restaura DESDE ahí, sin
-- depender de lo que digan los seeds históricos (V001 no refleja la BD real).
--
-- Permisos nuevos (TIPO='OPCION', exigido por CK_SS_PERMISO_TIPO):
--   ASI_READ / ASI_WRITE → asistencia como familia propia, para poder delegar
--       la carga sin conceder acceso a planilla (antes colgaba de PLA_WRITE).
--   PER_READ             → consulta de períodos sin PLA_READ.
--   PLA_LBS_READ/WRITE   → faltaban en el catálogo pese a estar referenciados
--       por LbsController; sin ellos ese módulo solo respondía a SUPER_ADMIN.
--
-- Reportes usa REP_READ / REP_EXPORT (códigos reales del catálogo). Las
-- constantes Java RPT_READ / RPT_WRITE no existían en SS_PERMISO y se
-- renombraron en el backend en este mismo pase.
--
-- NO toca USERS.ROLE_ID: es una FK legacy hacia GESTIONRRHH.ROLES, tabla ajena
-- a SS_ROL (lección de V012_43 → ORA-02291).
--
-- Idempotente. Ejecutar en GESTIONRRHH.
-- ============================================================================

SET SERVEROUTPUT ON;
SET DEFINE OFF;

-- ── PASO 0a: tablas de respaldo (se crean una sola vez) ─────────────────────
-- El DEFAULT_TABLESPACE del usuario puede apuntar a uno inexistente en entornos
-- clonados (ORA-00959: tablespace 'TBS_RRHH' no existe), así que el tablespace
-- se hereda del que ya usa SS_ROL en lugar de confiar en el del esquema.
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
          FROM all_tables
         WHERE owner = 'GESTIONRRHH' AND table_name = 'SS_ROL';
    EXCEPTION
        WHEN NO_DATA_FOUND THEN v_tbs := NULL;
        WHEN TOO_MANY_ROWS THEN v_tbs := NULL;
    END;

    IF v_tbs IS NOT NULL THEN
        v_clause := ' TABLESPACE ' || v_tbs;
        DBMS_OUTPUT.PUT_LINE('0a. Tablespace heredado de SS_ROL: ' || v_tbs);
    ELSE
        DBMS_OUTPUT.PUT_LINE('0a. Sin tablespace explícito — se usará el del esquema.');
    END IF;

    crear('CREATE TABLE GESTIONRRHH.BKP_V01245_ROL
             (ID_ROL NUMBER, CODIGO VARCHAR2(100), NOMBRE VARCHAR2(200), ACTIVO VARCHAR2(5))'
          || v_clause);
    crear('CREATE TABLE GESTIONRRHH.BKP_V01245_ROL_PERM
             (ID_ROL NUMBER, ID_PERMISO NUMBER)' || v_clause);
    crear('CREATE TABLE GESTIONRRHH.BKP_V01245_USR_ROL
             (ID_USER NUMBER, ID_ROL NUMBER, SISTEMA VARCHAR2(50))' || v_clause);

    DBMS_OUTPUT.PUT_LINE('0a. Tablas de respaldo listas.');
END;
/

DECLARE
    v_filas     NUMBER := 0;
    v_bkp       NUMBER;

    PROCEDURE upsert_permiso(p_codigo VARCHAR2, p_desc VARCHAR2) IS
    BEGIN
        MERGE INTO GESTIONRRHH.SS_PERMISO d
        USING (SELECT p_codigo AS CODIGO, p_desc AS DESCRIPCION FROM DUAL) s
           ON (UPPER(d.CODIGO) = UPPER(s.CODIGO))
        WHEN MATCHED THEN
            UPDATE SET d.DESCRIPCION = s.DESCRIPCION, d.ACTIVO = 'S'
        WHEN NOT MATCHED THEN
            INSERT (CODIGO, DESCRIPCION, TIPO, ACTIVO, ORDEN, DESPLEGABLE)
            VALUES (s.CODIGO, s.DESCRIPCION, 'OPCION', 'S', 0, 'N');
    END;

    PROCEDURE upsert_rol(p_codigo VARCHAR2, p_nombre VARCHAR2, p_nivel NUMBER) IS
    BEGIN
        MERGE INTO GESTIONRRHH.SS_ROL d
        USING (SELECT p_codigo AS CODIGO, p_nombre AS NOMBRE, p_nivel AS NIVEL FROM DUAL) s
           ON (UPPER(d.CODIGO) = UPPER(s.CODIGO))
        WHEN MATCHED THEN
            UPDATE SET d.NOMBRE = s.NOMBRE, d.ACTIVO = 'S', d.NIVEL = s.NIVEL
        WHEN NOT MATCHED THEN
            INSERT (CODIGO, NOMBRE, ACTIVO, NIVEL)
            VALUES (s.CODIGO, s.NOMBRE, 'S', s.NIVEL);
    END;

    -- Asigna un permiso a un rol. Avisa si el permiso no existe en el catálogo:
    -- sin esto, un código equivocado no inserta nada y el rol queda corto en
    -- silencio (exactamente lo que ocurría con RPT_READ / RPT_WRITE).
    PROCEDURE asignar(p_rol VARCHAR2, p_permiso VARCHAR2) IS
        l_existe NUMBER;
    BEGIN
        SELECT COUNT(*) INTO l_existe
          FROM GESTIONRRHH.SS_PERMISO WHERE UPPER(CODIGO) = UPPER(p_permiso);
        IF l_existe = 0 THEN
            DBMS_OUTPUT.PUT_LINE('  !! AVISO: permiso ' || p_permiso
                || ' no existe en SS_PERMISO — ' || p_rol || ' quedará sin él.');
            RETURN;
        END IF;

        INSERT INTO GESTIONRRHH.SS_ROL_PERMISO (ID_ROL, ID_PERMISO)
        SELECT r.ID_ROL, p.ID_PERMISO
          FROM GESTIONRRHH.SS_ROL r
          CROSS JOIN GESTIONRRHH.SS_PERMISO p
         WHERE UPPER(r.CODIGO) = UPPER(p_rol)
           AND UPPER(p.CODIGO) = UPPER(p_permiso)
           AND NOT EXISTS (
                 SELECT 1 FROM GESTIONRRHH.SS_ROL_PERMISO rp
                  WHERE rp.ID_ROL = r.ID_ROL AND rp.ID_PERMISO = p.ID_PERMISO);
        v_filas := v_filas + SQL%ROWCOUNT;
    END;

    PROCEDURE quitar(p_rol VARCHAR2, p_permiso VARCHAR2) IS
    BEGIN
        DELETE FROM GESTIONRRHH.SS_ROL_PERMISO
         WHERE ID_ROL     IN (SELECT ID_ROL     FROM GESTIONRRHH.SS_ROL     WHERE UPPER(CODIGO) = UPPER(p_rol))
           AND ID_PERMISO IN (SELECT ID_PERMISO FROM GESTIONRRHH.SS_PERMISO WHERE UPPER(CODIGO) = UPPER(p_permiso));
    END;

    PROCEDURE asignar_rol_usuario(p_id_user NUMBER, p_rol VARCHAR2) IS
    BEGIN
        INSERT INTO GESTIONRRHH.SS_USUARIO_ROL (ID_USER, ID_ROL, SISTEMA)
        SELECT p_id_user, r.ID_ROL, 'SISRH'
          FROM GESTIONRRHH.SS_ROL r
         WHERE UPPER(r.CODIGO) = UPPER(p_rol)
           AND NOT EXISTS (
                 SELECT 1 FROM GESTIONRRHH.SS_USUARIO_ROL ur
                  WHERE ur.ID_USER = p_id_user AND ur.ID_ROL = r.ID_ROL
                    AND UPPER(TRIM(ur.SISTEMA)) = 'SISRH');
    END;

    PROCEDURE retirar_rol(p_rol VARCHAR2) IS
    BEGIN
        DELETE FROM GESTIONRRHH.SS_USUARIO_ROL
         WHERE ID_ROL IN (SELECT ID_ROL FROM GESTIONRRHH.SS_ROL WHERE UPPER(CODIGO) = UPPER(p_rol))
           AND UPPER(TRIM(SISTEMA)) = 'SISRH';

        DELETE FROM GESTIONRRHH.SS_ROL_PERMISO
         WHERE ID_ROL IN (SELECT ID_ROL FROM GESTIONRRHH.SS_ROL WHERE UPPER(CODIGO) = UPPER(p_rol));

        UPDATE GESTIONRRHH.SS_ROL
           SET ACTIVO = 'N',
               NOMBRE = SUBSTR(NOMBRE || ' (retirado V012_45)', 1, 200)
         WHERE UPPER(CODIGO) = UPPER(p_rol) AND ACTIVO <> 'N';

        DBMS_OUTPUT.PUT_LINE('  Rol retirado: ' || p_rol);
    END;

BEGIN
    -- ── PASO 0b: snapshot (solo la primera vez) ─────────────────────────────
    SELECT COUNT(*) INTO v_bkp FROM GESTIONRRHH.BKP_V01245_ROL;
    IF v_bkp = 0 THEN
        INSERT INTO GESTIONRRHH.BKP_V01245_ROL (ID_ROL, CODIGO, NOMBRE, ACTIVO)
        SELECT ID_ROL, CODIGO, NOMBRE, ACTIVO FROM GESTIONRRHH.SS_ROL
         WHERE UPPER(CODIGO) IN ('PLANILLA_ANALISTA','PLANILLA_APROBADOR','RRHH_JEFE',
                                 'RRHH_ANALISTA','RRHH_CONSULTA','RRHH_ADMIN');

        INSERT INTO GESTIONRRHH.BKP_V01245_ROL_PERM (ID_ROL, ID_PERMISO)
        SELECT rp.ID_ROL, rp.ID_PERMISO
          FROM GESTIONRRHH.SS_ROL_PERMISO rp
          JOIN GESTIONRRHH.SS_ROL r ON r.ID_ROL = rp.ID_ROL
         WHERE UPPER(r.CODIGO) IN ('PLANILLA_ANALISTA','PLANILLA_APROBADOR','RRHH_JEFE',
                                   'RRHH_ANALISTA','RRHH_CONSULTA','RRHH_ADMIN');

        INSERT INTO GESTIONRRHH.BKP_V01245_USR_ROL (ID_USER, ID_ROL, SISTEMA)
        SELECT ur.ID_USER, ur.ID_ROL, ur.SISTEMA
          FROM GESTIONRRHH.SS_USUARIO_ROL ur
          JOIN GESTIONRRHH.SS_ROL r ON r.ID_ROL = ur.ID_ROL
         WHERE UPPER(r.CODIGO) IN ('PLANILLA_ANALISTA','PLANILLA_APROBADOR','RRHH_JEFE',
                                   'RRHH_ANALISTA','RRHH_CONSULTA','RRHH_ADMIN')
           AND UPPER(TRIM(ur.SISTEMA)) = 'SISRH';

        DBMS_OUTPUT.PUT_LINE('0b. Snapshot del estado previo tomado.');
    ELSE
        DBMS_OUTPUT.PUT_LINE('0b. Snapshot ya existía — se conserva el original.');
    END IF;

    -- ── 1. Permisos nuevos (TIPO='OPCION' por CK_SS_PERMISO_TIPO) ───────────
    upsert_permiso('ASI_READ',      'Asistencia — consulta de cualquier empleado');
    upsert_permiso('ASI_WRITE',     'Asistencia — importar, editar y recalcular');
    upsert_permiso('PER_READ',      'Períodos de planilla — solo lectura');
    upsert_permiso('PLA_LBS_READ',  'Liquidación de Beneficios Sociales — lectura');
    upsert_permiso('PLA_LBS_WRITE', 'Liquidación de Beneficios Sociales — proceso');
    DBMS_OUTPUT.PUT_LINE('1. Permisos ASI_*, PER_READ y PLA_LBS_* sincronizados.');

    -- ── 2. Roles nuevos ─────────────────────────────────────────────────────
    upsert_rol('PLANILLA',    'Planilla — operación y aprobación', 50);
    upsert_rol('VINCULACION', 'Vinculación y Legajo',              45);
    upsert_rol('ASISTENCIA',  'Asistencia — carga y corrección',   55);
    DBMS_OUTPUT.PUT_LINE('2. Roles PLANILLA / VINCULACION / ASISTENCIA sincronizados.');

    -- ── 3. Permisos de cada rol nuevo ───────────────────────────────────────
    -- PLANILLA: planilla + asistencia + subsidios + CTS/LBS + reportes.
    asignar('PLANILLA','PLA_READ');        asignar('PLANILLA','PLA_WRITE');
    asignar('PLANILLA','PLA_APPROVE');     asignar('PLANILLA','ASI_READ');
    asignar('PLANILLA','ASI_WRITE');       asignar('PLANILLA','PER_READ');
    asignar('PLANILLA','CAT_READ');        asignar('PLANILLA','EMP_READ');
    asignar('PLANILLA','REP_READ');        asignar('PLANILLA','REP_EXPORT');
    asignar('PLANILLA','PLA_CTS_READ');    asignar('PLANILLA','PLA_CTS_WRITE');
    asignar('PLANILLA','PLA_CTS_APPROVE');
    asignar('PLANILLA','PLA_LBS_READ');    asignar('PLANILLA','PLA_LBS_WRITE');
    asignar('PLANILLA','SUB_READ');        asignar('PLANILLA','SUB_WRITE');
    asignar('PLANILLA','SUB_VALIDATE');    asignar('PLANILLA','SUB_CALCULATE');
    asignar('PLANILLA','SUB_APPLY_PLANILLA'); asignar('PLANILLA','SUB_ESSALUD');
    asignar('PLANILLA','SUB_ADJUST');      asignar('PLANILLA','SUB_ADMIN_CONFIG');
    asignar('PLANILLA','SUB_SIMULATE');    asignar('PLANILLA','SUB_EXPORT');
    -- Operativos del catálogo aún no cableados en código, pero del dominio planilla.
    asignar('PLANILLA','PLA_GENERAR');     asignar('PLANILLA','PLA_CIERRE');
    asignar('PLANILLA','PLA_APROBAR');     asignar('PLANILLA','PLA_ABONOS');
    asignar('PLANILLA','PLA_AIRHSP');      asignar('PLANILLA','PLA_MCPP');
    asignar('PLANILLA','PLA_PRESUPUESTO'); asignar('PLANILLA','PLA_ASISTENCIA');

    -- VINCULACION: empleados y legajo (ambos usan EMP_*) + catálogos en lectura.
    asignar('VINCULACION','EMP_READ');
    asignar('VINCULACION','EMP_WRITE');
    asignar('VINCULACION','CAT_READ');

    -- ASISTENCIA: sin ningún PLA_* de escritura; períodos en solo lectura.
    asignar('ASISTENCIA','ASI_READ');
    asignar('ASISTENCIA','ASI_WRITE');
    asignar('ASISTENCIA','PER_READ');

    DBMS_OUTPUT.PUT_LINE('3. SS_ROL_PERMISO: ' || v_filas || ' fila(s) insertada(s).');

    -- ── 4. RRHH_ADMIN se reduce a Catálogos + Reportes ──────────────────────
    quitar('RRHH_ADMIN','PLA_READ');        quitar('RRHH_ADMIN','PLA_WRITE');
    quitar('RRHH_ADMIN','PLA_APPROVE');     quitar('RRHH_ADMIN','EMP_READ');
    quitar('RRHH_ADMIN','EMP_WRITE');       quitar('RRHH_ADMIN','PLA_CTS_READ');
    quitar('RRHH_ADMIN','PLA_CTS_WRITE');   quitar('RRHH_ADMIN','PLA_CTS_APPROVE');
    quitar('RRHH_ADMIN','SUB_READ');        quitar('RRHH_ADMIN','SUB_WRITE');
    quitar('RRHH_ADMIN','SUB_VALIDATE');    quitar('RRHH_ADMIN','SUB_CALCULATE');
    quitar('RRHH_ADMIN','SUB_APPLY_PLANILLA'); quitar('RRHH_ADMIN','SUB_ESSALUD');
    quitar('RRHH_ADMIN','SUB_ADJUST');      quitar('RRHH_ADMIN','SUB_ADMIN_CONFIG');
    quitar('RRHH_ADMIN','SUB_SIMULATE');    quitar('RRHH_ADMIN','SUB_EXPORT');
    asignar('RRHH_ADMIN','CAT_READ');       asignar('RRHH_ADMIN','CAT_WRITE');
    asignar('RRHH_ADMIN','REP_READ');       asignar('RRHH_ADMIN','REP_EXPORT');
    UPDATE GESTIONRRHH.SS_ROL
       SET NOMBRE = 'Operador RRHH — Catálogos y Reportes'
     WHERE UPPER(CODIGO) = 'RRHH_ADMIN';
    DBMS_OUTPUT.PUT_LINE('4. RRHH_ADMIN reducido a CAT_* + REP_*.');

    -- ── 5. Migración de usuarios ────────────────────────────────────────────
    -- Quienes tenían PLANILLA_ANALISTA / PLANILLA_APROBADOR reciben PLANILLA.
    FOR u IN (
        SELECT DISTINCT ur.ID_USER
          FROM GESTIONRRHH.SS_USUARIO_ROL ur
          JOIN GESTIONRRHH.SS_ROL r ON r.ID_ROL = ur.ID_ROL
         WHERE UPPER(r.CODIGO) IN ('PLANILLA_ANALISTA','PLANILLA_APROBADOR')
           AND UPPER(TRIM(ur.SISTEMA)) = 'SISRH'
    ) LOOP
        asignar_rol_usuario(u.ID_USER, 'PLANILLA');
        DBMS_OUTPUT.PUT_LINE('  Usuario ' || u.ID_USER || ' -> PLANILLA');
    END LOOP;

    -- 71655915 es el operador de asistencia: conserva EMPLEADO y RRHH_PAPELETA;
    -- pierde RRHH_ADMIN, RRHH_ANALISTA (retirado) y GESTOR_USUARIOS.
    FOR u IN (SELECT ID FROM GESTIONRRHH.USERS WHERE USERNAME = '71655915') LOOP
        asignar_rol_usuario(u.ID, 'ASISTENCIA');
        DELETE FROM GESTIONRRHH.SS_USUARIO_ROL
         WHERE ID_USER = u.ID
           AND UPPER(TRIM(SISTEMA)) = 'SISRH'
           AND ID_ROL IN (SELECT ID_ROL FROM GESTIONRRHH.SS_ROL
                           WHERE UPPER(CODIGO) IN ('RRHH_ADMIN','GESTOR_USUARIOS'));
        DBMS_OUTPUT.PUT_LINE('  Usuario ' || u.ID || ' (71655915) -> ASISTENCIA');
    END LOOP;

    -- ── 6. Retirar los 5 roles (soft delete) ────────────────────────────────
    retirar_rol('PLANILLA_ANALISTA');
    retirar_rol('PLANILLA_APROBADOR');
    retirar_rol('RRHH_JEFE');
    retirar_rol('RRHH_ANALISTA');
    retirar_rol('RRHH_CONSULTA');

    COMMIT;
    DBMS_OUTPUT.PUT_LINE('V012_45 finalizado correctamente.');
END;
/

-- ── Verificación posterior (no modifica nada) ───────────────────────────────
SET LINESIZE 250
SET PAGESIZE 100
COLUMN rol      FORMAT A18
COLUMN permisos FORMAT A190
COLUMN username FORMAT A25
COLUMN roles    FORMAT A70

PROMPT
PROMPT === Matriz rol -> permisos resultante ===
SELECT r.CODIGO AS rol, r.ACTIVO,
       LISTAGG(p.CODIGO, ', ') WITHIN GROUP (ORDER BY p.CODIGO) AS permisos
  FROM GESTIONRRHH.SS_ROL r
  LEFT JOIN GESTIONRRHH.SS_ROL_PERMISO rp ON rp.ID_ROL = r.ID_ROL
  LEFT JOIN GESTIONRRHH.SS_PERMISO p      ON p.ID_PERMISO = rp.ID_PERMISO
 WHERE UPPER(r.CODIGO) IN ('SUPER_ADMIN','PLANILLA','VINCULACION','ASISTENCIA',
                           'RRHH_ADMIN','GESTOR_USUARIOS')
 GROUP BY r.CODIGO, r.ACTIVO
 ORDER BY r.CODIGO;

PROMPT
PROMPT === Usuarios con roles operativos ===
SELECT u.ID, u.USERNAME,
       LISTAGG(r.CODIGO, ', ') WITHIN GROUP (ORDER BY r.CODIGO) AS roles
  FROM GESTIONRRHH.SS_USUARIO_ROL ur
  JOIN GESTIONRRHH.SS_ROL r ON r.ID_ROL = ur.ID_ROL
  JOIN GESTIONRRHH.USERS  u ON u.ID     = ur.ID_USER
 WHERE UPPER(TRIM(ur.SISTEMA)) = 'SISRH'
   AND u.ID IN (601, 610, 1326, 1479)
 GROUP BY u.ID, u.USERNAME
 ORDER BY u.ID;

PROMPT V012_45 — reestructuración RBAC aplicada.
