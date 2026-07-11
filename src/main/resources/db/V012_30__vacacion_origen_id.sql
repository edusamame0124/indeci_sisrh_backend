-- ============================================================================
-- Hub Vacacional / Reprogramación segura — VACACION_ORIGEN_ID.
--
-- Reemplaza la dependencia de fechas TIPEADAS por el empleado (anti-patrón:
-- confiar en la memoria del usuario para transacciones de fechas) por una
-- referencia dura al registro INDECI_VACACIONES que se está reprogramando o
-- fraccionando. El frontend selecciona el período de un dropdown (Poka-Yoke)
-- en vez de escribirlo; el backend guarda aquí el id exacto seleccionado.
--
-- Consumido por VacacionService.procesarAprobacionVacaciones: al aprobar un
-- detalle "_ACTUAL" (REPROG_ACTUAL/FRACC_ACTUAL) con VACACION_ORIGEN_ID no
-- nulo, marca el registro INDECI_VACACIONES original como ESTADO='SUSTITUIDO'
-- para que deje de aparecer como disponible en el dropdown.
--
-- Solo DDL (ALTER ADD), idempotente. Oracle 19c+ / GESTIONRRHH.
-- ============================================================================

SET SERVEROUTPUT ON;

DECLARE
    PROCEDURE add_column_if_missing(
        p_table VARCHAR2, p_column VARCHAR2, p_ddl VARCHAR2
    ) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_exists FROM ALL_TAB_COLUMNS
         WHERE OWNER='GESTIONRRHH' AND TABLE_NAME=p_table AND COLUMN_NAME=p_column;
        IF v_exists = 0 THEN
            EXECUTE IMMEDIATE p_ddl;
            DBMS_OUTPUT.PUT_LINE(p_table||'.'||p_column||' -> agregada.');
        ELSE
            DBMS_OUTPUT.PUT_LINE(p_table||'.'||p_column||' ya existe.');
        END IF;
    END;
BEGIN
    add_column_if_missing('INDECI_SOLICITUD_VACACION_DET', 'VACACION_ORIGEN_ID',
        'ALTER TABLE GESTIONRRHH.INDECI_SOLICITUD_VACACION_DET ADD (VACACION_ORIGEN_ID NUMBER)');

    EXECUTE IMMEDIATE 'COMMENT ON COLUMN GESTIONRRHH.INDECI_SOLICITUD_VACACION_DET.VACACION_ORIGEN_ID IS '
        || '''Hub Vacacional — FK referencial a INDECI_VACACIONES.ID. Se llena SOLO en detalles '
        || '_ACTUAL (REPROG_ACTUAL/FRACC_ACTUAL) elegidos del dropdown de periodos programados. '
        || 'Al aprobar, el motor marca ese registro origen como ESTADO=SUSTITUIDO.''';

    DBMS_OUTPUT.PUT_LINE('V012_30 finalizado.');
END;
/
