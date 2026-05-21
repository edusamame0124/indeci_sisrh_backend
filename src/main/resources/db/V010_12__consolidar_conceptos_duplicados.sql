-- ============================================================================
-- Spec 010 / V010_12 — Consolidación de conceptos duplicados + UNIQUE CODIGO_MEF
--
-- CONTEXTO: el diagnóstico de V010_11 reveló 7 CODIGO_MEF con filas duplicadas
-- (un concepto legacy backfilleado en V010_05 colisionando con el sembrado por
-- V010_04). Sin UNIQUE en CODIGO_MEF la colisión pasó inadvertida.
--
-- ESTRATEGIA (confirmada — camino D, 3 decisiones):
--   1. Conservar el concepto de V010_04 (nombre correcto del SPEC) = GANADOR.
--   2. Reapuntar las FK de los conceptos legacy (PERDEDORES) hacia el ganador
--      en INDECI_MOVIMIENTO_PLANILLA_DET e INDECI_EMPLEADO_CONCEPTO.
--      Los montos históricos NO se pierden — viven en el detalle, sólo se
--      corrige el puntero CONCEPTO_PLANILLA_ID.
--   3. DELETE físico de los perdedores (ya sin FK entrante).
--   4. ADD CONSTRAINT UNIQUE (CODIGO_MEF) — cierra la puerta a futuras colisiones.
--
-- MAPA perdedor -> ganador (de los diagnósticos de V010_11):
--   ID 1  -> 60   (00101  Remuneración Principal)
--   ID 8  -> 53   (00301  Sueldo Básico)
--   ID 21 -> 41   (00501  Remuneración CAS)
--   ID 4  -> 57   (05002  Aporte AFP 10%)
--   ID 9  -> 57   (05002  Aporte AFP 10%)
--   ID 5  -> 48   (05003  Comisión AFP)
--   ID 6  -> 64   (05004  Prima Seguro AFP)
--   ID 7  -> 42   (05101  Retención 5ta Categoría)
--
-- DEFENSA EN PROFUNDIDAD:
--   - Verifica que cada GANADOR exista antes de migrar (aborta si no).
--   - Reentrante: si un perdedor ya fue eliminado, los UPDATE/DELETE afectan
--     0 filas y el script continúa.
--   - Verifica 0 duplicados ANTES de agregar el UNIQUE (aborta si quedan).
--   - ADD CONSTRAINT idempotente (consulta ALL_CONSTRAINTS).
--
-- FUERA DE SCOPE: la recodificación 00501 CAS->SERVIR (bloque 4 de V010_11)
-- NO se toca aquí. V010_12 sólo deduplica y blinda con UNIQUE.
--
-- Ejecutar en esquema GESTIONRRHH / Oracle 19c+.
-- ============================================================================

SET SERVEROUTPUT ON;

DECLARE
    TYPE t_par   IS RECORD (perdedor NUMBER, ganador NUMBER);
    TYPE t_lista IS TABLE OF t_par;

    v_pares t_lista := t_lista(
        t_par(1,  60),   -- 00101
        t_par(8,  53),   -- 00301
        t_par(21, 41),   -- 00501
        t_par(4,  57),   -- 05002
        t_par(9,  57),   -- 05002
        t_par(5,  48),   -- 05003
        t_par(6,  64),   -- 05004
        t_par(7,  42)    -- 05101
    );

    v_mov            NUMBER;
    v_ec             NUMBER;
    v_del            NUMBER;
    v_total_mov      NUMBER := 0;
    v_total_ec       NUMBER := 0;
    v_total_del      NUMBER := 0;
    v_ganador_existe NUMBER;
    v_dups           NUMBER;
    v_uk_exists      NUMBER;
