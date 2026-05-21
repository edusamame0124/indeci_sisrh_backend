-- ============================================================================
-- Spec 013 / B3 / V010_27 — Códigos externos del concepto: PLAME y MCPP
--                          (Etapa 3 · B3 · M09 PLAME/PDT 601 + MCPP Web)
--
-- Puente entre el catálogo interno (CODIGO_MEF) y los identificadores que exigen
-- los sistemas externos de declaración/pago:
--   CODIGO_PLAME_SUNAT — código del Anexo 2 SUNAT para el PDT 601 (archivos
--                        .rem / .jor / .snl). Ej: 0601 (rem básica), 0608
--                        (aporte AFP/ONP), 0618 (renta 5ta), 1019 (incremento
--                        por DS), 2039 (descuento no deducible).
--   CODIGO_MCPP        — código del Módulo de Control de Pago de Planillas (MCPP
--                        Web) para los archivos PLL*.TXT. Ej: 0131 (base), 0668
--                        (compensación SERVIR), 0009 (aporte AFP), 0210 (mandato
--                        judicial).
--
-- DECISIONES DE DISEÑO (alineadas con V010_08):
--   - Nullable: no todo concepto tiene equivalente en ambos sistemas (ej: CUC
--     Total y ESSALUD empleador no van a MCPP; conceptos internos sin homólogo
--     SUNAT quedan NULL hasta su validación normativa).
--   - Sin índice ni UNIQUE: el mapeo es muchos-a-uno (varios conceptos comparten
--     un mismo código externo, ej: seguros/cooperativas → PLAME 2039 / MCPP 0075).
--     El lookup de generación parte de CODIGO_MEF; estas columnas se LEEN.
--   - Sin CHECK de formato: los códigos mezclan longitudes (3-4 díg.). Validación
--     a nivel app.
--   - El seed de valores (V010_28) se aplica por separado, una vez RRHH remita
--     los CODIGO_MEF oficiales de los 4 conceptos nuevos (LEY-01: no inventar).
--
-- DEFENSA EN PROFUNDIDAD:
--   - Idempotente: si la columna ya existe salta el ALTER.
--   - Solo metadata: ninguna fila se modifica.
--   - Re-ejecución segura.
--
-- Ejecutar en esquema GESTIONRRHH / Oracle 19c+.
-- ============================================================================

SET SERVEROUTPUT ON;

DECLARE
    PROCEDURE add_column_if_missing(
        p_col_name  VARCHAR2,
        p_col_ddl   VARCHAR2
    ) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*)
          INTO v_exists
          FROM ALL_TAB_COLUMNS
         WHERE OWNER       = 'GESTIONRRHH'
           AND TABLE_NAME  = 'INDECI_CONCEPTO_PLANILLA'
           AND COLUMN_NAME = p_col_name;

        IF v_exists = 0 THEN
            EXECUTE IMMEDIATE
                'ALTER TABLE GESTIONRRHH.INDECI_CONCEPTO_PLANILLA ADD ('
                || p_col_ddl || ')';
            DBMS_OUTPUT.PUT_LINE(p_col_name || ' -> agregada.');
        ELSE
            DBMS_OUTPUT.PUT_LINE(p_col_name || ' ya existe. Sin cambios.');
        END IF;
    END;
BEGIN
    -- ------------------------------------------------------------------
    -- CODIGO_PLAME_SUNAT — código Anexo 2 SUNAT (PDT 601 / .rem .jor .snl)
    -- ------------------------------------------------------------------
    add_column_if_missing(
        'CODIGO_PLAME_SUNAT',
        'CODIGO_PLAME_SUNAT VARCHAR2(6 CHAR)');

    -- ------------------------------------------------------------------
    -- CODIGO_MCPP — código MCPP Web (archivos PLL*.TXT)
    -- ------------------------------------------------------------------
    add_column_if_missing(
        'CODIGO_MCPP',
        'CODIGO_MCPP VARCHAR2(6 CHAR)');

    -- ------------------------------------------------------------------
    -- COMMENTS — idempotente (Oracle sobreescribe)
    -- ------------------------------------------------------------------
    EXECUTE IMMEDIATE
        'COMMENT ON COLUMN GESTIONRRHH.INDECI_CONCEPTO_PLANILLA.CODIGO_PLAME_SUNAT IS ' ||
        '''Código del Anexo 2 SUNAT para el PDT 601 (.rem/.jor/.snl). Ej: 0601 (rem básica), 0608 (aporte AFP/ONP), 0611 (comisión AFP), 0606 (prima AFP), 0618 (renta 5ta), 0915 (ESSALUD empleador), 1016 (vac. truncas), 1019 (incremento DS), 1028 (otras asignaciones), 2039 (descuento no deducible), 2046 (inasistencias), 2051 (descuento judicial). NULL = sin homólogo SUNAT o pendiente de validación.''';

    EXECUTE IMMEDIATE
        'COMMENT ON COLUMN GESTIONRRHH.INDECI_CONCEPTO_PLANILLA.CODIGO_MCPP IS ' ||
        '''Código MCPP Web (archivos PLL*.TXT). Ej: 0131 (base CAS/276/728), 0668 (compensación SERVIR), 0009 (aporte AFP), 0008 (comisión AFP), 0010 (prima AFP), 0002 (ONP), 0007 (renta 5ta SERVIR/728), 0006 (renta 5ta CAS), 0210 (mandato judicial), 0067 (cuota sindical), 0075 (cooperativas/banco), 1053 (incremento DS 327). NULL = no va a MCPP (ej: ESSALUD empleador, CUC) o pendiente.''';

    DBMS_OUTPUT.PUT_LINE('V010_27 finalizado.');
END;
/
