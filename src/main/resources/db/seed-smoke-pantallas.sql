-- ============================================================================
-- SEED DE SMOKE — Pantallas §12.2 (Spec 010)  ·  NO es una migración
--
-- Carga datos mínimos para que el smoke test de las pantallas nuevas sea
-- concluyente (que no se vean vacías):
--   - INDECI_EMPLEADO.AIRHSP_MONTO   → PANTALLA-06 (un caso VERDE y uno ROJO)
--   - INDECI_ASISTENCIA_CABECERA/DET → PANTALLA-02 + PASO 7 (asistencia VALIDADA)
--   - INDECI_PRESTAMO                → PANTALLA-08 (saldo de préstamos)
--   - INDECI_VACACION_SALDO          → PANTALLA-08 (saldo de vacaciones)
--
-- AUTO-DETECTA los empleados: toma los que ya tienen movimiento de planilla
-- activo (no hay que editar IDs). Es IDEMPOTENTE: re-ejecutable sin duplicar.
--
-- PRE-REQUISITOS: aplicar antes V010_17, V010_18 y V010_19, y tener al menos
-- una planilla generada (INDECI_MOVIMIENTO_PLANILLA).
--
-- ⚠ Es data de prueba: ejecutar SOLO en entornos de smoke/QA, no en producción.
-- Ejecutar en esquema GESTIONRRHH / Oracle 19c+.
-- ============================================================================

SET SERVEROUTPUT ON;

DECLARE
    v_chk         NUMBER;
    v_emp1        NUMBER;
    v_emp2        NUMBER;
    v_periodo     VARCHAR2(7);
    v_ingresos1   NUMBER;
    v_ingresos2   NUMBER;
    v_remun       NUMBER;
    v_cab_id      NUMBER;
    v_cnt         NUMBER;
    v_desc_tard   NUMBER;
    v_desc_falta  NUMBER;
    v_anio        NUMBER;
