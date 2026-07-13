-- Rollback V012_33 — vuelve DIAS a entero. ADVERTENCIA: si existen goces de media
-- jornada (0.5), Oracle redondeará/fallará; primero depurar esos registros.
SET SERVEROUTPUT ON
BEGIN
    EXECUTE IMMEDIATE 'ALTER TABLE GESTIONRRHH.INDECI_VACACIONES MODIFY (DIAS NUMBER(4,0))';
    DBMS_OUTPUT.PUT_LINE('INDECI_VACACIONES.DIAS revertida a NUMBER(4,0).');
END;
/
