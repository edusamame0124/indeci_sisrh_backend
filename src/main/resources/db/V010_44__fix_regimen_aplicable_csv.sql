-- ============================================================================
-- F1.5b-fix / V010_44 — Relajar INDECI_CONCEPTO_REGIMEN_CK para aceptar CSV
--                       + reintentar UPDATEs DS 265/279/327 que V010_42 no pudo.
--
-- CONTEXTO:
--   V010_42 falló al actualizar los DS con REGIMEN_APLICABLE='728,1057':
--     ORA-02290: GESTIONRRHH.INDECI_CONCEPTO_REGIMEN_CK violada.
--
--   El CK preexistente (creado en V010_02 cuando se agregaron las columnas
--   MEF) solo permite valores únicos: '276', '728', '1057', 'SERVIR', 'TODOS'.
--   No admite CSV como '728,1057' que requiere F1.5b (decisión RRHH para los
--   DS de pacto colectivo MEF: aplican simultáneamente a 728 y 1057).
--
-- ESTRATEGIA:
--   1) DROP CONSTRAINT INDECI_CONCEPTO_REGIMEN_CK (si existe).
--   2) NO recrear un CK rígido — la validación del formato y de los códigos
--      de régimen vive en aplicación (helper regimenAplicaConcepto en
--      GeneradorPlanillaService, F1.5b). Si quisiéramos volver a tener un
--      CK más permisivo, sería con regex (`REGEXP_LIKE(REGIMEN_APLICABLE,
--      '^([0-9A-Z]+,)*[0-9A-Z]+$')`) — pero esto se difiere a F3/UI donde
--      la validación tendrá UX adecuada.
--   3) Reintentar los 3 UPDATEs que fallaron (DS 265/279/327).
--
-- IDEMPOTENTE: el DROP CONSTRAINT verifica primero la existencia. Los UPDATEs
-- son idempotentes por naturaleza (asignan valores fijos a filas concretas).
--
-- Schema NO hardcodeado. Oracle 19c+.
-- ============================================================================

SET SERVEROUTPUT ON;

-- ----------------------------------------------------------------------------
-- 1) DROP CONSTRAINT preexistente si está presente.
-- ----------------------------------------------------------------------------
DECLARE
    v_exists NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_exists
      FROM USER_CONSTRAINTS
     WHERE CONSTRAINT_NAME = 'INDECI_CONCEPTO_REGIMEN_CK';

    IF v_exists = 0 THEN
        DBMS_OUTPUT.PUT_LINE('INDECI_CONCEPTO_REGIMEN_CK no existe. Sin cambios.');
    ELSE
        EXECUTE IMMEDIATE
            'ALTER TABLE INDECI_CONCEPTO_PLANILLA '
            || 'DROP CONSTRAINT INDECI_CONCEPTO_REGIMEN_CK';
        DBMS_OUTPUT.PUT_LINE('INDECI_CONCEPTO_REGIMEN_CK -> dropeado.');
        DBMS_OUTPUT.PUT_LINE(
            '  Razon: CK rigido (valor unico) impedia CSV 728,1057 ' ||
            'que F1.5b requiere para DS de pacto colectivo MEF.'
        );
    END IF;
END;
/

-- ----------------------------------------------------------------------------
-- 2) Reintentar UPDATEs de DS 265/279/327 que V010_42 no pudo aplicar.
--    Mapeo normativo RRHH (Convenios Colectivos Centralizados MEF):
--    los DS aplican simultaneamente a 728 y 1057 (el regimen 276 esta
--    excluido por MUC en AIRHSP).
-- ----------------------------------------------------------------------------
UPDATE INDECI_CONCEPTO_PLANILLA
   SET ES_PRORRATEABLE   = 'S',
       REGIMEN_APLICABLE = '728,1057'
 WHERE CODIGO_MEF = '11051';   -- DS 265-2024-EF

UPDATE INDECI_CONCEPTO_PLANILLA
   SET ES_PRORRATEABLE   = 'S',
       REGIMEN_APLICABLE = '728,1057'
 WHERE CODIGO_MEF = '11053';   -- DS 279-2024-EF

UPDATE INDECI_CONCEPTO_PLANILLA
   SET ES_PRORRATEABLE   = 'S',
       REGIMEN_APLICABLE = '728,1057'
 WHERE CODIGO_MEF = '11214';   -- DS 327-2025-EF

-- DS 311-2022-EF / DS 313-2023-EF: V010_42 busca por nombre. Si no existen
-- en el catalogo, las queries afectan 0 filas (igual antes). RRHH puede
-- crearlos despues con la UI de conceptos.
UPDATE INDECI_CONCEPTO_PLANILLA
   SET ES_PRORRATEABLE   = 'S',
       REGIMEN_APLICABLE = '728,1057'
 WHERE UPPER(NOMBRE) LIKE '%DS 311-2022%'
    OR UPPER(NOMBRE) LIKE '%D.S. 311-2022%';

UPDATE INDECI_CONCEPTO_PLANILLA
   SET ES_PRORRATEABLE   = 'S',
       REGIMEN_APLICABLE = '728,1057'
 WHERE UPPER(NOMBRE) LIKE '%DS 313-2023%'
    OR UPPER(NOMBRE) LIKE '%D.S. 313-2023%';

COMMIT;

-- ----------------------------------------------------------------------------
-- 3) Verificacion final: imprime el estado de los conceptos prorrateables.
-- ----------------------------------------------------------------------------
DECLARE
    v_count NUMBER;
    v_ck_existe NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count
      FROM INDECI_CONCEPTO_PLANILLA
     WHERE ES_PRORRATEABLE = 'S';
    SELECT COUNT(*) INTO v_ck_existe
      FROM USER_CONSTRAINTS
     WHERE CONSTRAINT_NAME = 'INDECI_CONCEPTO_REGIMEN_CK';

    DBMS_OUTPUT.PUT_LINE('---');
    DBMS_OUTPUT.PUT_LINE('CK rigido REGIMEN_APLICABLE          : ' || v_ck_existe || ' (esperado 0 — relajado).');
    DBMS_OUTPUT.PUT_LINE('Conceptos prorrateables total         : ' || v_count);
    DBMS_OUTPUT.PUT_LINE('---');

    FOR r IN (
        SELECT CODIGO_MEF, NOMBRE, REGIMEN_APLICABLE
          FROM INDECI_CONCEPTO_PLANILLA
         WHERE ES_PRORRATEABLE = 'S'
         ORDER BY REGIMEN_APLICABLE, CODIGO_MEF
    ) LOOP
        DBMS_OUTPUT.PUT_LINE(
            RPAD(NVL(r.CODIGO_MEF, '-'), 8) ||
            ' | ' || RPAD(NVL(r.REGIMEN_APLICABLE, '-'), 12) ||
            ' | ' || r.NOMBRE
        );
    END LOOP;
    DBMS_OUTPUT.PUT_LINE('V010_44 listo.');
END;
/
