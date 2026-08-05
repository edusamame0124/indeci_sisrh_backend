-- ============================================================================
-- V012_55 — Trazabilidad de usuario en INDECI_VACACIONES.
--
-- OBJETIVO: el modal "Registro Directo de Goce (Override)" del Padrón Vacacional
-- no dejaba rastro de QUIÉN registró el goce (solo CREATED_AT, autogenerado). Se
-- agrega USUARIO_REGISTRO, mismo patrón que ya tiene INDECI_VACACION_ACUM_DECISION,
-- para que "Ver detalle" e "Historial" puedan mostrarlo sin depender de Administración
-- → Auditoría (pantalla fuera del alcance del rol RR.HH. operativo).
--
-- Forward-only: filas ya existentes quedan con USUARIO_REGISTRO=NULL (se muestra
-- "—" en pantalla); no hay forma de reconstruir el usuario real de goces pasados.
--
-- Idempotente (add_column_if_missing) — mismo patrón que V012_22. Oracle 19c+ / GESTIONRRHH.
-- ============================================================================

SET SERVEROUTPUT ON;

DECLARE
    PROCEDURE add_column_if_missing(
        p_table  VARCHAR2,
        p_column VARCHAR2,
        p_ddl    VARCHAR2
    ) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_exists
          FROM ALL_TAB_COLUMNS
         WHERE OWNER = 'GESTIONRRHH'
           AND TABLE_NAME = p_table
           AND COLUMN_NAME = p_column;
        IF v_exists = 0 THEN
            EXECUTE IMMEDIATE p_ddl;
            DBMS_OUTPUT.PUT_LINE(p_table || '.' || p_column || ' -> agregada.');
        ELSE
            DBMS_OUTPUT.PUT_LINE(p_table || '.' || p_column || ' ya existe. Sin cambios.');
        END IF;
    END;
BEGIN
    add_column_if_missing('INDECI_VACACIONES', 'USUARIO_REGISTRO',
        'ALTER TABLE GESTIONRRHH.INDECI_VACACIONES ADD (USUARIO_REGISTRO VARCHAR2(60 CHAR))');

    EXECUTE IMMEDIATE 'COMMENT ON COLUMN GESTIONRRHH.INDECI_VACACIONES.USUARIO_REGISTRO IS '
        || '''Usuario que registró la fila (ej. Goce Directo/Override). NULL en filas migradas antes de V012_55.''';

    DBMS_OUTPUT.PUT_LINE('V012_55 finalizado.');
END;
/
