-- B2 — Trazabilidad: FK de movimiento de planilla hacia los parámetros previsionales
-- usados en el cálculo. Permite auditar qué vigencia AFP/ONP estaba activa cuando
-- se generó la planilla y, al cerrar el período, bloquear esas vigencias.
-- Columnas nullable: movimientos históricos (antes de V010_71) no tendrán valor.

ALTER TABLE GESTIONRRHH.INDECI_MOVIMIENTO_PLANILLA
    ADD AFP_PARAM_VIGENCIA_ID NUMBER(19) NULL;

ALTER TABLE GESTIONRRHH.INDECI_MOVIMIENTO_PLANILLA
    ADD ONP_PARAM_VIGENCIA_ID NUMBER(19) NULL;

COMMENT ON COLUMN GESTIONRRHH.INDECI_MOVIMIENTO_PLANILLA.AFP_PARAM_VIGENCIA_ID
    IS 'FK a INDECI_AFP_PARAMETRO_VIGENCIA — vigencia AFP activa al generar la planilla';

COMMENT ON COLUMN GESTIONRRHH.INDECI_MOVIMIENTO_PLANILLA.ONP_PARAM_VIGENCIA_ID
    IS 'FK a INDECI_ONP_PARAMETRO_VIGENCIA — vigencia ONP activa al generar la planilla';
