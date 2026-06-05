-- ============================================================================
-- V010_52 — Seed de INDECI_TIPO_PERSONAL (catálogo creado en V010_51).
--
-- Tipos/grupos ocupacionales típicos del sector público peruano (D.Leg. 276,
-- Ley 30057 Servicio Civil y categorías de uso común en planilla/AIRHSP).
-- Idempotente: MERGE por CODIGO (re-ejecutable; actualiza el nombre si cambia).
--
-- NOTA RR. HH.: ajustar/depurar según la clasificación oficial de la entidad
-- (puede unificarse con la categoría AIRHSP si difiere). No afecta el motor.
-- Ejecutar en GESTIONRRHH / Oracle 19c+ DESPUÉS de V010_51.
-- ============================================================================

SET SERVEROUTPUT ON;

MERGE INTO INDECI_TIPO_PERSONAL d
USING (
    SELECT 'FUNCIONARIO' AS CODIGO, 'Funcionario o Directivo'              AS NOMBRE FROM DUAL UNION ALL
    SELECT 'CONFIANZA',             'Empleado de confianza'                           FROM DUAL UNION ALL
    SELECT 'PROFESIONAL',           'Profesional'                                     FROM DUAL UNION ALL
    SELECT 'TECNICO',               'Técnico'                                         FROM DUAL UNION ALL
    SELECT 'AUXILIAR',              'Auxiliar / Asistencial'                          FROM DUAL UNION ALL
    SELECT 'SERVIDOR_CARRERA',      'Servidor civil de carrera (Ley 30057)'           FROM DUAL UNION ALL
    SELECT 'ACT_COMPLEMENT',        'Servidor de actividades complementarias'         FROM DUAL UNION ALL
    SELECT 'PRACTICANTE',           'Practicante (modalidad formativa)'               FROM DUAL UNION ALL
    -- Subgrupos SERVIR (Ley 30057 / RD0111-2021-EF/53.01) — el motor mapea estos
    -- códigos a la compensación base L001–L004 del servidor civil.
    SELECT 'FUNCIONARIO_PUBLICO',        'SERVIR: Funcionario público'                FROM DUAL UNION ALL
    SELECT 'DIRECTIVO_PUBLICO',          'SERVIR: Directivo público'                  FROM DUAL UNION ALL
    SELECT 'SERVIDOR_CIVIL_CARRERA',     'SERVIR: Servidor civil de carrera'          FROM DUAL UNION ALL
    SELECT 'ACT_COMPLEMENTARIAS',        'SERVIR: Servidor de actividades complementarias' FROM DUAL
) s
ON ( d.CODIGO = s.CODIGO )
WHEN MATCHED THEN UPDATE SET d.NOMBRE = s.NOMBRE
WHEN NOT MATCHED THEN INSERT (CODIGO, NOMBRE) VALUES (s.CODIGO, s.NOMBRE);

COMMIT;