BEGIN
    -- 0. Pre-requisitos: tablas de V010_17 / V010_19 + columna de V010_18
    SELECT COUNT(*) INTO v_chk
      FROM ALL_TABLES
     WHERE OWNER = 'GESTIONRRHH'
       AND TABLE_NAME IN ('INDECI_ASISTENCIA_CABECERA', 'INDECI_ASISTENCIA_DETALLE',
                          'INDECI_PRESTAMO', 'INDECI_VACACION_SALDO');
    IF v_chk < 4 THEN
        DBMS_OUTPUT.PUT_LINE('ABORTADO — faltan tablas. Aplica V010_17 y V010_19 antes del seed.');
        RETURN;
    END IF;

    SELECT COUNT(*) INTO v_chk
      FROM ALL_TAB_COLUMNS
     WHERE OWNER = 'GESTIONRRHH'
       AND TABLE_NAME = 'INDECI_EMPLEADO'
       AND COLUMN_NAME = 'AIRHSP_MONTO';
    IF v_chk = 0 THEN
        DBMS_OUTPUT.PUT_LINE('ABORTADO — falta INDECI_EMPLEADO.AIRHSP_MONTO. Aplica V010_18.');
        RETURN;
    END IF;

    -- 1. Empleado 1 = primer empleado con movimiento de planilla activo
    BEGIN
        SELECT EMPLEADO_ID, PERIODO, TOTAL_INGRESOS
          INTO v_emp1, v_periodo, v_ingresos1
          FROM (SELECT EMPLEADO_ID, PERIODO, TOTAL_INGRESOS
                  FROM GESTIONRRHH.INDECI_MOVIMIENTO_PLANILLA
                 WHERE ACTIVO = 1
                 ORDER BY EMPLEADO_ID, PERIODO DESC)
         WHERE ROWNUM = 1;
    EXCEPTION WHEN NO_DATA_FOUND THEN
        DBMS_OUTPUT.PUT_LINE('ABORTADO — no hay movimientos de planilla. Genera una planilla primero.');
        RETURN;
    END;
    v_remun := NVL(v_ingresos1, 3000);

    -- 2. Empleado 2 = otro empleado con movimiento (para el caso ROJO de conciliación)
    BEGIN
        SELECT EMPLEADO_ID, TOTAL_INGRESOS
          INTO v_emp2, v_ingresos2
          FROM (SELECT EMPLEADO_ID, TOTAL_INGRESOS
                  FROM GESTIONRRHH.INDECI_MOVIMIENTO_PLANILLA
                 WHERE ACTIVO = 1 AND EMPLEADO_ID <> v_emp1
                 ORDER BY EMPLEADO_ID, PERIODO DESC)
         WHERE ROWNUM = 1;
    EXCEPTION WHEN NO_DATA_FOUND THEN
        v_emp2 := NULL;
    END;

    -- ================= AIRHSP_MONTO (PANTALLA-06 / PASO 16) =================
    -- emp1: monto = ingresos del sistema → conciliará VERDE al regenerar.
    UPDATE GESTIONRRHH.INDECI_EMPLEADO
       SET AIRHSP_MONTO = v_ingresos1
     WHERE ID = v_emp1;

    -- emp2: monto distinto (85% de sus ingresos) → conciliará ROJO.
    IF v_emp2 IS NOT NULL THEN
        UPDATE GESTIONRRHH.INDECI_EMPLEADO
           SET AIRHSP_MONTO = ROUND(NVL(v_ingresos2, 0) * 0.85, 2)
         WHERE ID = v_emp2;
    END IF;
    DBMS_OUTPUT.PUT_LINE('AIRHSP_MONTO: emp ' || v_emp1 || ' (VERDE)'
        || CASE WHEN v_emp2 IS NULL THEN '' ELSE ', emp ' || v_emp2 || ' (ROJO)' END);

    -- ================= ASISTENCIA VALIDADA (PANTALLA-02 / PASO 7) =================
    SELECT COUNT(*) INTO v_cnt
      FROM GESTIONRRHH.INDECI_ASISTENCIA_CABECERA
     WHERE EMPLEADO_ID = v_emp1 AND PERIODO = v_periodo;
    IF v_cnt = 0 THEN
        -- D.Leg. 276 Art. 24: descuento = (remun/30/8/60) * minutos ; (remun/30) * faltas
        v_desc_tard  := ROUND(v_remun * 120 / 14400, 2);  -- 120 min de tardanza
        v_desc_falta := ROUND(v_remun * 1 / 30, 2);       -- 1 día de falta

        INSERT INTO GESTIONRRHH.INDECI_ASISTENCIA_CABECERA
            (EMPLEADO_ID, PERIODO, REMUNERACION_BASE, DIAS_LABORADOS, DIAS_FALTA,
             TOTAL_MIN_TARDANZA, DESCUENTO_TARDANZA, DESCUENTO_FALTA, ESTADO, OBSERVACION, ACTIVO)
        VALUES (v_emp1, v_periodo, v_remun, 2, 1, 120, v_desc_tard, v_desc_falta,
                'VALIDADA', 'Seed smoke §12.2', 1)
        RETURNING ID INTO v_cab_id;

        INSERT INTO GESTIONRRHH.INDECI_ASISTENCIA_DETALLE (CABECERA_ID, DIA, TIPO_DIA, MINUTOS_TARDANZA)
        VALUES (v_cab_id, TO_DATE(v_periodo || '-04', 'YYYY-MM-DD'), 'LABORAL', 0);
        INSERT INTO GESTIONRRHH.INDECI_ASISTENCIA_DETALLE (CABECERA_ID, DIA, TIPO_DIA, MINUTOS_TARDANZA)
        VALUES (v_cab_id, TO_DATE(v_periodo || '-05', 'YYYY-MM-DD'), 'TARDANZA', 120);
        INSERT INTO GESTIONRRHH.INDECI_ASISTENCIA_DETALLE (CABECERA_ID, DIA, TIPO_DIA, MINUTOS_TARDANZA)
        VALUES (v_cab_id, TO_DATE(v_periodo || '-06', 'YYYY-MM-DD'), 'FALTA', 0);

        DBMS_OUTPUT.PUT_LINE('Asistencia VALIDADA: emp ' || v_emp1 || ' periodo ' || v_periodo
            || ' (desc. tardanza ' || v_desc_tard || ', falta ' || v_desc_falta || ')');
    ELSE
        DBMS_OUTPUT.PUT_LINE('Asistencia ya existe para emp ' || v_emp1
            || ' periodo ' || v_periodo || ' — sin cambios.');
    END IF;

    -- ================= PRÉSTAMOS (PANTALLA-08) =================
    SELECT COUNT(*) INTO v_cnt
      FROM GESTIONRRHH.INDECI_PRESTAMO WHERE EMPLEADO_ID = v_emp1;
    IF v_cnt = 0 THEN
        INSERT INTO GESTIONRRHH.INDECI_PRESTAMO
            (EMPLEADO_ID, DESCRIPCION, MONTO_TOTAL, NUMERO_CUOTAS, CUOTA_MENSUAL,
             CUOTAS_PAGADAS, ESTADO, FECHA_INICIO, ACTIVO)
        VALUES (v_emp1, 'Préstamo administrativo', 1200, 12, 100, 3, 'ACTIVO', SYSDATE, 1);
        INSERT INTO GESTIONRRHH.INDECI_PRESTAMO
            (EMPLEADO_ID, DESCRIPCION, MONTO_TOTAL, NUMERO_CUOTAS, CUOTA_MENSUAL,
             CUOTAS_PAGADAS, ESTADO, FECHA_INICIO, ACTIVO)
        VALUES (v_emp1, 'Adelanto de gratificación', 600, 6, 100, 6, 'CANCELADO', SYSDATE, 1);
        DBMS_OUTPUT.PUT_LINE('Préstamos sembrados: emp ' || v_emp1 || ' (1 ACTIVO + 1 CANCELADO).');
    ELSE
        DBMS_OUTPUT.PUT_LINE('Préstamos ya existen para emp ' || v_emp1 || ' — sin cambios.');
    END IF;

    -- ================= SALDO DE VACACIONES (PANTALLA-08) =================
    v_anio := EXTRACT(YEAR FROM SYSDATE);
    FOR i IN 0 .. 1 LOOP
        SELECT COUNT(*) INTO v_cnt
          FROM GESTIONRRHH.INDECI_VACACION_SALDO
         WHERE EMPLEADO_ID = v_emp1 AND ANIO = v_anio - i;
        IF v_cnt = 0 THEN
            INSERT INTO GESTIONRRHH.INDECI_VACACION_SALDO
                (EMPLEADO_ID, ANIO, DIAS_GANADOS, DIAS_GOZADOS, OBSERVACION, ACTIVO)
            VALUES (v_emp1, v_anio - i, 30,
                    CASE WHEN i = 0 THEN 8 ELSE 30 END, 'Seed smoke §12.2', 1);
        END IF;
    END LOOP;
    DBMS_OUTPUT.PUT_LINE('Saldo de vacaciones sembrado: emp ' || v_emp1
        || ' (años ' || (v_anio - 1) || ' y ' || v_anio || ').');

    COMMIT;
    DBMS_OUTPUT.PUT_LINE('====================================================');
    DBMS_OUTPUT.PUT_LINE('Seed smoke completado.');
    DBMS_OUTPUT.PUT_LINE('SIGUIENTE PASO: regenera la planilla del periodo ' || v_periodo
        || ' (empleados ' || v_emp1
        || CASE WHEN v_emp2 IS NULL THEN '' ELSE ' y ' || v_emp2 END
        || ') para que el PASO 7 aplique el descuento de asistencia y el PASO 16');
    DBMS_OUTPUT.PUT_LINE('cree las conciliaciones AIRHSP con los montos sembrados.');
END;
/
