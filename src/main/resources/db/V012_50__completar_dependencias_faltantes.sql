-- ============================================================================
-- V012_50 — INDECI_DEPENDENCIA: completa el catálogo con las unidades que
-- faltaban frente al organigrama real (visto en INDECI_OFICINA, 74 filas).
--
-- Origen del hallazgo: el PDF de papeletas no mostraba "Dependencia" para
-- ciertos empleados porque INDECI_EMPLEADO_PUESTO_HIST.DEPENDENCIA_ID quedaba
-- NULL — 654 filas activas en ese estado (import de Vinculación, ~617 filas,
-- nunca mapeó Dependencia, solo Oficina). Al auditar el catálogo se detectó
-- que INDECI_DEPENDENCIA (16 filas) es SOLO el primer nivel del organigrama
-- (Gerencia General, 5 Oficinas de línea, COEN, 4 Direcciones, 5 Unidades) y
-- le faltan unidades que sí existen operativamente en INDECI_OFICINA:
--   - JEFATURA INSTITUCIONAL, ÓRGANO DE CONTROL INSTITUCIONAL (OCI),
--     OFICINA DE COOPERACIÓN Y ASUNTOS INTERNACIONALES (OCAI)
--   - La unidad "paraguas" de coordinación de las Direcciones Desconcentradas
--     (Oficina ids 18/57: "Dirección Desconcentrada INDECI" / "UF GEDES")
--   - 25 Direcciones Desconcentradas regionales (una por región; en
--     INDECI_OFICINA aparecen con nombres/variantes duplicadas por región,
--     ese problema de la OFICINA NO se toca aquí — solo se crea el padre
--     Dependencia correcto una vez por región)
--
-- Decisión RR.HH./Organización (2026-08-04): una Dependencia por región
-- (no una sola "DIRECCION DESCONCENTRADA" genérica), sigla DD-<REGION>.
--
-- INDECI_DEPENDENCIA.ID es GENERATED ALWAYS AS IDENTITY en la BD real (ORA-32795
-- confirmado al probar), aunque la entidad Dependencia.java no tiene
-- @GeneratedValue (desfase entidad/BD ya conocido en el proyecto — no se toca
-- la entidad aquí, no hace falta para este script). El INSERT no especifica ID.
--
-- Este script SOLO crea el catálogo. NO reasigna DEPENDENCIA_ID a los 654
-- puestos existentes (backfill) ni corrige el import de Vinculación — eso
-- va en un script/fix aparte una vez este catálogo esté confirmado.
--
-- IDEMPOTENTE: solo inserta si no existe ya una fila con el mismo NOMBRE.
-- ============================================================================
SET SERVEROUTPUT ON;

