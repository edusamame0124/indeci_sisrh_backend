-- ============================================================================
-- V010_90 subsidio retiro eventos (P0-F0)
--
-- Objetivo:
--   Desactivar tipos de evento ENFERMEDAD/MATERNIDAD del flujo operativo.
--   Los subsidios migran al modulo dedicado /asistencia/subsidios (F1+).
--   Columnas maternidad en INDECI_EMPLEADO_EVENTO quedan deprecated (no DROP).
--   Tablas legacy INDECI_SUBSIDIO_EVENTO_CALCULO / INDECI_EVENTO_DISTRIBUCION_MES
--   permanecen read-only hasta migracion de datos historicos.
--
-- Idempotente: re-ejecutable sin efectos duplicados.
-- ============================================================================

SET SERVEROUTPUT ON;

UPDATE INDECI_TIPO_EVENTO
   SET ACTIVO = 0
 WHERE CODIGO IN ('MATERNIDAD', 'ENFERMEDAD')
   AND NVL(ACTIVO, 0) = 1;

COMMIT;

BEGIN
    EXECUTE IMMEDIATE q'[
        COMMENT ON COLUMN INDECI_EMPLEADO_EVENTO.DURACION_LEGAL IS
        'DEPRECATED P0-F0: maternidad movida a modulo subsidios. Solo lectura historica.'
    ]';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -904 THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE q'[
        COMMENT ON COLUMN INDECI_EMPLEADO_EVENTO.MOTIVO_EXTENSION IS
        'DEPRECATED P0-F0: maternidad movida a modulo subsidios. Solo lectura historica.'
    ]';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -904 THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE q'[
        COMMENT ON COLUMN INDECI_EMPLEADO_EVENTO.FECHA_PROBABLE_PARTO IS
        'DEPRECATED P0-F0: maternidad movida a modulo subsidios. Solo lectura historica.'
    ]';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -904 THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE q'[
        COMMENT ON COLUMN INDECI_EMPLEADO_EVENTO.DIFIERE_PRENATAL_POSTNATAL IS
        'DEPRECATED P0-F0: maternidad movida a modulo subsidios. Solo lectura historica.'
    ]';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -904 THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE q'[
        COMMENT ON COLUMN INDECI_EMPLEADO_EVENTO.TIPO_DOCUMENTO IS
        'DEPRECATED P0-F0: CITT/documento subsidio movido a modulo subsidios. Solo lectura historica.'
    ]';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -904 THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE q'[
        COMMENT ON COLUMN INDECI_EMPLEADO_EVENTO.NRO_CITT IS
        'DEPRECATED P0-F0: CITT movido a modulo subsidios. Solo lectura historica.'
    ]';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -904 THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE q'[
        COMMENT ON COLUMN INDECI_EMPLEADO_EVENTO.FECHA_EMISION_DOC IS
        'DEPRECATED P0-F0: documento subsidio movido a modulo subsidios. Solo lectura historica.'
    ]';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -904 THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE q'[
        COMMENT ON TABLE INDECI_EVENTO_DISTRIBUCION_MES IS
        'DEPRECATED P0-F0: distribucion mensual subsidio movida a INDECI_SUBSIDIO_TRAMO (F1). Solo lectura historica.'
    ]';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -942 THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE q'[
        COMMENT ON TABLE INDECI_SUBSIDIO_EVENTO_CALCULO IS
        'LEGACY read-only P0-F0: calculos por evento reemplazados por INDECI_SUBSIDIO_LIQUIDACION (F2).'
    ]';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -942 THEN RAISE; END IF;
END;
/

DBMS_OUTPUT.PUT_LINE('V010_90: tipos evento subsidio desactivados; columnas/tablas legacy marcadas deprecated.');
