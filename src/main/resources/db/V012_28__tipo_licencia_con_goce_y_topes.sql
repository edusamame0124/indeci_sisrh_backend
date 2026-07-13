-- ============================================================================
-- SPEC_VACACIONES F9.2 — Reestructuración de Licencias (con goce / sin goce).
--
-- Completa el catálogo INDECI_TIPO_LICENCIA para el formulario único de licencia:
--   1) Columna MAX_DIAS  → tope de días permitido por motivo (NULL = sin tope).
--                          Fuente normativa en BD (REGLA-02), no en código.
--   2) Siembra los 13 motivos CON GOCE (ES_SIN_GOCE=0). Topes:
--        Pre y post natal ..... 98 (D.S. 013-2019-PCM / Ley 30367)
--        Adopción ............. 30 (Ley 27409)
--        Cuidado familiar ......  7 (Ley 30012)
--        Onomástico ...........  1
--        (resto → NULL = sin tope predefinido)
--   3) Reconcilia SIN GOCE a solo 2 subtipos vigentes (desactiva los 5 legacy):
--        LIC_SIN_JUS  "Por causas justificadas o motivos personales"
--        LIC_SIN_OTR  "Otros motivos"  (el detalle se captura en MOTIVO de la papeleta)
--
-- IMPORTANTE (ORA-00904): PL/SQL compila TODO el bloque antes de ejecutar. Como
-- MAX_DIAS se agrega en runtime, los DML que la referencian deben ser DINÁMICOS
-- (EXECUTE IMMEDIATE). Idempotente (UPSERT por CODIGO). Oracle 19c+ / GESTIONRRHH.
-- ============================================================================

SET SERVEROUTPUT ON;

DECLARE
    PROCEDURE add_column_if_missing(p_column VARCHAR2, p_ddl VARCHAR2) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_exists FROM ALL_TAB_COLUMNS
         WHERE OWNER='GESTIONRRHH' AND TABLE_NAME='INDECI_TIPO_LICENCIA' AND COLUMN_NAME=p_column;
        IF v_exists = 0 THEN
            EXECUTE IMMEDIATE p_ddl;
            DBMS_OUTPUT.PUT_LINE('INDECI_TIPO_LICENCIA.'||p_column||' -> agregada.');
        ELSE
            DBMS_OUTPUT.PUT_LINE('INDECI_TIPO_LICENCIA.'||p_column||' ya existe.');
        END IF;
    END;

    -- UPSERT por CODIGO. DML dinámico (referencia MAX_DIAS, columna nueva).
    PROCEDURE upsert_tipo(
        p_codigo    VARCHAR2, p_nombre  VARCHAR2,
        p_sin_goce  NUMBER,   p_req_reso NUMBER,
        p_cod_plame VARCHAR2, p_max_dias NUMBER,
        p_activo    NUMBER DEFAULT 1
    ) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_exists
          FROM GESTIONRRHH.INDECI_TIPO_LICENCIA WHERE CODIGO = p_codigo;
        IF v_exists = 0 THEN
            EXECUTE IMMEDIATE
                'INSERT INTO GESTIONRRHH.INDECI_TIPO_LICENCIA '
                || '(NOMBRE, ACTIVO, CODIGO, ES_SIN_GOCE, REQUIERE_RESOLUCION, COD_PLAME_SUNAT, MAX_DIAS) '
                || 'VALUES (:1, :2, :3, :4, :5, :6, :7)'
                USING p_nombre, p_activo, p_codigo, p_sin_goce, p_req_reso, p_cod_plame, p_max_dias;
        ELSE
            EXECUTE IMMEDIATE
                'UPDATE GESTIONRRHH.INDECI_TIPO_LICENCIA '
                || 'SET NOMBRE = :1, ACTIVO = :2, ES_SIN_GOCE = :3, REQUIERE_RESOLUCION = :4, '
                || '    COD_PLAME_SUNAT = :5, MAX_DIAS = :6 '
                || 'WHERE CODIGO = :7'
                USING p_nombre, p_activo, p_sin_goce, p_req_reso, p_cod_plame, p_max_dias, p_codigo;
        END IF;
    END;
