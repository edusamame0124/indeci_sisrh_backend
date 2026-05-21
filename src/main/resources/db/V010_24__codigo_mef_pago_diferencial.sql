-- ============================================================================
-- Spec 013 / V010_24 — CODIGO_MEF oficial de "Pago Diferencial (059)"
--                       (Etapa 3 · C1 · P-04 — cierre)
--
-- PROBLEMA: el concepto "Pago Diferencial (059)" quedó en V010_11 con el código
-- LOCAL '00505'. El MEF/AIRHSP rechaza identificadores locales.
--
-- CORRECCIÓN autorizada por RRHH (régimen SERVIR — sin reclasificar):
--   CODIGO_MEF local '00505'  →  CODIGO_MEF oficial '110412'
--   Denominación oficial MEF: "Componente por Diferencial de la Remuneración (SERVIR)"
--   Columna CODIGO_MEF = VARCHAR2(10 CHAR) (V010_02) → cabe el código de 6 díg.
--
-- DECISIONES DE DISEÑO:
--   - Migración FORWARD (no se reedita V010_11), igual que V010_23.
--   - UPDATE → el PK ID NO cambia: las FK desde INDECI_MOVIMIENTO_PLANILLA_DET
--     e INDECI_EMPLEADO_CONCEPTO siguen válidas.
--   - El CODIGO interno se conserva en '00505' (identidad interna intacta);
--     solo cambia el CODIGO_MEF, que es lo que valida AIRHSP.
--   - REGIMEN_APLICABLE, AFECTO_IR_5TA y demás banderas NO se tocan: el
--     concepto sigue siendo SERVIR, remunerativo y afecto a 5ta (LEY-03).
--   - Idempotente: si el código oficial ya existe no hace nada; si existe el
--     local lo corrige; si no existe ninguno inserta el concepto.
--
-- Ejecutar en esquema GESTIONRRHH / Oracle 19c+.
-- ============================================================================

SET SERVEROUTPUT ON;

DECLARE
    v_existe_oficial NUMBER;
    v_corregidos     NUMBER := 0;
BEGIN
    SELECT COUNT(*) INTO v_existe_oficial
      FROM GESTIONRRHH.INDECI_CONCEPTO_PLANILLA
     WHERE CODIGO_MEF = '110412';

    IF v_existe_oficial > 0 THEN
        DBMS_OUTPUT.PUT_LINE('110412 (Pago Diferencial) ya presente. Sin cambios.');
    ELSE
        -- Solo se corrige CODIGO_MEF y NOMBRE; CODIGO interno, ID y régimen intactos.
        UPDATE GESTIONRRHH.INDECI_CONCEPTO_PLANILLA
           SET CODIGO_MEF = '110412',
               NOMBRE     = 'Componente por Diferencial de la Remuneración (SERVIR)'
         WHERE CODIGO_MEF = '00505';
        v_corregidos := SQL%ROWCOUNT;

        IF v_corregidos > 0 THEN
            DBMS_OUTPUT.PUT_LINE('00505 -> 110412 (Pago Diferencial) corregido. '
                || 'PK ID y CODIGO interno intactos.');
        ELSE
            -- La BD no tenía el concepto: se inserta con el código oficial.
            INSERT INTO GESTIONRRHH.INDECI_CONCEPTO_PLANILLA (
                CODIGO, CODIGO_MEF, CODIGO_SISPER, NOMBRE, TIPO,
                TIPO_CONCEPTO, AFECTO_IR_5TA, AFECTO_APORTE_PENS, AFECTO_ESSALUD,
                ES_MUC, ES_CUC, REGIMEN_APLICABLE,
                ACTIVO, CREATED_AT, FECHA_VIG_INI
            ) VALUES (
                '00505', '110412', '059',
                'Componente por Diferencial de la Remuneración (SERVIR)', 'INGRESO',
                'REMUNERATIVO', 'S', 'S', 'S',
                'N', 'N', 'SERVIR',
                1, CURRENT_TIMESTAMP, DATE '2026-01-01'
            );
            DBMS_OUTPUT.PUT_LINE('110412 (Pago Diferencial) insertado como concepto nuevo.');
        END IF;
    END IF;

    DBMS_OUTPUT.PUT_LINE('V010_24 finalizado.');
END;
/