DECLARE
    TYPE t_dependencia IS RECORD (
        nombre VARCHAR2(200),
        sigla  VARCHAR2(50));
    TYPE t_dependencias IS TABLE OF t_dependencia;

    v_deps t_dependencias := t_dependencias(
        t_dependencia('JEFATURA INSTITUCIONAL',                         'JEFATURA'),
        t_dependencia('ORGANO DE CONTROL INSTITUCIONAL',                 'OCI'),
        t_dependencia('OFICINA DE COOPERACION Y ASUNTOS INTERNACIONALES','OCAI'),
        t_dependencia('UNIDAD FUNCIONAL DE GESTION DESCONCENTRADA',      'UF GEDES'),
        t_dependencia('DIRECCION DESCONCENTRADA DE AMAZONAS',            'DD-AMAZONAS'),
        t_dependencia('DIRECCION DESCONCENTRADA DE ANCASH',              'DD-ANCASH'),
        t_dependencia('DIRECCION DESCONCENTRADA DE APURIMAC',            'DD-APURIMAC'),
        t_dependencia('DIRECCION DESCONCENTRADA DE AREQUIPA',            'DD-AREQUIPA'),
        t_dependencia('DIRECCION DESCONCENTRADA DE AYACUCHO',            'DD-AYACUCHO'),
        t_dependencia('DIRECCION DESCONCENTRADA DE CAJAMARCA',           'DD-CAJAMARCA'),
        t_dependencia('DIRECCION DESCONCENTRADA DE CALLAO',              'DD-CALLAO'),
        t_dependencia('DIRECCION DESCONCENTRADA DE CUSCO',               'DD-CUSCO'),
        t_dependencia('DIRECCION DESCONCENTRADA DE HUANCAVELICA',        'DD-HUANCAVELICA'),
        t_dependencia('DIRECCION DESCONCENTRADA DE HUANUCO',             'DD-HUANUCO'),
        t_dependencia('DIRECCION DESCONCENTRADA DE ICA',                 'DD-ICA'),
        t_dependencia('DIRECCION DESCONCENTRADA DE JUNIN',               'DD-JUNIN'),
        t_dependencia('DIRECCION DESCONCENTRADA DE LA LIBERTAD',         'DD-LA_LIBERTAD'),
        t_dependencia('DIRECCION DESCONCENTRADA DE LAMBAYEQUE',          'DD-LAMBAYEQUE'),
        t_dependencia('DIRECCION DESCONCENTRADA DE LIMA',                'DD-LIMA'),
        t_dependencia('DIRECCION DESCONCENTRADA DE LORETO',              'DD-LORETO'),
        t_dependencia('DIRECCION DESCONCENTRADA DE MADRE DE DIOS',       'DD-MADRE_DE_DIOS'),
        t_dependencia('DIRECCION DESCONCENTRADA DE MOQUEGUA',            'DD-MOQUEGUA'),
        t_dependencia('DIRECCION DESCONCENTRADA DE PASCO',               'DD-PASCO'),
        t_dependencia('DIRECCION DESCONCENTRADA DE PIURA',               'DD-PIURA'),
        t_dependencia('DIRECCION DESCONCENTRADA DE PUNO',                'DD-PUNO'),
        t_dependencia('DIRECCION DESCONCENTRADA DE SAN MARTIN',          'DD-SAN_MARTIN'),
        t_dependencia('DIRECCION DESCONCENTRADA DE TACNA',               'DD-TACNA'),
        t_dependencia('DIRECCION DESCONCENTRADA DE TUMBES',              'DD-TUMBES'),
        t_dependencia('DIRECCION DESCONCENTRADA DE UCAYALI',             'DD-UCAYALI'));

    v_existe NUMBER;
    v_id     NUMBER;
BEGIN
    FOR i IN 1 .. v_deps.COUNT LOOP
        SELECT COUNT(*) INTO v_existe
          FROM GESTIONRRHH.INDECI_DEPENDENCIA
         WHERE UPPER(TRIM(NOMBRE)) = UPPER(TRIM(v_deps(i).nombre));

        IF v_existe = 0 THEN
            -- ID es GENERATED ALWAYS AS IDENTITY en la tabla real (ORA-32795 si se
            -- especifica) — la entidad Dependencia.java no lo refleja (desfase
            -- entidad/BD ya conocido en el proyecto). Se omite de la lista de columnas
            -- y se recupera el valor autogenerado con RETURNING INTO.
            INSERT INTO GESTIONRRHH.INDECI_DEPENDENCIA (NOMBRE, SIGLA, OFICINA_ID)
            VALUES (v_deps(i).nombre, v_deps(i).sigla, NULL)
            RETURNING ID INTO v_id;

            DBMS_OUTPUT.PUT_LINE('Dependencia agregada: ' || v_deps(i).nombre
                    || ' (ID ' || v_id || ', SIGLA ' || v_deps(i).sigla || ')');
        ELSE
            DBMS_OUTPUT.PUT_LINE('Dependencia ya existe: ' || v_deps(i).nombre);
        END IF;
    END LOOP;
END;
/

COMMIT;

-- Verificación: deben quedar 45 dependencias (16 originales + 29 nuevas).
SELECT ID, NOMBRE, SIGLA
  FROM GESTIONRRHH.INDECI_DEPENDENCIA
 ORDER BY ID;
