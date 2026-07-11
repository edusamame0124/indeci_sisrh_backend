-- ============================================================================
-- V012_29 ROLLBACK — restaura los flags originales del seed V012_27.
-- (No recomendado: reintroduce el bug de registro. Solo para trazabilidad.)
-- Idempotente. Oracle 19c+ / GESTIONRRHH.
-- ============================================================================

SET SERVEROUTPUT ON;

BEGIN
    UPDATE INDECI_TIPO_SOLICITUD_RRHH
       SET MOSTRAR_HORAS = 1,
           REQUIERE_OBSERVACION = 1
     WHERE CODIGO = 'TELETRABAJO';

    DBMS_OUTPUT.PUT_LINE('Tipo TELETRABAJO flags revertidos (' || SQL%ROWCOUNT || ' fila).');
    COMMIT;
END;
/
