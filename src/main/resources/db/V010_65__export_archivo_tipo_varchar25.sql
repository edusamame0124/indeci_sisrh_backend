-- ============================================================================
-- V010_65 — Amplía INDECI_EXPORT_ARCHIVO.TIPO_ARCHIVO de VARCHAR2(12) a
--           VARCHAR2(25) para acomodar los tipos XLSX agregados en V010_59/60.
--
-- V010_29 creó la columna con VARCHAR2(12) — suficiente para PLAME_*/MCPP_*.
-- V010_59 añadió 'XLSX_PLANILLA'      (13 chars) al CHECK constraint.
-- V010_60 añadió 'XLSX_CAS_CONSOLIDADA' (20 chars) al CHECK constraint.
-- Ambas scripts olvidaron ampliar la columna → ORA-12899 al insertar historial.
--
-- VARCHAR2(25) cubre todos los valores actuales con margen:
--   PLAME_REM(8), PLAME_JOR(8), PLAME_SNL(8), MCPP_01(7), MCPP_03(7),
--   MCPP_12(7), XLSX_PLANILLA(13), XLSX_CAS_CONSOLIDADA(20).
--
-- Idempotente: ALTER COLUMN en Oracle ampliar tamaño nunca es destructivo.
-- Ejecutar en GESTIONRRHH / Oracle 19c+.
-- ============================================================================

SET SERVEROUTPUT ON;

BEGIN
    EXECUTE IMMEDIATE
        'ALTER TABLE GESTIONRRHH.INDECI_EXPORT_ARCHIVO '
        || 'MODIFY (TIPO_ARCHIVO VARCHAR2(25 CHAR))';
    DBMS_OUTPUT.PUT_LINE('TIPO_ARCHIVO -> ampliado a VARCHAR2(25). OK.');
    COMMIT;
END;
/
