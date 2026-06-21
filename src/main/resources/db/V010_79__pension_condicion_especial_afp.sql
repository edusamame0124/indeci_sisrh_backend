-- Condición especial AFP en INDECI_EMPLEADO_PENSION
-- Permite registrar trabajadores AFP en situación de retiro 95.5% o pensionistas SPP
-- que NO generan descuento de aporte, comisión ni prima en planilla.
--
-- Valores CONDICION_ESPECIAL_AFP:
--   NO_APLICA     → calcula AFP normal (aporte 10% + comisión + prima)
--   RETIRO_955    → sin descuento AFP; exige sustento documental (Art. 40 SPP)
--   PENSIONISTA_SPP → sin descuento AFP; exige sustento documental

ALTER TABLE GESTIONRRHH.INDECI_EMPLEADO_PENSION
ADD (
    CONDICION_ESPECIAL_AFP    VARCHAR2(30)  DEFAULT NULL,
    FECHA_CONDICION_AFP       DATE          DEFAULT NULL,
    DOCUMENTO_SUSTENTO_ID     NUMBER(19)    DEFAULT NULL,
    OBSERVACION_CONDICION_AFP VARCHAR2(500) DEFAULT NULL
);

ALTER TABLE GESTIONRRHH.INDECI_EMPLEADO_PENSION
ADD CONSTRAINT CHK_PENSION_CONDICION_AFP
CHECK (CONDICION_ESPECIAL_AFP IN ('NO_APLICA', 'RETIRO_955', 'PENSIONISTA_SPP'));

-- Backfill: pensiones AFP activas y anteriores -> NO_APLICA (comportamiento actual sin cambio)
UPDATE GESTIONRRHH.INDECI_EMPLEADO_PENSION
SET    CONDICION_ESPECIAL_AFP = 'NO_APLICA'
WHERE  TIPO_REGIMEN = 'AFP';

COMMIT;
