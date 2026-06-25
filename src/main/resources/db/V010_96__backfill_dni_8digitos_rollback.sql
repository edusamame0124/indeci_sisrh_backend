-- ============================================================================
-- Rollback V010_96 — NO es auto-reversible.
--
-- V010_96 rellenó DNI cortos con ceros a la izquierda (LPAD a 8). NO se puede
-- revertir automáticamente sin corromper DNI legítimos que empiezan con cero
-- (p. ej. "00123456" que SIEMPRE tuvo 8 dígitos): quitar ceros a la izquierda a
-- ciegas afectaría a esos también.
--
-- Para revertir: usar el reporte impreso por V010_96 (líneas "ACTUALIZADO · ID x
-- · viejo -> nuevo") o un respaldo de INDECI_PERSONA tomado antes de aplicar, y
-- restaurar los DNI uno por uno:
--
--   UPDATE GESTIONRRHH.INDECI_PERSONA SET DNI = '<viejo>' WHERE ID = <id>;
--   COMMIT;
--
-- Recomendación: tomar SIEMPRE un export de INDECI_PERSONA (ID, DNI) antes de
-- ejecutar V010_96.
-- ============================================================================

SET SERVEROUTPUT ON;

BEGIN
    DBMS_OUTPUT.PUT_LINE(
        'V010_96 no es auto-reversible. Restaurar DNI individualmente desde el '
        || 'reporte de V010_96 o desde un respaldo de INDECI_PERSONA.');
END;
/
