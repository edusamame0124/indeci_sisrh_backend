-- ============================================================================
-- V010_61 — Amplía INDECI_CALC_SNAPSHOT_REGLA_CK para incluir 'ESSALUD'.
--
-- El motor usa CalculoSnapshotService.REGLA_ESSALUD = "ESSALUD" al registrar
-- el snapshot de EsSalud empleador (P1), pero el CK creado en V010_55 solo
-- admitía: GENERAL | IR4TA_CAS | IR5TA | SUBSIDIO.
-- Resultado: ORA-02290 al generar planilla masiva o individual.
--
-- Idempotente: comprueba existencia del CK antes de operar.
-- Ejecutar en GESTIONRRHH / Oracle 19c+.
-- ============================================================================

SET SERVEROUTPUT ON;

DECLARE
    v_ck_exists NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_ck_exists
      FROM USER_CONSTRAINTS
     WHERE TABLE_NAME      = 'INDECI_CALCULO_SNAPSHOT'
       AND CONSTRAINT_NAME = 'INDECI_CALC_SNAPSHOT_REGLA_CK';

    IF v_ck_exists > 0 THEN
        EXECUTE IMMEDIATE
            'ALTER TABLE GESTIONRRHH.INDECI_CALCULO_SNAPSHOT '
            || 'DROP CONSTRAINT INDECI_CALC_SNAPSHOT_REGLA_CK';
        DBMS_OUTPUT.PUT_LINE('INDECI_CALC_SNAPSHOT_REGLA_CK -> eliminado.');
    END IF;

    EXECUTE IMMEDIATE
        'ALTER TABLE GESTIONRRHH.INDECI_CALCULO_SNAPSHOT ADD ('
        || 'CONSTRAINT INDECI_CALC_SNAPSHOT_REGLA_CK '
        || 'CHECK (REGLA IN ('
        || '''GENERAL'', ''IR4TA_CAS'', ''IR5TA'', ''SUBSIDIO'', ''ESSALUD''))' -- P1 EsSalud empleador
        || ')';

    DBMS_OUTPUT.PUT_LINE('INDECI_CALC_SNAPSHOT_REGLA_CK -> recreado con ESSALUD.');
    COMMIT;
END;
/
