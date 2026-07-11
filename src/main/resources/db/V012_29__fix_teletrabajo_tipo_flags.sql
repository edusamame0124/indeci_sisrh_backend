-- ============================================================================
-- V012_29 — Fix flags del tipo TELETRABAJO (Ley N° 31572)
--
-- V012_27 sembró el tipo con MOSTRAR_HORAS=1 y REQUIERE_OBSERVACION=1, pero el
-- reporte de teletrabajo usa su propio diálogo (fecha + actividades del día) y NO
-- envía horas ni observación libre. Con esos flags, validarSolicitud (validarHoras
-- / validarObservacion) rechazaba SIEMPRE el registro con HTTP 400.
--
-- Este script corrige los datos ya cargados. (El código además blinda el flujo
-- excluyendo TELETRABAJO de validarHoras/validarObservacion.)
--
-- Idempotente. Oracle 19c+ / GESTIONRRHH.
-- ============================================================================

SET SERVEROUTPUT ON;

BEGIN
    UPDATE INDECI_TIPO_SOLICITUD_RRHH
       SET MOSTRAR_HORAS = 0,
           REQUIERE_OBSERVACION = 0
     WHERE CODIGO = 'TELETRABAJO'
       AND (MOSTRAR_HORAS <> 0 OR REQUIERE_OBSERVACION <> 0);

    DBMS_OUTPUT.PUT_LINE('Tipo TELETRABAJO flags corregidos (' || SQL%ROWCOUNT || ' fila).');
    COMMIT;
    DBMS_OUTPUT.PUT_LINE('V012_29 finalizado correctamente.');
END;
/
