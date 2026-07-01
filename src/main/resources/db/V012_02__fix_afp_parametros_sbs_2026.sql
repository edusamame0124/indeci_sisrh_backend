-- ============================================================
-- V012_02 — Corrección de parámetros AFP a tabla oficial SBS vigente (2026)
--
-- Motivo: el seed V010_70 cargó valores erróneos de comisión sobre flujo,
--         prima de seguro (1.74% en vez de 1.37%) y tope de remuneración
--         asegurable (12,537.00 en vez de 12,598.91). Esto producía comisiones
--         y netos incorrectos en el motor de planilla.
--
-- Fuente oficial (SBS — comisiones y prima de seguro AFP vigentes 2026):
--   AFP        Comisión flujo   Prima seguro   Comisión saldo (anual, informativa)   Tope aseg.
--   Integra        1.55%           1.37%                0.78%                        12,598.91
--   Habitat        1.47%           1.37%                1.25%                        12,598.91
--   Profuturo      1.69%           1.37%                0.68%                        12,598.91
--   Prima          1.60%           1.37%                1.25%                        12,598.91
--
-- Nota: la comisión SOBRE SALDO no se cobra en la planilla mensual (la carga la
--       AFP una vez al año sobre el fondo). Se registra solo como referencia.
--
-- Estrategia: corrige EN SITIO la vigencia abierta (PERIODO_FIN IS NULL, VIGENTE)
--             de cada AFP. Idempotente: reejecutable sin efectos adversos.
-- ============================================================

SET SERVEROUTPUT ON;

DECLARE
    PROCEDURE actualizar_afp(
        p_codigo VARCHAR2,
        p_flujo  NUMBER,
        p_saldo  NUMBER,
        p_prima  NUMBER,
        p_tope   NUMBER
    ) IS
        v_afp_id NUMBER;
    BEGIN
        SELECT ID INTO v_afp_id
          FROM GESTIONRRHH.INDECI_AFP
         WHERE CODIGO = p_codigo;

        UPDATE GESTIONRRHH.INDECI_AFP_PARAMETRO_VIGENCIA
           SET COMISION_FLUJO_PCT       = p_flujo,
               COMISION_SALDO_ANUAL_PCT = p_saldo,
               PRIMA_SEGURO_PCT         = p_prima,
               REMUNERACION_MAXIMA_ASEG = p_tope,
               FUENTE_OFICIAL           = 'SBS - Comisiones y prima de seguro AFP vigentes 2026',
               MODIFICADO_POR           = 'SISTEMA',
               MODIFICADO_EN            = CURRENT_TIMESTAMP
         WHERE AFP_ID = v_afp_id
           AND PERIODO_FIN IS NULL
           AND ESTADO = 'VIGENTE';

        DBMS_OUTPUT.PUT_LINE(p_codigo || ' -> ' || SQL%ROWCOUNT || ' fila(s) actualizada(s).');
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            DBMS_OUTPUT.PUT_LINE(p_codigo || ' -> AFP no encontrada en catálogo. Omitido.');
    END;
BEGIN
    actualizar_afp('INTEGRA',   1.55, 0.78, 1.37, 12598.91);
    actualizar_afp('HABITAT',   1.47, 1.25, 1.37, 12598.91);
    actualizar_afp('PROFUTURO', 1.69, 0.68, 1.37, 12598.91);
    actualizar_afp('PRIMA',     1.60, 1.25, 1.37, 12598.91);

    COMMIT;
    DBMS_OUTPUT.PUT_LINE('V012_02 finalizado correctamente.');
EXCEPTION
    WHEN OTHERS THEN
        ROLLBACK;
        DBMS_OUTPUT.PUT_LINE('ERROR en V012_02: ' || SQLERRM);
        RAISE;
END;
/
