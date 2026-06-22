-- ============================================================================
-- Rollback V010_94 — elimina INDECI_IR4TA_CONTROL_ANUAL
--
-- DEFENSIVO: solo elimina la tabla si NO tiene filas (protege datos de control
-- ya capturados en entornos compartidos). Si tiene datos, avisa y no la borra.
-- Los índices y la FK caen con la tabla (CASCADE CONSTRAINTS).
--
-- Ejecutar conectado como GESTIONRRHH.
-- ============================================================================

SET SERVEROUTPUT ON;

DECLARE
    v_exists NUMBER;
    v_rows   NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_exists
      FROM USER_TABLES
     WHERE TABLE_NAME = 'INDECI_IR4TA_CONTROL_ANUAL';

    IF v_exists = 0 THEN
        DBMS_OUTPUT.PUT_LINE('INDECI_IR4TA_CONTROL_ANUAL no existe. Sin cambios.');
        RETURN;
    END IF;

    EXECUTE IMMEDIATE 'SELECT COUNT(*) FROM INDECI_IR4TA_CONTROL_ANUAL' INTO v_rows;
    IF v_rows > 0 THEN
        DBMS_OUTPUT.PUT_LINE(
            'INDECI_IR4TA_CONTROL_ANUAL tiene ' || v_rows
            || ' fila(s). NO se elimina (revisar manualmente).');
        RETURN;
    END IF;

    EXECUTE IMMEDIATE 'DROP TABLE INDECI_IR4TA_CONTROL_ANUAL CASCADE CONSTRAINTS PURGE';
    DBMS_OUTPUT.PUT_LINE('INDECI_IR4TA_CONTROL_ANUAL -> eliminada (vacía).');
END;
/