BEGIN
    DBMS_OUTPUT.PUT_LINE('=== V010_12 — consolidación de conceptos duplicados ===');

    -- ------------------------------------------------------------------
    -- 1-3. Migrar FKs de cada perdedor al ganador y eliminar el perdedor
    -- ------------------------------------------------------------------
    FOR i IN 1 .. v_pares.COUNT LOOP

        -- Seguridad: el ganador debe existir
        SELECT COUNT(*)
          INTO v_ganador_existe
          FROM GESTIONRRHH.INDECI_CONCEPTO_PLANILLA
         WHERE ID = v_pares(i).ganador;

        IF v_ganador_existe = 0 THEN
            RAISE_APPLICATION_ERROR(-20030,
                'V010_12 ABORTADO: el concepto GANADOR ID='
                || v_pares(i).ganador || ' no existe. Revisar manualmente.');
        END IF;

        -- Reapuntar FK en INDECI_MOVIMIENTO_PLANILLA_DET
        UPDATE GESTIONRRHH.INDECI_MOVIMIENTO_PLANILLA_DET
           SET CONCEPTO_PLANILLA_ID = v_pares(i).ganador
         WHERE CONCEPTO_PLANILLA_ID = v_pares(i).perdedor;
        v_mov := SQL%ROWCOUNT;

        -- Reapuntar FK en INDECI_EMPLEADO_CONCEPTO
        UPDATE GESTIONRRHH.INDECI_EMPLEADO_CONCEPTO
           SET CONCEPTO_PLANILLA_ID = v_pares(i).ganador
         WHERE CONCEPTO_PLANILLA_ID = v_pares(i).perdedor;
        v_ec := SQL%ROWCOUNT;

        -- Eliminar el concepto perdedor (ya sin FK entrante)
        DELETE FROM GESTIONRRHH.INDECI_CONCEPTO_PLANILLA
         WHERE ID = v_pares(i).perdedor;
        v_del := SQL%ROWCOUNT;

        v_total_mov := v_total_mov + v_mov;
        v_total_ec  := v_total_ec  + v_ec;
        v_total_del := v_total_del + v_del;

        DBMS_OUTPUT.PUT_LINE(
            '  perdedor ' || LPAD(v_pares(i).perdedor, 3)
            || ' -> ganador ' || LPAD(v_pares(i).ganador, 3)
            || ' | mov reapuntados=' || v_mov
            || ' ec reapuntados='    || v_ec
            || ' concepto eliminado='|| v_del);
    END LOOP;

    DBMS_OUTPUT.PUT_LINE('------------------------------------------------------------------');
    DBMS_OUTPUT.PUT_LINE('TOTALES: movimientos reapuntados=' || v_total_mov
        || ' | empleado_concepto reapuntados=' || v_total_ec
        || ' | conceptos eliminados=' || v_total_del);

    -- ------------------------------------------------------------------
    -- Verificación: no debe quedar NINGÚN CODIGO_MEF duplicado
    -- ------------------------------------------------------------------
    SELECT COUNT(*)
      INTO v_dups
      FROM (
          SELECT CODIGO_MEF
            FROM GESTIONRRHH.INDECI_CONCEPTO_PLANILLA
           GROUP BY CODIGO_MEF
          HAVING COUNT(*) > 1
      );

    IF v_dups > 0 THEN
        RAISE_APPLICATION_ERROR(-20031,
            'V010_12 ABORTADO antes del UNIQUE: aún hay ' || v_dups
            || ' CODIGO_MEF duplicado(s). Revisar con el query de diagnóstico.');
    END IF;
    DBMS_OUTPUT.PUT_LINE('Verificación OK: 0 CODIGO_MEF duplicados.');

    -- ------------------------------------------------------------------
    -- 4. ADD CONSTRAINT UNIQUE (CODIGO_MEF) — idempotente
    -- ------------------------------------------------------------------
    SELECT COUNT(*)
      INTO v_uk_exists
      FROM ALL_CONSTRAINTS
     WHERE OWNER           = 'GESTIONRRHH'
       AND TABLE_NAME      = 'INDECI_CONCEPTO_PLANILLA'
       AND CONSTRAINT_NAME = 'INDECI_CONCEPTO_MEF_UK';

    IF v_uk_exists = 0 THEN
        EXECUTE IMMEDIATE
            'ALTER TABLE GESTIONRRHH.INDECI_CONCEPTO_PLANILLA '
            || 'ADD CONSTRAINT INDECI_CONCEPTO_MEF_UK UNIQUE (CODIGO_MEF)';
        DBMS_OUTPUT.PUT_LINE('INDECI_CONCEPTO_MEF_UK -> agregado.');
    ELSE
        DBMS_OUTPUT.PUT_LINE('INDECI_CONCEPTO_MEF_UK ya existe. Sin cambios.');
    END IF;

    -- DML commiteado (el ALTER anterior ya hizo commit implícito; este COMMIT
    -- cubre el caso reentrante donde no se ejecutó ningún ALTER).
    COMMIT;
    DBMS_OUTPUT.PUT_LINE('V010_12 finalizado.');
END;
/
