-- ============================================================================
-- Spec 011 / B2 — Vínculo usuario ↔ empleado (Etapa 3)
--
-- Agrega USERS.EMPLEADO_ID: liga la cuenta de acceso (M01) con el empleado
-- (INDECI_EMPLEADO). Habilita que el Portal del Empleado (PANTALLA-08) opere
-- como self-service: el usuario logueado sabe "qué empleado soy".
--
--   USERS.EMPLEADO_ID  NUMBER  → FK lógica a INDECI_EMPLEADO.ID. NULL = la
--                                cuenta no está vinculada a ningún empleado
--                                (p. ej. usuarios administrativos).
--
-- DEFENSA EN PROFUNDIDAD: idempotente (add_column_if_missing).
-- Ejecutar en esquema GESTIONRRHH / Oracle 19c+.
-- ============================================================================

SET SERVEROUTPUT ON;

DECLARE
    v_exists NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO v_exists
      FROM ALL_TAB_COLUMNS
     WHERE OWNER       = 'GESTIONRRHH'
       AND TABLE_NAME  = 'USERS'
       AND COLUMN_NAME = 'EMPLEADO_ID';

    IF v_exists = 0 THEN
        EXECUTE IMMEDIATE
            'ALTER TABLE GESTIONRRHH.USERS ADD (EMPLEADO_ID NUMBER)';
        DBMS_OUTPUT.PUT_LINE('USERS.EMPLEADO_ID -> agregada.');
    ELSE
        DBMS_OUTPUT.PUT_LINE('USERS.EMPLEADO_ID ya existe. Sin cambios.');
    END IF;

    EXECUTE IMMEDIATE
        'COMMENT ON COLUMN GESTIONRRHH.USERS.EMPLEADO_ID IS ' ||
        '''Spec 011 / B2: empleado vinculado a la cuenta (INDECI_EMPLEADO.ID). ' ||
        'NULL = cuenta sin vínculo (usuario administrativo).''';

    DBMS_OUTPUT.PUT_LINE('V010_21 finalizado.');
END;
/
