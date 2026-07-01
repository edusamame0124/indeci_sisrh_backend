-- ============================================================
-- ROLLBACK V012_02 — Restaura los valores AFP previos (seed V010_70).
-- ADVERTENCIA: esos valores eran INCORRECTOS. Este rollback existe solo por
-- consistencia/reversibilidad; no debe usarse en producción salvo emergencia.
-- ============================================================

SET SERVEROUTPUT ON;

DECLARE
    PROCEDURE restaurar_afp(
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
               FUENTE_OFICIAL           = 'SBS - Circular AFP-163-2025',
               MODIFICADO_POR           = 'SISTEMA',
               MODIFICADO_EN            = CURRENT_TIMESTAMP
         WHERE AFP_ID = v_afp_id
           AND PERIODO_FIN IS NULL
           AND ESTADO = 'VIGENTE';

        DBMS_OUTPUT.PUT_LINE(p_codigo || ' -> ' || SQL%ROWCOUNT || ' fila(s) restaurada(s).');
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            DBMS_OUTPUT.PUT_LINE(p_codigo || ' -> AFP no encontrada. Omitido.');
    END;
BEGIN
    restaurar_afp('HABITAT',   0.38, 0.00, 1.74, 12537.00);
    restaurar_afp('INTEGRA',   0.67, 0.00, 1.74, 12537.00);
    restaurar_afp('PROFUTURO', 0.87, 0.00, 1.74, 12537.00);
    restaurar_afp('PRIMA',     0.18, 1.25, 1.74, 12537.00);

    COMMIT;
    DBMS_OUTPUT.PUT_LINE('Rollback V012_02 finalizado.');
EXCEPTION
    WHEN OTHERS THEN
        ROLLBACK;
        DBMS_OUTPUT.PUT_LINE('ERROR en rollback V012_02: ' || SQLERRM);
        RAISE;
END;
/
