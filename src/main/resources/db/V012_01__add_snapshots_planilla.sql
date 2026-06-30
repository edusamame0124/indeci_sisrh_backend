-- ==============================================================================
-- V012_01__add_snapshots_planilla.sql
-- Fase 4: Auditoría e inmutabilidad de Boleta de Pago.
-- Añade campos snapshot a la cabecera de la planilla.
-- ==============================================================================

ALTER TABLE GESTIONRRHH.INDECI_MOVIMIENTO_PLANILLA
    ADD (
        REGIMEN_LABORAL_SNAPSHOT VARCHAR2(100 CHAR),
        NIVEL_REMUNERATIVO_SNAPSHOT VARCHAR2(100 CHAR),
        CUENTA_BANCARIA_SNAPSHOT VARCHAR2(50 CHAR),
        MODALIDAD_SNAPSHOT VARCHAR2(100 CHAR)
    );

COMMENT ON COLUMN GESTIONRRHH.INDECI_MOVIMIENTO_PLANILLA.REGIMEN_LABORAL_SNAPSHOT IS 'Snapshot del régimen laboral al momento de generar la planilla';
COMMENT ON COLUMN GESTIONRRHH.INDECI_MOVIMIENTO_PLANILLA.NIVEL_REMUNERATIVO_SNAPSHOT IS 'Snapshot del nivel remunerativo o escala base';
COMMENT ON COLUMN GESTIONRRHH.INDECI_MOVIMIENTO_PLANILLA.CUENTA_BANCARIA_SNAPSHOT IS 'Snapshot de la cuenta bancaria donde se depositó';
COMMENT ON COLUMN GESTIONRRHH.INDECI_MOVIMIENTO_PLANILLA.MODALIDAD_SNAPSHOT IS 'Snapshot de la modalidad o subgrupo SERVIR';
