-- ============================================================================
-- V012_54 — Documento de sustento OBLIGATORIO para 'COMISION_DIA' y Licencia
-- con goce ('011')
--
-- OBJETIVO (RR.HH., 2026-08-04):
--   El adjunto deja de ser opcional para estos dos tipos. El flag
--   REQUIERE_SUSTENTO se usa como bandera informativa/UI; la exigencia real
--   la aplica el backend en SolicitudRrhhService.validarSustentoObligatorio()
--   (guard nuevo y acotado — NO reactiva la validación genérica retirada que
--   afecta a los demás 11+ tipos de papeleta).
--
--   '011' agrupa AMBAS modalidades (con/sin goce) bajo el mismo tipo de
--   catálogo; la licencia SIN GOCE no se ve afectada — sigue su propio
--   flujo de papeleta firmada (INDECI_SOLICITUD_RRHH.ESTADO=PENDIENTE_FIRMA),
--   ajeno a este adjunto de creación.
--
-- Idempotente. Oracle 19c+ / GESTIONRRHH.
-- ============================================================================

SET SERVEROUTPUT ON;

DECLARE
    v_rows NUMBER;
BEGIN
    UPDATE INDECI_TIPO_SOLICITUD_RRHH
       SET REQUIERE_SUSTENTO = 1
     WHERE CODIGO IN ('COMISION_DIA', '011')
       AND REQUIERE_SUSTENTO <> 1;

    v_rows := SQL%ROWCOUNT;

    COMMIT;
    DBMS_OUTPUT.PUT_LINE('V012_54: REQUIERE_SUSTENTO=1 aplicado (' || v_rows || ' fila[s]).');
END;
/
