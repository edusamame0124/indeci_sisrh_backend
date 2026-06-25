-- ============================================================================
-- SPEC_CONCEPTOS_PLANILLA P5 / V010_105 — Backfill de "Aplicabilidad por tipo de
-- planilla" para conceptos existentes.
--
-- Fase A (V010_102) añadió la asociación M:N concepto ↔ tipo de planilla con la
-- regla de negocio "≥1 planilla por concepto". Los conceptos ya existentes (seed
-- MEF + técnicos) quedaron SIN asociación, lo que genera fricción al editarlos
-- (el wizard exigiría asignar una) y "—" en la columna de la lista.
--
-- Este script asocia a la planilla por defecto 'CAS' a todo concepto que aún no
-- tenga ninguna asociación. IDEMPOTENTE (WHERE NOT EXISTS): re-ejecutar no duplica.
-- No toca conceptos que ya tengan al menos una planilla asignada.
--
-- Solo datos (sin DDL ⇒ sin TABLESPACE). Ejecutar en GESTIONRRHH / Oracle 19c+
-- (después de V010_102).
-- ============================================================================

SET SERVEROUTPUT ON;

DECLARE
    v_filas NUMBER;
BEGIN
    INSERT INTO GESTIONRRHH.INDECI_CONCEPTO_PLANILLA_TIPO
        (CONCEPTO_PLANILLA_ID, PLANILLA_TIPO_CODIGO)
    SELECT c.ID, 'CAS'
      FROM GESTIONRRHH.INDECI_CONCEPTO_PLANILLA c
     WHERE NOT EXISTS (
            SELECT 1
              FROM GESTIONRRHH.INDECI_CONCEPTO_PLANILLA_TIPO t
             WHERE t.CONCEPTO_PLANILLA_ID = c.ID
           );

    v_filas := SQL%ROWCOUNT;
    DBMS_OUTPUT.PUT_LINE('Backfill Aplicabilidad (CAS): ' || v_filas || ' concepto(s) asociado(s).');
    COMMIT;
END;
/

-- Verificación: conceptos sin ninguna planilla (esperado 0 tras el backfill):
-- SELECT COUNT(*) FROM GESTIONRRHH.INDECI_CONCEPTO_PLANILLA c
--  WHERE NOT EXISTS (SELECT 1 FROM GESTIONRRHH.INDECI_CONCEPTO_PLANILLA_TIPO t
--                     WHERE t.CONCEPTO_PLANILLA_ID = c.ID);
