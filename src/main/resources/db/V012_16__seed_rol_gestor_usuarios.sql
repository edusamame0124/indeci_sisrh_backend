-- ============================================================================
-- V012_16 — Rol SISRH acotado GESTOR_USUARIOS (solo módulo Administración).
--
-- Propósito: crear un rol interno de SISRH que da acceso ÚNICAMENTE al módulo
-- Administración (Usuarios + Roles/Permisos + Auditoría) y a NADA más del
-- sistema. Se asigna desde el bloque "Asignación de roles" (claim roles[]),
-- NO desde "Accesos por sistema" (claim sistemas.{codigo}).
--
--   ADM_USERS → gestión de usuarios (crear/editar, estado, roles, reset clave).
--   ADM_META  → catálogo de Roles y Permisos.
--   ADM_AUDIT → consulta de auditoría.
--
-- OJO: a diferencia de SUPER_ADMIN (que salta todo vía ROLE_SUPER_ADMIN),
-- este rol NO tiene bypass; sin ADM_META explícito, la pestaña Roles/Permisos
-- devolvería 403. Por eso se asignan los 3 permisos de forma explícita.
--
-- Los 3 permisos ya existen en SS_PERMISO (V001 fase 1). NIVEL=25 ubica el rol
-- entre ADMIN (20) y RRHH_JEFE (30). Idempotente. Ejecutar en GESTIONRRHH.
-- ============================================================================

SET SERVEROUTPUT ON;
SET DEFINE OFF;

DECLARE
    v_filas NUMBER := 0;

    -- Asigna un permiso (por CODIGO) a un rol (por CODIGO) si no existe ya.
    PROCEDURE asignar_permiso(p_codigo_rol VARCHAR2, p_codigo_permiso VARCHAR2) IS
        l_count NUMBER;
    BEGIN
        FOR rec IN (
            SELECT r.ID_ROL, p.ID_PERMISO
              FROM GESTIONRRHH.SS_ROL r
              CROSS JOIN GESTIONRRHH.SS_PERMISO p
             WHERE UPPER(r.CODIGO) = UPPER(p_codigo_rol)
               AND UPPER(p.CODIGO) = UPPER(p_codigo_permiso)
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
    -- ── 1. Crear/actualizar el rol GESTOR_USUARIOS ──────────────────────────
    MERGE INTO GESTIONRRHH.SS_ROL d
    USING (
        SELECT 'GESTOR_USUARIOS' AS CODIGO,
               'Gestor de Usuarios' AS NOMBRE,
               'S' AS ACTIVO,
               25 AS NIVEL
          FROM DUAL
    ) s ON (UPPER(d.CODIGO) = UPPER(s.CODIGO))
    WHEN MATCHED THEN
        UPDATE SET d.NOMBRE = s.NOMBRE, d.ACTIVO = s.ACTIVO, d.NIVEL = s.NIVEL
    WHEN NOT MATCHED THEN
        INSERT (CODIGO, NOMBRE, ACTIVO, NIVEL)
        VALUES (s.CODIGO, s.NOMBRE, s.ACTIVO, s.NIVEL);

    DBMS_OUTPUT.PUT_LINE('SS_ROL GESTOR_USUARIOS sincronizado.');

    -- ── 2. Asignar SOLO los permisos del módulo Administración ──────────────
    asignar_permiso('GESTOR_USUARIOS', 'ADM_USERS');
    asignar_permiso('GESTOR_USUARIOS', 'ADM_META');
    asignar_permiso('GESTOR_USUARIOS', 'ADM_AUDIT');

    DBMS_OUTPUT.PUT_LINE('SS_ROL_PERMISO: ' || v_filas
        || ' fila(s) insertada(s) para GESTOR_USUARIOS.');

    COMMIT;
    DBMS_OUTPUT.PUT_LINE('V012_16 finalizado correctamente.');
END;
/

PROMPT V012_16 — rol GESTOR_USUARIOS (ADM_USERS/ADM_META/ADM_AUDIT) aplicado.
