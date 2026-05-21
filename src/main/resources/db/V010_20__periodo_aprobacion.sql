-- ============================================================================
-- Spec 011 / V010_20 — Flujo de aprobación de planilla (Etapa 3 · B7)
--
-- Agrega a INDECI_PERIODO_PLANILLA las columnas del paso APROBADO:
--   - NRO_CERT_PRESUP   VARCHAR2(30)  → número de certificación presupuestal
--                                       (Ley 28411 / LEY-05). Obligatorio para
--                                       pasar el período a APROBADO.
--   - FECHA_APROBACION  TIMESTAMP     → momento de la aprobación.
--
-- El campo ESTADO pasa a manejar 4 valores: ABIERTO | EN_REVISION | APROBADO |
-- CERRADO. No tenía CHECK constraint, así que no se altera (solo crece el dominio).
--
-- DEFENSA EN PROFUNDIDAD: idempotente (add_column_if_missing).
-- Ejecutar en esquema GESTIONRRHH / Oracle 19c+.
-- ============================================================================

SET SERVEROUTPUT ON;

DECLARE
    PROCEDURE add_column_if_missing(
        p_col_name VARCHAR2,
        p_col_ddl  VARCHAR2
    ) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*)
          INTO v_exists
          FROM ALL_TAB_COLUMNS
         WHERE OWNER       = 'GESTIONRRHH'
           AND TABLE_NAME  = 'INDECI_PERIODO_PLANILLA'
           AND COLUMN_NAME = p_col_name;

        IF v_exists = 0 THEN
            EXECUTE IMMEDIATE
                'ALTER TABLE GESTIONRRHH.INDECI_PERIODO_PLANILLA ADD ('
                || p_col_ddl || ')';
            DBMS_OUTPUT.PUT_LINE(p_col_name || ' -> agregada.');
        ELSE
            DBMS_OUTPUT.PUT_LINE(p_col_name || ' ya existe. Sin cambios.');
        END IF;
    END;
BEGIN
    add_column_if_missing(
        'NRO_CERT_PRESUP',
        'NRO_CERT_PRESUP VARCHAR2(30 CHAR)');
    add_column_if_missing(
        'FECHA_APROBACION',
        'FECHA_APROBACION TIMESTAMP');

    EXECUTE IMMEDIATE
        'COMMENT ON COLUMN GESTIONRRHH.INDECI_PERIODO_PLANILLA.NRO_CERT_PRESUP IS ' ||
        '''Spec 011 / LEY-05 (Ley 28411): número de certificación presupuestal. ' ||
        'Obligatorio para que el período pase a APROBADO.''';
    EXECUTE IMMEDIATE
        'COMMENT ON COLUMN GESTIONRRHH.INDECI_PERIODO_PLANILLA.FECHA_APROBACION IS ' ||
        '''Spec 011: fecha/hora en que el período pasó a APROBADO.''';
    EXECUTE IMMEDIATE
        'COMMENT ON COLUMN GESTIONRRHH.INDECI_PERIODO_PLANILLA.ESTADO IS ' ||
        '''ABIERTO | EN_REVISION | APROBADO | CERRADO — ciclo de vida de la planilla (Spec 011).''';

    DBMS_OUTPUT.PUT_LINE('V010_20 finalizado.');
END;
/
