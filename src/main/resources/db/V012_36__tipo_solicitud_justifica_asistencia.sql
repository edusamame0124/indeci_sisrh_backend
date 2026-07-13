-- ============================================================================
-- V012_36 — Flag JUSTIFICA_ASISTENCIA en INDECI_TIPO_SOLICITUD_RRHH
--
-- OBJETIVO:
--   Parametrizar, por tipo de papeleta, si una solicitud APROBADA que cubre la
--   fecha JUSTIFICA el dia en la carga de asistencia (no genera FALTA ni
--   descuento). Regla de negocio (RR. HH.): solo papeletas CON GOCE + Teletrabajo.
--
--   El motor NO hardcodea la lista (REGLA-02): lee este flag desde BD. RR. HH.
--   mantiene la clasificacion con goce / sin goce marcando 1/0 por tipo.
--
-- SEED CONSERVADOR (solo lo normativamente inequivoco):
--   TELETRABAJO           -> 1 (trabajo efectivo remoto, Ley 31572)
--   008/009 Lactancia     -> 1 (permiso con goce)
--   010 Descanso medico   -> 1 (subsidiado / con goce, no es falta)
--   Resto (001-007, 011,  -> 0 (RR. HH. debe marcar 1 los permisos comunes
--   012, 013)                 CON GOCE que apliquen; 012 vacaciones y 011
--                             licencia tienen su propio tipo de dia).
--
-- Idempotente. Oracle 19c+ / GESTIONRRHH.
-- ============================================================================

SET SERVEROUTPUT ON

DECLARE
    l_cnt NUMBER;
BEGIN
    SELECT COUNT(*) INTO l_cnt
    FROM all_tab_columns
    WHERE owner = 'GESTIONRRHH'
      AND table_name = 'INDECI_TIPO_SOLICITUD_RRHH'
      AND column_name = 'JUSTIFICA_ASISTENCIA';

    IF l_cnt = 0 THEN
        EXECUTE IMMEDIATE
            'ALTER TABLE GESTIONRRHH.INDECI_TIPO_SOLICITUD_RRHH '
            || 'ADD (JUSTIFICA_ASISTENCIA NUMBER(1) DEFAULT 0 NOT NULL)';
        DBMS_OUTPUT.PUT_LINE('Columna JUSTIFICA_ASISTENCIA agregada.');
    ELSE
        DBMS_OUTPUT.PUT_LINE('Columna JUSTIFICA_ASISTENCIA ya existe (omitida).');
    END IF;

    EXECUTE IMMEDIATE
        'COMMENT ON COLUMN GESTIONRRHH.INDECI_TIPO_SOLICITUD_RRHH.JUSTIFICA_ASISTENCIA IS '
        || '''1 = una papeleta aprobada de este tipo que cubre la fecha justifica el dia '
        || '(no descuenta) al cargar asistencia; 0 = no justifica (permiso sin goce -> FALTA).''';

    -- Seed conservador (idempotente por CODIGO). DINAMICO: la columna se crea en
    -- runtime (EXECUTE IMMEDIATE arriba); un UPDATE estatico se validaria al compilar
    -- el bloque -> ORA-00904. Por eso tambien va por EXECUTE IMMEDIATE.
    EXECUTE IMMEDIATE
        'UPDATE GESTIONRRHH.INDECI_TIPO_SOLICITUD_RRHH '
        || '   SET JUSTIFICA_ASISTENCIA = 1 '
        || ' WHERE CODIGO IN (''TELETRABAJO'', ''008'', ''009'', ''010'') '
        || '   AND JUSTIFICA_ASISTENCIA <> 1';
    DBMS_OUTPUT.PUT_LINE('Seed JUSTIFICA_ASISTENCIA aplicado (' || SQL%ROWCOUNT || ' fila[s]).');

    COMMIT;
    DBMS_OUTPUT.PUT_LINE('V012_36 finalizado.');
END;
/
