-- ============================================================================
-- V010_60 — Extiende INDECI_EXPORT_ARCHIVO_TIPO_CK para incluir
--           XLSX_CAS_CONSOLIDADA (Planilla CAS Consolidada — P0).
--
-- La exportación general "Planilla CAS Consolidada" registra su historial con
-- tipo 'XLSX_CAS_CONSOLIDADA'. Se recrea el CK agregando ese valor a los ya
-- existentes (incluido XLSX_PLANILLA de V010_59).
-- Idempotente: si el CK no existe, solo lo crea con la lista completa.
-- Ejecutar en GESTIONRRHH / Oracle 19c+.
-- ============================================================================

SET SERVEROUTPUT ON;

DECLARE
    v_ck_exists NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_ck_exists
      FROM USER_CONSTRAINTS
     WHERE TABLE_NAME       = 'INDECI_EXPORT_ARCHIVO'
       AND CONSTRAINT_NAME  = 'INDECI_EXPORT_ARCHIVO_TIPO_CK';

    IF v_ck_exists > 0 THEN
        EXECUTE IMMEDIATE
            'ALTER TABLE GESTIONRRHH.INDECI_EXPORT_ARCHIVO '
            || 'DROP CONSTRAINT INDECI_EXPORT_ARCHIVO_TIPO_CK';
        DBMS_OUTPUT.PUT_LINE('INDECI_EXPORT_ARCHIVO_TIPO_CK -> eliminado.');
    END IF;

    EXECUTE IMMEDIATE
        'ALTER TABLE GESTIONRRHH.INDECI_EXPORT_ARCHIVO ADD ('
        || 'CONSTRAINT INDECI_EXPORT_ARCHIVO_TIPO_CK '
        || 'CHECK (TIPO_ARCHIVO IN ('
        || '''PLAME_REM'', ''PLAME_JOR'', ''PLAME_SNL'', '
        || '''MCPP_01'', ''MCPP_03'', ''MCPP_12'', '
        || '''XLSX_PLANILLA'', '
        || '''XLSX_CAS_CONSOLIDADA''))' -- P0 Planilla CAS Consolidada
        || ')';

    DBMS_OUTPUT.PUT_LINE('INDECI_EXPORT_ARCHIVO_TIPO_CK -> recreado con XLSX_CAS_CONSOLIDADA.');
    COMMIT;
END;
/
