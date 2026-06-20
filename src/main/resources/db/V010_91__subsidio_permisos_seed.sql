-- ============================================================================
-- V010_91 subsidio_permisos_seed (P0-F1)
-- Permisos SUB_* en SS_PERMISO + asignación a roles. No borra nada (idempotente).
--
-- Por qué NO se usa un literal en TIPO:
--   CK_SS_PERMISO_TIPO rechaza valores no contemplados (igual que rechazó 'PLA'
--   en V010_63 -> ORA-02290). Solución establecida en V010_64: leer el TIPO real
--   de un permiso existente (PLA_READ) y reutilizarlo, evitando adivinar el CK.
--   Todo dentro de un bloque PL/SQL para que DBMS_OUTPUT funcione.
--
-- Idempotente. Ejecutar en GESTIONRRHH / Oracle 19c+.
-- ============================================================================
SET SERVEROUTPUT ON;
SET DEFINE OFF;

DECLARE
    v_tipo    GESTIONRRHH.SS_PERMISO.TIPO%TYPE;
    v_count   NUMBER;
    v_filas   NUMBER := 0;

    PROCEDURE upsert_permiso(p_codigo VARCHAR2, p_descripcion VARCHAR2, p_tipo VARCHAR2) IS
    BEGIN
        SELECT COUNT(*) INTO v_count
          FROM GESTIONRRHH.SS_PERMISO
         WHERE UPPER(CODIGO) = UPPER(p_codigo);

        IF v_count = 0 THEN
            INSERT INTO GESTIONRRHH.SS_PERMISO (CODIGO, DESCRIPCION, TIPO, ACTIVO, ORDEN, DESPLEGABLE)
            VALUES (p_codigo, p_descripcion, p_tipo, 'S', 0, 'N');
            DBMS_OUTPUT.PUT_LINE(p_codigo || ' -> insertado.');
        ELSE
            UPDATE GESTIONRRHH.SS_PERMISO
               SET DESCRIPCION = p_descripcion, ACTIVO = 'S'
             WHERE UPPER(CODIGO) = UPPER(p_codigo);
            DBMS_OUTPUT.PUT_LINE(p_codigo || ' -> ya existe (actualizado).');
        END IF;
    END;
BEGIN
    -- ── TIPO válido: tomar el de un permiso existente (PLA_READ; fallback: cualquiera) ──
    BEGIN
        SELECT TIPO INTO v_tipo
          FROM GESTIONRRHH.SS_PERMISO
         WHERE UPPER(CODIGO) = 'PLA_READ';
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            BEGIN
                SELECT TIPO INTO v_tipo
                  FROM (SELECT TIPO FROM GESTIONRRHH.SS_PERMISO WHERE TIPO IS NOT NULL ORDER BY ID_PERMISO)
                 WHERE ROWNUM = 1;
            EXCEPTION
                WHEN NO_DATA_FOUND THEN v_tipo := NULL;
            END;
    END;
    DBMS_OUTPUT.PUT_LINE('TIPO reutilizado para permisos SUB_*: ' || NVL(v_tipo, 'NULL'));

    -- ── 1. Upsert de los 10 permisos SUB_* ──────────────────────────────────
    upsert_permiso('SUB_READ',           'Subsidios — lectura',                        v_tipo);
    upsert_permiso('SUB_WRITE',          'Subsidios — registro y edición',             v_tipo);
    upsert_permiso('SUB_VALIDATE',       'Subsidios — validación',                     v_tipo);
    upsert_permiso('SUB_CALCULATE',      'Subsidios — cálculo',                        v_tipo);
    upsert_permiso('SUB_APPLY_PLANILLA', 'Subsidios — aplicar a planilla',             v_tipo);
    upsert_permiso('SUB_ESSALUD',        'Subsidios — trámite EsSalud',                v_tipo);
    upsert_permiso('SUB_ADJUST',         'Subsidios — ajustes post-aplicación',        v_tipo);
    upsert_permiso('SUB_ADMIN_CONFIG',   'Subsidios — administración y configuración', v_tipo);
    upsert_permiso('SUB_SIMULATE',       'Subsidios — simulación',                     v_tipo);
    upsert_permiso('SUB_EXPORT',         'Subsidios — exportación auditoría',          v_tipo);

    -- ── 2. Asignar SUB_* a roles (idempotente, no borra otros permisos) ──────
    FOR rec IN (
        SELECT r.ID_ROL, p.ID_PERMISO
          FROM GESTIONRRHH.SS_ROL r
          CROSS JOIN GESTIONRRHH.SS_PERMISO p
         WHERE UPPER(r.CODIGO) IN ('SUPER_ADMIN', 'RRHH_ADMIN', 'RRHH_JEFE')
           AND UPPER(p.CODIGO) LIKE 'SUB\_%' ESCAPE '\'
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

    DBMS_OUTPUT.PUT_LINE('SS_ROL_PERMISO: ' || v_filas || ' asignación(es) SUB_* nueva(s).');

    COMMIT;
    DBMS_OUTPUT.PUT_LINE('V010_91 permisos SUB_* sincronizados.');
END;
/