BEGIN
    -- 1) Columna nueva (idempotente).
    add_column_if_missing('MAX_DIAS',
        'ALTER TABLE GESTIONRRHH.INDECI_TIPO_LICENCIA ADD (MAX_DIAS NUMBER)');

    -- 2) Motivos CON GOCE (ES_SIN_GOCE=0, sin resolución obligatoria por default).
    --    COD_PLAME_SUNAT se deja NULL (Tabla 21 pendiente de mapeo por RR.HH.; no inventar).
    upsert_tipo('LIC_CG_ENF', 'Por enfermedad o accidente grave del servidor civil',                 0, 0, NULL, NULL);
    upsert_tipo('LIC_CG_INV', 'Por invalidez temporal del servidor civil',                            0, 0, NULL, NULL);
    upsert_tipo('LIC_CG_MAT', 'Por descanso pre y post natal de la servidora gestante',               0, 0, NULL, 98);
    upsert_tipo('LIC_CG_PAT', 'Por paternidad',                                                        0, 0, NULL, NULL);
    upsert_tipo('LIC_CG_ADO', 'Por adopción',                                                          0, 0, NULL, 30);
    upsert_tipo('LIC_CG_FAL', 'Por fallecimiento de cónyuge, concubino(a), padres, hijos o hermanos',  0, 0, NULL, NULL);
    upsert_tipo('LIC_CG_SIN', 'Por pertenecer al sindicato (dirigentes sindicales)',                   0, 0, NULL, NULL);
    upsert_tipo('LIC_CG_CUI', 'Por cuidado de familiar directo grave o terminal (Ley N° 30012)',       0, 0, NULL, 7);
    upsert_tipo('LIC_CG_CAP', 'Por capacitación autorizada (Plan de Desarrollo de las Personas)',      0, 0, NULL, NULL);
    upsert_tipo('LIC_CG_EDI', 'Por función edil',                                                      0, 0, NULL, NULL);
    upsert_tipo('LIC_CG_ONO', 'Por onomástico',                                                        0, 0, NULL, 1);
    upsert_tipo('LIC_CG_DIS', 'Para asistencia médica y terapia de rehabilitación (Ley N° 30119)',     0, 0, NULL, NULL);
    upsert_tipo('LIC_CG_LEY', 'Otras establecidas por Ley',                                            0, 0, NULL, NULL);

    -- 3) SIN GOCE vigentes: solo 2 subtipos (ES_SIN_GOCE=1, resta récord/días laborados).
    upsert_tipo('LIC_SIN_JUS', 'Por causas justificadas o motivos personales', 1, 1, '05', NULL);
    upsert_tipo('LIC_SIN_OTR', 'Otros motivos',                                 1, 1, '05', NULL);

    -- 4) Desactivar los 5 subtipos SIN GOCE legacy (V012_24). Las papeletas históricas
    --    conservan su tipo; solo dejan de ofrecerse en el select (ACTIVO=0).
    EXECUTE IMMEDIATE q'[UPDATE GESTIONRRHH.INDECI_TIPO_LICENCIA
                            SET ACTIVO = 0
                          WHERE CODIGO IN ('LIC_SIN_PAR','LIC_SIN_CAP','LIC_SIN_CON','LIC_SIN_ENF','LIC_SIN_PUB')]';

    -- 5) Normalizar NULLs a 0 en flags (defensivo).
    EXECUTE IMMEDIATE 'UPDATE GESTIONRRHH.INDECI_TIPO_LICENCIA SET ES_SIN_GOCE = 0 WHERE ES_SIN_GOCE IS NULL';
    EXECUTE IMMEDIATE 'UPDATE GESTIONRRHH.INDECI_TIPO_LICENCIA SET REQUIERE_RESOLUCION = 0 WHERE REQUIERE_RESOLUCION IS NULL';

    COMMIT;
    DBMS_OUTPUT.PUT_LINE('V012_28 finalizado.');
END;
/
