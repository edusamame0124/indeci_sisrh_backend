-- Spec 006: columnas de baja lógica en catálogos (ejecutar en GESTIONRRHH / Oracle).
-- Revisar nombres de secuencia/identity según estándar local antes de aplicar.

ALTER TABLE GESTIONRRHH.BANKS ADD ACTIVO NUMBER(1) DEFAULT 1 NOT NULL;
ALTER TABLE GESTIONRRHH.BANK_ACCOUNT_TYPES ADD ACTIVO NUMBER(1) DEFAULT 1 NOT NULL;
