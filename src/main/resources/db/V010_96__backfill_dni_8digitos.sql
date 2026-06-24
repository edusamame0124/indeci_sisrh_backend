-- ============================================================================
-- V010_96 — Backfill: normaliza DNI a 8 dígitos (recupera ceros a la izquierda)
--
-- CONTEXTO: una migración previa guardó algunos DNI sin sus ceros a la izquierda
--   (p. ej. "123456" en vez de "00123456"). El DNI peruano es de 8 dígitos; un
--   valor corto rompe el prerequisito "Datos personales" del flujo de empleado y
--   también PLAME / boletas / archivos de banco-SUNAT.
--
-- QUÉ HACE: rellena con ceros a la izquierda los DNI NUMÉRICOS de 1–7 dígitos →
--   LPAD(DNI, 8, '0'). Salvaguardas:
--     * ANTI-COLISIÓN: si el valor resultante ya pertenece a OTRA persona, NO se
--       actualiza y se REPORTA para revisión manual (la tabla tiene UNIQUE en DNI).
--     * IDEMPOTENTE: los DNI de 8 dígitos no se tocan; re-ejecutar es seguro.
--     * El bloque imprime cada cambio (DNI viejo -> nuevo) ANTES de confirmar, para
--       dejar registro (este script NO es auto-reversible — ver rollback).
--
-- SUPUESTO: los DNI numéricos de <8 dígitos son DNI reales que perdieron ceros.
--   Si existieran documentos numéricos cortos que NO sean DNI, revisar el reporte.
--
-- Ejecutar conectado como GESTIONRRHH / Oracle 19c+.
-- ============================================================================

SET SERVEROUTPUT ON;

DECLARE
    v_actualizados NUMBER := 0;
    v_colisiones   NUMBER := 0;
    v_nuevo        VARCHAR2(20);
    v_dup          NUMBER;
BEGIN
    DBMS_OUTPUT.PUT_LINE('--- V010_96: normalización de DNI a 8 dígitos ---');

    FOR r IN (
        SELECT ID, DNI
          FROM GESTIONRRHH.INDECI_PERSONA
         WHERE REGEXP_LIKE(DNI, '^[0-9]{1,7}$')
         ORDER BY ID
    ) LOOP
        v_nuevo := LPAD(r.DNI, 8, '0');

        SELECT COUNT(*)
          INTO v_dup
          FROM GESTIONRRHH.INDECI_PERSONA
         WHERE DNI = v_nuevo
           AND ID <> r.ID;

        IF v_dup > 0 THEN
            v_colisiones := v_colisiones + 1;
            DBMS_OUTPUT.PUT_LINE(
                'COLISIÓN  · ID ' || r.ID || ' · ' || r.DNI || ' -> ' || v_nuevo
                || ' (ya existe en otra persona) · NO actualizado, revisar manualmente.');
        ELSE
            UPDATE GESTIONRRHH.INDECI_PERSONA
               SET DNI = v_nuevo
             WHERE ID = r.ID;
            v_actualizados := v_actualizados + 1;
            DBMS_OUTPUT.PUT_LINE(
                'ACTUALIZADO · ID ' || r.ID || ' · ' || r.DNI || ' -> ' || v_nuevo);
        END IF;
    END LOOP;

    DBMS_OUTPUT.PUT_LINE('---------------------------------------------');
    DBMS_OUTPUT.PUT_LINE('DNI actualizados : ' || v_actualizados);
    DBMS_OUTPUT.PUT_LINE('Colisiones (manual): ' || v_colisiones);

    COMMIT;
    DBMS_OUTPUT.PUT_LINE('V010_96 finalizado correctamente.');
EXCEPTION
    WHEN OTHERS THEN
        ROLLBACK;
        DBMS_OUTPUT.PUT_LINE('ERROR V010_96: ' || SQLERRM);
        RAISE;
END;
/

-- Verificación: ¿quedan DNI numéricos de menos de 8 dígitos? (deberían ser solo colisiones)
SELECT ID, DNI
  FROM GESTIONRRHH.INDECI_PERSONA
 WHERE REGEXP_LIKE(DNI, '^[0-9]{1,7}$')
 ORDER BY ID;
