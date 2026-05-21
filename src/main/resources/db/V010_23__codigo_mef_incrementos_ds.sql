-- ============================================================================
-- Spec 013 / V010_23 — Corrección de CODIGO_MEF de los incrementos DS
--                       (Etapa 3 · C1 · P-04)
--
-- PROBLEMA: V010_11 sembró los incrementos por Decreto Supremo con códigos
-- LOCALES de 5 caracteres (00503, 00504). El MEF (AIRHSP / Planilla Electrónica)
-- rechaza esos identificadores: exige los CODIGO_MEF OFICIALES.
--
-- CORRECCIÓN ESTRUCTURAL autorizada por la oficina de RRHH:
--   DS 265-2024-EF (aumento S/ 50)  → CODIGO_MEF '11051'  (concepto NUEVO)
--   DS 279-2024-EF (aumento S/ 100) → CODIGO_MEF '11053'  (era local '00503')
--   DS 327-2025-EF (aumento)        → CODIGO_MEF '11214'  (era local '00504')
--
-- "Pago Diferencial (059)" (CODIGO_MEF local '00505') NO se toca: conserva su
-- ID interno y su código. Como DS 327 pasa a '11214' (no a '00505'), no hay
-- colisión. Asignarle su propio CODIGO_MEF oficial es una tarea de datos
-- aparte: el código exacto debe darlo RRHH/MEF (LEY-01: no inventar códigos).
--
-- DECISIONES DE DISEÑO:
--   - Migración FORWARD, no se reedita V010_11: reeditar el seed crearía filas
--     duplicadas en las BD que ya lo aplicaron (MERGE casa por CODIGO_MEF).
--   - Se actualiza por UPDATE → el PK ID del concepto NO cambia: las FK desde
--     INDECI_MOVIMIENTO_PLANILLA_DET / INDECI_EMPLEADO_CONCEPTO siguen válidas.
--   - Idempotente y robusta: si el código oficial ya existe no hace nada; si el
--     local existe lo corrige; si no existe ninguno lo inserta nuevo.
--
-- Ejecutar en esquema GESTIONRRHH / Oracle 19c+.
-- ============================================================================

SET SERVEROUTPUT ON;

DECLARE
    v_total NUMBER;

    -- Deja el incremento DS con su CODIGO_MEF oficial. Corrige el código local
    -- previo si existe; si no existe en ninguna forma, inserta el concepto.
    PROCEDURE fijar_incremento_ds(
        p_cod_local   VARCHAR2,   -- código local previo; NULL si nunca existió
        p_cod_oficial VARCHAR2,   -- CODIGO_MEF oficial destino
        p_sisper      VARCHAR2,
        p_nombre      VARCHAR2
    ) IS
        v_existe_oficial NUMBER;
        v_corregidos     NUMBER := 0;
    BEGIN
        SELECT COUNT(*) INTO v_existe_oficial
          FROM GESTIONRRHH.INDECI_CONCEPTO_PLANILLA
         WHERE CODIGO_MEF = p_cod_oficial;

        IF v_existe_oficial > 0 THEN
            DBMS_OUTPUT.PUT_LINE(p_cod_oficial || ' (' || p_nombre
                || ') ya presente. Sin cambios.');
            RETURN;
        END IF;

        IF p_cod_local IS NOT NULL THEN
            UPDATE GESTIONRRHH.INDECI_CONCEPTO_PLANILLA
               SET CODIGO     = p_cod_oficial,
                   CODIGO_MEF = p_cod_oficial,
                   NOMBRE     = p_nombre
             WHERE CODIGO_MEF = p_cod_local;
            v_corregidos := SQL%ROWCOUNT;
        END IF;

        IF v_corregidos > 0 THEN
            DBMS_OUTPUT.PUT_LINE(p_cod_local || ' -> ' || p_cod_oficial
                || ' (' || p_nombre || ') corregido.');
        ELSE
            INSERT INTO GESTIONRRHH.INDECI_CONCEPTO_PLANILLA (
                CODIGO, CODIGO_MEF, CODIGO_SISPER, NOMBRE, TIPO,
                TIPO_CONCEPTO, AFECTO_IR_5TA, AFECTO_APORTE_PENS, AFECTO_ESSALUD,
                ES_MUC, ES_CUC, REGIMEN_APLICABLE,
                ACTIVO, CREATED_AT, FECHA_VIG_INI
            ) VALUES (
                p_cod_oficial, p_cod_oficial, p_sisper, p_nombre, 'INGRESO',
                'REMUNERATIVO', 'S', 'S', 'S',
                'N', 'N', 'SERVIR',
                1, CURRENT_TIMESTAMP, DATE '2026-01-01'
            );
            DBMS_OUTPUT.PUT_LINE(p_cod_oficial || ' (' || p_nombre
                || ') insertado como concepto nuevo.');
        END IF;
    END;
BEGIN
    -- DS 279-2024-EF: local '00503' -> oficial '11053'
    fijar_incremento_ds('00503', '11053', '072', 'Incremento DS 279-2024-EF');

    -- DS 327-2025-EF: local '00504' -> oficial '11214'
    fijar_incremento_ds('00504', '11214', 'xxx', 'Incremento DS 327-2025-EF');

    -- DS 265-2024-EF: concepto nuevo -> oficial '11051'
    fijar_incremento_ds(NULL, '11051', '071', 'Incremento DS 265-2024-EF');

    EXECUTE IMMEDIATE
        'COMMENT ON TABLE GESTIONRRHH.INDECI_CONCEPTO_PLANILLA IS ' ||
        '''Catálogo de conceptos de planilla. CODIGO_MEF debe ser el código ' ||
        'OFICIAL del MEF/AIRHSP (Ley 32448 / LEY-01) — no códigos locales.''';

    SELECT COUNT(*) INTO v_total
      FROM GESTIONRRHH.INDECI_CONCEPTO_PLANILLA
     WHERE CODIGO_MEF IN ('11051', '11053', '11214');
    DBMS_OUTPUT.PUT_LINE('Incrementos DS con CODIGO_MEF oficial: '
        || v_total || ' / 3.');
    DBMS_OUTPUT.PUT_LINE('V010_23 finalizado. '
        || '"Pago Diferencial (059)" / 00505 NO modificado.');
END;
/
