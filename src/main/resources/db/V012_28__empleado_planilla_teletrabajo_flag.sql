-- ============================================================================
-- V012_28 — Gate de Modalidad de Teletrabajo (Ley N° 31572 / SERVIR)
--
-- El teletrabajo en el Estado NO es autodeclaración del servidor: requiere
-- resolución/adenda en el legajo. Este flag habilita, por vínculo/configuración
-- remunerativa (INDECI_EMPLEADO_PLANILLA), a los servidores autorizados por RR.HH.
-- El sistema bloquea (Poka-Yoke) el "Reporte Teletrabajo" para quien tenga 0.
--
-- Idempotente (verifica USER_TAB_COLUMNS). Oracle 19c+ / GESTIONRRHH.
-- ============================================================================

SET SERVEROUTPUT ON;

DECLARE
    v NUMBER;
BEGIN
    SELECT COUNT(*) INTO v
      FROM USER_TAB_COLUMNS
     WHERE TABLE_NAME = 'INDECI_EMPLEADO_PLANILLA'
       AND COLUMN_NAME = 'ES_TELETRABAJADOR';

    IF v = 0 THEN
        EXECUTE IMMEDIATE
            'ALTER TABLE INDECI_EMPLEADO_PLANILLA '
            || 'ADD (ES_TELETRABAJADOR NUMBER(1) DEFAULT 0 NOT NULL '
            || 'CONSTRAINT INDECI_EMP_PLA_TT_CK CHECK (ES_TELETRABAJADOR IN (0, 1)))';
        DBMS_OUTPUT.PUT_LINE('ES_TELETRABAJADOR -> agregada (default 0).');
    ELSE
        DBMS_OUTPUT.PUT_LINE('ES_TELETRABAJADOR ya existe. Sin cambios.');
    END IF;

    COMMIT;
    DBMS_OUTPUT.PUT_LINE('V012_28 finalizado correctamente.');
END;
/
