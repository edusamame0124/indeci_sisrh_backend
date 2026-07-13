-- Rollback V012_31 — elimina la tabla de auditoría de decisiones de acumulación (idempotente).
-- ⚠️ Es una tabla de auditoría append-only: solo hacer rollback si es estrictamente necesario
-- (se pierde el historial de decisiones registradas por RR.HH.).
SET SERVEROUTPUT ON;
DECLARE
    v NUMBER;
BEGIN
    SELECT COUNT(*) INTO v FROM USER_TABLES WHERE TABLE_NAME = 'INDECI_VACACION_ACUM_DECISION';
    IF v > 0 THEN
        EXECUTE IMMEDIATE 'DROP TABLE INDECI_VACACION_ACUM_DECISION';
        DBMS_OUTPUT.PUT_LINE('INDECI_VACACION_ACUM_DECISION -> eliminada.');
    ELSE
        DBMS_OUTPUT.PUT_LINE('INDECI_VACACION_ACUM_DECISION no existe. Sin cambios.');
    END IF;
END;
/
