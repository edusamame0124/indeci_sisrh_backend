-- V010_78: Agrega TIPO_ORIGEN a INDECI_META_PPTO_EQUIV
-- Distingue si la equivalencia fue detectada automáticamente (AUTOMATICO) o ingresada a mano (MANUAL).
ALTER TABLE GESTIONRRHH.INDECI_META_PPTO_EQUIV
    ADD TIPO_ORIGEN VARCHAR2(20) DEFAULT 'MANUAL';

COMMENT ON COLUMN GESTIONRRHH.INDECI_META_PPTO_EQUIV.TIPO_ORIGEN
    IS 'AUTOMATICO = detectado por coincidencia estructural; MANUAL = ingresado por analista';
