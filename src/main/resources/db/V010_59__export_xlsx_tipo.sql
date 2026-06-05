-- ============================================================================
-- V010_59 — Extiende INDECI_EXPORT_ARCHIVO_TIPO_CK para incluir XLSX_PLANILLA.
--
-- El export consolidado XLSX (B1) usa tipo 'XLSX_PLANILLA', que no estaba
-- en el CK original de V010_29/V010_51. Se recrea el constraint.
-- Idempotente: salta si ya existe el valor en el CK o si el CK no existe.
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
        || '''XLSX_PLANILLA''))' -- B1 export consolidado
        || ')';

    DBMS_OUTPUT.PUT_LINE('INDECI_EXPORT_ARCHIVO_TIPO_CK -> recreado con XLSX_PLANILLA.');
    COMMIT;
END;
/
