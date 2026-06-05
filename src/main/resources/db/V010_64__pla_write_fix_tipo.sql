-- ============================================================================
-- V010_64 — Inserta PLA_WRITE/PLA_APPROVE en SS_PERMISO (si faltan) y los
--           asigna a roles RRHH / PLANILLA / ADMIN en SS_ROL_PERMISO.
--
-- V010_63 falló porque:
--   1. CK_SS_PERMISO_TIPO rechazó el literal 'PLA' al insertar PLA_WRITE.
--   2. DBMS_OUTPUT.PUT_LINE fuera de bloque PL/SQL generó "comando desconocido".
--
-- Solución: leer el TIPO real de la fila PLA_READ que ya existe en BD y
-- reutilizarlo para PLA_WRITE y PLA_APPROVE, evitando adivinar el valor del CK.
-- Todo dentro de un bloque PL/SQL para que DBMS_OUTPUT funcione correctamente.
--
-- Idempotente. Ejecutar en GESTIONRRHH / Oracle 19c+.
-- ============================================================================

SET SERVEROUTPUT ON;

DECLARE
    v_tipo       GESTIONRRHH.SS_PERMISO.TIPO%TYPE;
    v_id_perm    GESTIONRRHH.SS_PERMISO.ID_PERMISO%TYPE;
    v_count      NUMBER;
    v_filas      NUMBER;

    -- Inserta el permiso si no existe, devuelve su ID.
    PROCEDURE upsert_permiso(
        p_codigo      VARCHAR2,
        p_descripcion VARCHAR2,
        p_tipo        VARCHAR2,
        p_id          OUT GESTIONRRHH.SS_PERMISO.ID_PERMISO%TYPE
    ) IS
    BEGIN
        SELECT COUNT(*) INTO v_count
          FROM GESTIONRRHH.SS_PERMISO
         WHERE UPPER(CODIGO) = UPPER(p_codigo);

        IF v_count = 0 THEN
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

BEGIN
    -- ── 1. Obtener TIPO real de PLA_READ (ya existe en BD) ──────────────────
    BEGIN
        SELECT TIPO INTO v_tipo
          FROM GESTIONRRHH.SS_PERMISO
         WHERE UPPER(CODIGO) = 'PLA_READ';
        DBMS_OUTPUT.PUT_LINE('TIPO de PLA_READ: ' || NVL(v_tipo, 'NULL'));
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            v_tipo := NULL;
            DBMS_OUTPUT.PUT_LINE('PLA_READ no encontrado, TIPO = NULL');
    END;

    -- ── 2. Insertar PLA_WRITE y PLA_APPROVE si no existen ───────────────────
    upsert_permiso('PLA_WRITE',   'Planilla — procesamiento',       v_tipo, v_id_perm);
    upsert_permiso('PLA_APPROVE', 'Planilla — aprobación y cierre', v_tipo, v_id_perm);

    -- ── 3. Asignar PLA_WRITE a los roles operativos ─────────────────────────
    v_filas := 0;
    FOR rec IN (
        SELECT r.ID_ROL, p.ID_PERMISO
          FROM GESTIONRRHH.SS_ROL r
          CROSS JOIN GESTIONRRHH.SS_PERMISO p
         WHERE UPPER(r.CODIGO) IN (
                   'SUPER_ADMIN', 'ADMIN_TI', 'ADMIN',
                   'RRHH_JEFE',   'RRHH_ADMIN', 'RRHH_ANALISTA',
                   'PLANILLA_ANALISTA', 'PLANILLA_APROBADOR'
               )
           AND UPPER(p.CODIGO) = 'PLA_WRITE'
    ) LOOP
        SELECT COUNT(*) INTO v_count
          FROM GESTIONRRHH.SS_ROL_PERMISO
         WHERE ID_ROL = rec.ID_ROL AND ID_PERMISO = rec.ID_PERMISO;

        IF v_count = 0 THEN
            INSERT INTO GESTIONRRHH.SS_ROL_PERMISO (ID_ROL, ID_PERMISO)
            VALUES (rec.ID_ROL, rec.ID_PERMISO);
            v_filas := v_filas + 1;
        END IF;
    END LOOP;

    DBMS_OUTPUT.PUT_LINE('SS_ROL_PERMISO: ' || v_filas || ' fila(s) insertada(s) para PLA_WRITE.');

    COMMIT;
    DBMS_OUTPUT.PUT_LINE('V010_64 finalizado correctamente.');
END;
/
