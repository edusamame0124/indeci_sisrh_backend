-- ============================================================================
-- V012_54 ROLLBACK — Revierte REQUIERE_SUSTENTO a 0 para 'COMISION_DIA'.
--
-- No revierte '011': el flag de Licencia pertenece al tipo padre desde antes
-- de esta migración (valor original desconocido, seed no versionado) — no es
-- seguro asumir que era 0. Si se necesita revertir '011' puntualmente,
-- hacerlo con un UPDATE manual verificado contra el valor real anterior.
-- Idempotente. Oracle 19c+ / GESTIONRRHH.
-- ============================================================================

SET SERVEROUTPUT ON;

DECLARE
    v_rows NUMBER;
BEGIN
    UPDATE INDECI_TIPO_SOLICITUD_RRHH
       SET REQUIERE_SUSTENTO = 0
     WHERE CODIGO = 'COMISION_DIA'
       AND REQUIERE_SUSTENTO <> 0;

    v_rows := SQL%ROWCOUNT;

    COMMIT;
    DBMS_OUTPUT.PUT_LINE('V012_54 rollback: REQUIERE_SUSTENTO=0 restaurado para COMISION_DIA (' || v_rows || ' fila[s]).');
END;
/
