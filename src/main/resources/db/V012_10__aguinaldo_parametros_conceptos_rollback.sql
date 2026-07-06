-- ============================================================
-- ROLLBACK V012_10 — AGUINALDO por régimen
--
-- Revierte: parámetros del aguinaldo, conceptos CAS/SERVIR sembrados (si no se
-- usaron) y restaura los flags originales de los conceptos 276 (00201/00202).
-- NO toca movimientos ni períodos históricos. Idempotente.
-- ============================================================

SET SERVEROUTPUT ON;

BEGIN
    -- 1) Parámetros del aguinaldo.
    DELETE FROM GESTIONRRHH.INDECI_PARAMETRO_REMUNERATIVO
     WHERE CODIGO_PARAMETRO IN ('AGUINALDO_CAS_PISO', 'AGUINALDO_276_MONTO')
       AND ANIO_FISCAL = 2026 AND REGIMEN_LABORAL_ID IS NULL;
    DBMS_OUTPUT.PUT_LINE('Parámetros aguinaldo eliminados: ' || SQL%ROWCOUNT);

    -- 2) Conceptos CAS/SERVIR sembrados por V012_10 (solo si no fueron usados).
    DELETE FROM GESTIONRRHH.INDECI_CONCEPTO_PLANILLA
     WHERE CODIGO_MEF IN ('0077', '0025', 'AGUISRVPV')
       AND NOT EXISTS (
           SELECT 1 FROM GESTIONRRHH.INDECI_MOVIMIENTO_PLANILLA_DET dt
            WHERE dt.CONCEPTO_PLANILLA_ID = GESTIONRRHH.INDECI_CONCEPTO_PLANILLA.ID);
    DBMS_OUTPUT.PUT_LINE('Conceptos aguinaldo CAS/SERVIR eliminados (no usados): ' || SQL%ROWCOUNT);

    -- 3) Restaurar flags originales de los conceptos 276 (estado pre-V012_10).
    UPDATE GESTIONRRHH.INDECI_CONCEPTO_PLANILLA
       SET AFECTO_IR_5TA = 'N', AFECTO_APORTE_PENS = 'S', AFECTO_ESSALUD = 'N'
     WHERE CODIGO_MEF IN ('00201', '00202');
    DBMS_OUTPUT.PUT_LINE('Flags 276 (00201/00202) restaurados: ' || SQL%ROWCOUNT);

    COMMIT;
    DBMS_OUTPUT.PUT_LINE('ROLLBACK V012_10 finalizado.');
EXCEPTION
    WHEN OTHERS THEN
        DBMS_OUTPUT.PUT_LINE('ERROR en rollback V012_10: ' || SQLERRM);
        RAISE;
END;
/
