-- ============================================================================
-- V012_14 — Permisos RBAC del módulo Liquidación de CTS Trunca (feature 016).
--
-- Registra los 3 permisos granulares en SS_PERMISO y los asigna a los roles
-- operativos en SS_ROL_PERMISO:
--   PLA_CTS_READ    → lectura del módulo, búsqueda de cesantes, ver snapshot.
--   PLA_CTS_WRITE   → calcular/precargar liquidaciones (PENDIENTE/CALCULADO).
--   PLA_CTS_APPROVE → aprobar/sellar/cerrar (transición a estado inmutable).
--
-- Réplica EXACTA del patrón de V010_64 (fix PLA_WRITE), que resolvió DOS trampas:
--   1. CK_SS_PERMISO_TIPO rechaza literales de TIPO no contemplados (rechazó 'PLA').
--      → Se LEE el TIPO real de un permiso PLA_* ya existente (PLA_READ) y se
--        reutiliza, en lugar de adivinar el valor permitido por el CHECK.
--   2. DBMS_OUTPUT.PUT_LINE fuera de un bloque PL/SQL da "comando desconocido".
--      → Todo va dentro de un único bloque PL/SQL.
--
-- Idempotente. Ejecutar en GESTIONRRHH / Oracle 19c+.
-- ============================================================================

SET SERVEROUTPUT ON;

DECLARE
    v_tipo    GESTIONRRHH.SS_PERMISO.TIPO%TYPE;
    v_id_perm GESTIONRRHH.SS_PERMISO.ID_PERMISO%TYPE;
    v_count   NUMBER;
    v_filas   NUMBER;

    -- Inserta el permiso si no existe; siempre devuelve su ID.
    PROCEDURE upsert_permiso(
        p_codigo      VARCHAR2,
        p_descripcion VARCHAR2,
        p_tipo        VARCHAR2,
        p_id          OUT GESTIONRRHH.SS_PERMISO.ID_PERMISO%TYPE
    ) IS
        l_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO l_count
          FROM GESTIONRRHH.SS_PERMISO
         WHERE UPPER(CODIGO) = UPPER(p_codigo);

        IF l_count = 0 THEN
            INSERT INTO GESTIONRRHH.SS_PERMISO (CODIGO, DESCRIPCION, TIPO, ACTIVO, ORDEN, DESPLEGABLE)
            VALUES (p_codigo, p_descripcion, p_tipo, 'S', 0, 'N');
            DBMS_OUTPUT.PUT_LINE(p_codigo || ' -> insertado en SS_PERMISO.');
        ELSE
            DBMS_OUTPUT.PUT_LINE(p_codigo || ' -> ya existe en SS_PERMISO. Sin cambios.');
        END IF;

        SELECT ID_PERMISO INTO p_id
          FROM GESTIONRRHH.SS_PERMISO
         WHERE UPPER(CODIGO) = UPPER(p_codigo);
    END;

    -- Asigna un permiso (por CODIGO) al conjunto de roles indicado; cuenta inserts.
    PROCEDURE asignar_a_roles(p_codigo_permiso VARCHAR2, p_roles SYS.ODCIVARCHAR2LIST) IS
        l_count NUMBER;
    BEGIN
        FOR rec IN (
            SELECT r.ID_ROL, p.ID_PERMISO
              FROM GESTIONRRHH.SS_ROL r
              CROSS JOIN GESTIONRRHH.SS_PERMISO p
             WHERE UPPER(p.CODIGO) = UPPER(p_codigo_permiso)
               AND UPPER(r.CODIGO) IN (
                       SELECT UPPER(COLUMN_VALUE) FROM TABLE(p_roles)
                   )
        ) LOOP
            SELECT COUNT(*) INTO l_count
              FROM GESTIONRRHH.SS_ROL_PERMISO
             WHERE ID_ROL = rec.ID_ROL AND ID_PERMISO = rec.ID_PERMISO;

            IF l_count = 0 THEN
                INSERT INTO GESTIONRRHH.SS_ROL_PERMISO (ID_ROL, ID_PERMISO)
                VALUES (rec.ID_ROL, rec.ID_PERMISO);
                v_filas := v_filas + 1;
            END IF;
        END LOOP;
    END;

BEGIN
    -- ── 1. TIPO real de un PLA_* existente (evita adivinar el CK) ────────────
    BEGIN
        SELECT TIPO INTO v_tipo
          FROM GESTIONRRHH.SS_PERMISO
         WHERE UPPER(CODIGO) = 'PLA_READ';
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            BEGIN
                SELECT TIPO INTO v_tipo
                  FROM GESTIONRRHH.SS_PERMISO
                 WHERE UPPER(CODIGO) = 'PLA_WRITE';
            EXCEPTION
                WHEN NO_DATA_FOUND THEN v_tipo := NULL;
            END;
    END;
    DBMS_OUTPUT.PUT_LINE('TIPO reutilizado de PLA_*: ' || NVL(v_tipo, 'NULL'));

    -- ── 2. Insertar los 3 permisos PLA_CTS_* (si faltan) ────────────────────
    upsert_permiso('PLA_CTS_READ',    'CTS Liquidación - lectura (buscar cesantes, ver snapshot)', v_tipo, v_id_perm);
    upsert_permiso('PLA_CTS_WRITE',   'CTS Liquidación - cálculo/precarga (PENDIENTE/CALCULADO)',   v_tipo, v_id_perm);
    upsert_permiso('PLA_CTS_APPROVE', 'CTS Liquidación - aprobación y cierre (estado inmutable)',   v_tipo, v_id_perm);

    -- ── 3. Asignaciones a roles (mínimo privilegio) ─────────────────────────
    v_filas := 0;

    -- READ + WRITE → analistas y administración.
    asignar_a_roles('PLA_CTS_READ',  SYS.ODCIVARCHAR2LIST(
        'SUPER_ADMIN','ADMIN_TI','ADMIN','RRHH_JEFE','RRHH_ADMIN',
        'RRHH_ANALISTA','PLANILLA_ANALISTA','PLANILLA_APROBADOR'));
    asignar_a_roles('PLA_CTS_WRITE', SYS.ODCIVARCHAR2LIST(
        'SUPER_ADMIN','ADMIN_TI','ADMIN','RRHH_JEFE','RRHH_ADMIN',
        'RRHH_ANALISTA','PLANILLA_ANALISTA','PLANILLA_APROBADOR'));

    -- APPROVE → SOLO jefatura/dirección/aprobador.
    asignar_a_roles('PLA_CTS_APPROVE', SYS.ODCIVARCHAR2LIST(
        'SUPER_ADMIN','ADMIN_TI','ADMIN','RRHH_JEFE','PLANILLA_APROBADOR'));

    DBMS_OUTPUT.PUT_LINE('SS_ROL_PERMISO: ' || v_filas || ' fila(s) insertada(s) para PLA_CTS_*.');

    COMMIT;
    DBMS_OUTPUT.PUT_LINE('V012_14 finalizado correctamente.');
END;
/
