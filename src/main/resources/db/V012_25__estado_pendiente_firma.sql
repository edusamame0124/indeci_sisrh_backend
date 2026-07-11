-- ============================================================================
-- SPEC_VACACIONES F9.1-bis — Estado PENDIENTE_FIRMA para el flujo de firma de papeleta.
--
-- Flujo (solo licencia sin goce): BORRADOR/creación → PENDIENTE_FIRMA → [subir papeleta
-- firmada al enviar] → ENVIADO → jefe → RR.HH.
--
-- Idempotente (INSERT WHERE NOT EXISTS por CODIGO). ID es IDENTITY → se omite.
-- Oracle 19c+ / GESTIONRRHH.
-- ============================================================================

SET SERVEROUTPUT ON;

DECLARE
    v_exists NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_exists
      FROM GESTIONRRHH.INDECI_ESTADO_SOLICITUD
     WHERE CODIGO = 'PENDIENTE_FIRMA';

    IF v_exists = 0 THEN
        INSERT INTO GESTIONRRHH.INDECI_ESTADO_SOLICITUD (NOMBRE, CODIGO, ACTIVO)
        VALUES ('Pendiente de firma', 'PENDIENTE_FIRMA', 1);
        DBMS_OUTPUT.PUT_LINE('Estado PENDIENTE_FIRMA -> creado.');
    ELSE
        DBMS_OUTPUT.PUT_LINE('Estado PENDIENTE_FIRMA ya existe.');
    END IF;

    COMMIT;
    DBMS_OUTPUT.PUT_LINE('V012_25 finalizado.');
END;
/
