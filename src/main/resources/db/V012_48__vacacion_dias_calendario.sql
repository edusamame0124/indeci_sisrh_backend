-- ============================================================================
-- Fix Art. 34 D.S. 013-2019-PCM — separa el conteo HÁBIL (Art. 35.b/c, pool de
-- fraccionamiento) del conteo CALENDARIO (Art. 34) que realmente descuenta el
-- saldo anual de 30 días.
--
-- Hasta ahora, para Fraccionamiento (FRACC_*) el único número que existía era el
-- de días HÁBILES (ej. 4, viernes a miércoles excluyendo fin de semana), y ese
-- mismo número se restaba de INDECI_VACACION_SALDO.DIAS_GOZADOS. Un fin de semana
-- "atrapado" dentro del rango de la fracción consume igual el saldo anual (Art. 34:
-- "el sábado y domingo inmediatos... también se computan dentro de dicho periodo
-- vacacional"), así que debía descontarse el calendario real (6), no el hábil (4).
--
-- DIAS_CALENDARIO queda NULL para registros históricos (forward-only, no se
-- recalcula lo ya aprobado). INDECI_VACACIONES.DIAS sigue siendo HÁBILES para
-- Fraccionamiento (lo usa el pool Art. 35.b — no tocar).
--
-- Idempotente (add_column_if_missing) — mismo patrón que V012_22/V012_33. Oracle
-- 19c+ / GESTIONRRHH.
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
    add_column_if_missing('INDECI_SOLICITUD_VACACION_DET', 'DIAS_CALENDARIO',
        'ALTER TABLE GESTIONRRHH.INDECI_SOLICITUD_VACACION_DET ADD (DIAS_CALENDARIO NUMBER(4,1))');
    add_column_if_missing('INDECI_VACACIONES', 'DIAS_CALENDARIO',
        'ALTER TABLE GESTIONRRHH.INDECI_VACACIONES ADD (DIAS_CALENDARIO NUMBER(4,1))');

    EXECUTE IMMEDIATE 'COMMENT ON COLUMN GESTIONRRHH.INDECI_SOLICITUD_VACACION_DET.DIAS_CALENDARIO IS '
        || '''Dias calendario reales del bloque (Art. 34) - descuenta VACACION_SALDO. '
        || 'Distinto de TOTAL_DIAS, que para FRACC_* es dias habiles (Art. 35.b/c).''';
    EXECUTE IMMEDIATE 'COMMENT ON COLUMN GESTIONRRHH.INDECI_VACACIONES.DIAS_CALENDARIO IS '
        || '''Dias calendario reales del bloque (Art. 34) que descontaron el saldo al aprobar. '
        || 'Distinto de DIAS, que para fraccionamiento es dias habiles (Art. 35.b, pool historico).''';

    DBMS_OUTPUT.PUT_LINE('V012_48 finalizado.');
END;
/
