package com.indeci.rrhh.vinculacion.importacion;

import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Colapsa las variantes del Excel a un valor canónico (Clase B del informe).
 *
 * <p>{@link TextoNormalizador#clave} ya unifica mayúsculas, tildes, NBSP y espacios, así
 * que {@code 'TÉCNICA COMPLETA'} y {@code 'tecnica completa '} llegan aquí como la misma
 * clave. Este diccionario resuelve lo que la normalización <b>no</b> puede deducir:
 * género ({@code Casada}/{@code Casado}), sinónimos ({@code MAGISTER}/{@code MAESTRIA}),
 * typos ({@code BANBINF}) y nombres comerciales ({@code BCP} = Banco de Crédito).
 *
 * <p>Un valor mapeado a {@code null} significa "celda sin dato" ({@code '0'}, {@code '-'}).
 *
 * <p>Es la fuente única de estas equivalencias: si RR.HH. reporta una variante nueva, se
 * agrega aquí y no se toca ninguna regla ni el resolver.
 */
@Component
public class DiccionarioEquivalencias {

    /** Catálogos con variantes conocidas en el archivo de RR.HH. */
    public enum Catalogo {
        REGIMEN_LABORAL,
        ESTADO_CIVIL,
        NACIONALIDAD,
        NIVEL_INSTRUCCION,
        GRADO_ACADEMICO,
        NIVEL_POSGRADO,
        CONDICION_GRADO,
        BANCO,
        DEPENDENCIA
    }

    private static final String SIN_DATO = "";

    private static final Map<Catalogo, Map<String, String>> EQUIVALENCIAS = Map.ofEntries(
            /*
             * CRÍTICO. El Excel escribe 'CAS', pero INDECI_REGIMEN_LABORAL solo tiene
             * CODIGO='1057' / NOMBRE='CONTRATO ADMINISTRATIVO DE SERVICIOS'. Sin este alias
             * ninguna de las ~588 filas CAS resolvería su régimen —el campo que gobierna
             * 5ta/4ta, asignación familiar y topes— y quedaría en null silenciosamente.
             * Ídem 'SERVIR' → 30057 ('SERVICIO CIVIL').
             */
            Map.entry(Catalogo.REGIMEN_LABORAL, Map.of(
                    "CAS", "1057",
                    "1057", "1057",
                    "SERVIR", "30057",
                    "LEY 30057", "30057",
                    "30057", "30057",
                    "276", "276",
                    "728", "728")),

            Map.entry(Catalogo.ESTADO_CIVIL, Map.ofEntries(
                    Map.entry("0", SIN_DATO),
                    Map.entry("CASADA", "CASADO"),
                    Map.entry("CASADO", "CASADO"),
                    Map.entry("CONCUVINA", "CONVIVIENTE"),
                    Map.entry("CONCUVINO", "CONVIVIENTE"),
                    Map.entry("CONVIVIENTE", "CONVIVIENTE"),
                    Map.entry("DIVORCIADA", "DIVORCIADO"),
                    Map.entry("DIVORCIADO", "DIVORCIADO"),
                    Map.entry("SOLTERA", "SOLTERO"),
                    Map.entry("SOLTERO", "SOLTERO"),
                    Map.entry("VIUDA", "VIUDO"),
                    Map.entry("VIUDO", "VIUDO"))),

            Map.entry(Catalogo.NACIONALIDAD, Map.of(
                    "PERUANA", "PERUANA",
                    "PERUANO", "PERUANA")),

            Map.entry(Catalogo.NIVEL_INSTRUCCION, Map.of(
                    // 'UNIVERSITARIO COMPLETA'/'COMPLETO' son la misma cosa que el femenino.
                    "UNIVERSITARIO COMPLETA", "UNIVERSITARIA COMPLETA",
                    "UNIVERSITARIO COMPLETO", "UNIVERSITARIA COMPLETA",
                    "UNIVERSITARIA COMPLETA", "UNIVERSITARIA COMPLETA",
                    "UNIVERSITARIO INCOMPLETA", "UNIVERSITARIA INCOMPLETA",
                    "UNIVERSITARIA INCOMPLETA", "UNIVERSITARIA INCOMPLETA",
                    // El catálogo real usa 'TÉCNICA ...'; el Excel abrevia 'TECNICO'.
                    "TECNICO", "TECNICA SUPERIOR")),

            /*
             * Los canónicos son los NOMBRES REALES de INDECI_GRADO_ACADEMICO
             * (verificado en BD): Bachiller, Titulado, Egresado Universitario,
             * Estudiante Universitario, Maestro, Doctor, Profesional Técnico.
             */
            Map.entry(Catalogo.GRADO_ACADEMICO, Map.ofEntries(
                    Map.entry("-", SIN_DATO),
                    Map.entry("TITULADO", "Titulado"),
                    Map.entry("TITULADO (A)", "Titulado"),
                    Map.entry("TITULADO(A)", "Titulado"),
                    Map.entry("TITULO", "Titulado"),
                    Map.entry("BACHILLER", "Bachiller"),
                    Map.entry("EGRESADO", "Egresado Universitario"),
                    Map.entry("EGRESADO (A)", "Egresado Universitario"),
                    Map.entry("EGRESADO(A)", "Egresado Universitario"),
                    Map.entry("ESTUDIANTE", "Estudiante Universitario"),
                    Map.entry("PROFESIONAL TECNICO", "Profesional Técnico"))),

            /* El posgrado se guarda como GRADO académico: en BD existen 'Maestro' y 'Doctor'. */
            Map.entry(Catalogo.NIVEL_POSGRADO, Map.of(
                    "MAESTRIA", "Maestro",
                    "MAESTRO", "Maestro",
                    "MAGISTER", "Maestro",
                    "DOCTORADO", "Doctor",
                    "DOCTOR", "Doctor")),

            Map.entry(Catalogo.CONDICION_GRADO, Map.of(
                    "EGRESADO", "EGRESADO",
                    "EGRESADO (A)", "EGRESADO",
                    "EGRESADO(A)", "EGRESADO",
                    "TITULADO", "TITULADO",
                    "TITULADO(A)", "TITULADO",
                    "TITULO", "TITULADO",
                    "ESTUDIANTE", "ESTUDIANTE")),

            Map.entry(Catalogo.BANCO, Map.ofEntries(
                    // Residuo del buscar-y-reemplazar 'NACION'→'BANCO DE LA NACION' del origen.
                    Map.entry("BANCO DE LA BANCO DE LA NACION", "BANCO DE LA NACION"),
                    Map.entry("NACION", "BANCO DE LA NACION"),
                    Map.entry("BANCO DE LA NACION", "BANCO DE LA NACION"),
                    Map.entry("BCP", "BANCO DE CREDITO DEL PERU"),
                    Map.entry("BANCO DE CREDITO DEL PERU", "BANCO DE CREDITO DEL PERU"),
                    Map.entry("BBVA", "BBVA PERU"),
                    Map.entry("BBVA PERU", "BBVA PERU"),
                    Map.entry("CONTINENTAL", "BBVA PERU"),
                    Map.entry("BANBINF", "BANBIF"),
                    Map.entry("BANBIF", "BANBIF"),
                    Map.entry("PICHINCHA", "BANCO PICHINCHA"),
                    Map.entry("BANCO PICHINCHA", "BANCO PICHINCHA"),
                    Map.entry("INTERBANK", "INTERBANK"),
                    // En BD el nombre real es 'SCOTIABANK PERU'; el Excel escribe 'SCOTIABANK'.
                    Map.entry("SCOTIABANK", "SCOTIABANK PERU"),
                    Map.entry("SCOTIABANK PERU", "SCOTIABANK PERU"))),

            /*
             * CRÍTICO (bug real, ver memoria bug-dependencia-vacia-papeletas). El Excel solo
             * trae UNA columna "Oficina / dependencia" (VinculacionColumna.OFICINA) con texto
             * libre; el import histórico solo la usaba para OFICINA_ID y nunca resolvía
             * DEPENDENCIA_ID — 654 puestos quedaron sin Dependencia (backfill V012_51,
             * 2026-08-04). Este diccionario resuelve la MISMA celda contra el catálogo
             * CERRADO de 45 Dependencias (V012_50): el canónico es la SIGLA. Generado a
             * partir de las 71 variantes reales del Excel oficial (663 filas). "DD" y "DDI"
             * son la MISMA Dirección Desconcentrada regional (Moquegua y Piura aparecen con
             * ambos prefijos para la misma región) — se fusionan a una sola sigla DD-<REGIÓN>.
             */
            Map.entry(Catalogo.DEPENDENCIA, Map.ofEntries(
                    Map.entry("CENTRO DE OPERACIONES DE EMERGENCIA BANCO DE LA NACIONAL", "COEN"),
                    Map.entry("CENTRO DE OPERACIONES DE EMERGENCIA NACIONAL", "COEN"),
                    Map.entry("DIRECCION DE POLITICAS, PLANES Y EVALUACION", "DIPPE"),
                    Map.entry("DIRECCION DE POLITICAS, PLANES, EVALUACION Y ESTADISTICA", "DIPPE"),
                    Map.entry("DIRECCION DE PREPARACION", "DIPRE"),
                    Map.entry("DIRECCION DE REHABILITACION", "DIREH"),
                    Map.entry("DIRECCION DE REHABILITACION - SUB DIRECCION DE NORMALIZACION DE MEDIOS DE VIDA", "DIREH"),
                    Map.entry("DIRECCION DE REHABILITACION - SUB DIRECCION DE RESTABLECIMIENTO DE SERVICIOS PUBLICOS BASICOS E INFRAESTRUCTURA", "DIREH"),
                    Map.entry("DIRECCION DE RESPUESTA", "DIRES"),
                    Map.entry("DIRECCION DE RESPUESTA - ALMACEN LIMA", "DIRES"),
                    Map.entry("DIRECCION DE RESPUESTA - ALMACENES", "DIRES"),
                    Map.entry("DIRECCION DE RESPUESTA - SUB DIRECCION DE ASISTENCIA HUMANITARIA Y MOVILIZACION", "DIRES"),
                    Map.entry("DIRECCION DE RESPUESTA - SUB DIRECCION DE GESTION OPERATIVA", "DIRES"),
                    Map.entry("DIRECCION DESCONCENTRADA - DD AYACUCHO", "DD-AYACUCHO"),
                    Map.entry("DIRECCION DESCONCENTRADA - DD CAJAMARCA", "DD-CAJAMARCA"),
                    Map.entry("DIRECCION DESCONCENTRADA - DD CALLAO", "DD-CALLAO"),
                    Map.entry("DIRECCION DESCONCENTRADA - DD HUANUCO", "DD-HUANUCO"),
                    Map.entry("DIRECCION DESCONCENTRADA - DD LA LIBERTAD", "DD-LA_LIBERTAD"),
                    Map.entry("DIRECCION DESCONCENTRADA - DD LAMBAYEQUE", "DD-LAMBAYEQUE"),
                    Map.entry("DIRECCION DESCONCENTRADA - DD LIMA - HUACHO", "DD-LIMA"),
                    Map.entry("DIRECCION DESCONCENTRADA - DD LORETO", "DD-LORETO"),
                    Map.entry("DIRECCION DESCONCENTRADA - DD MOQUEGUA", "DD-MOQUEGUA"),
                    Map.entry("DIRECCION DESCONCENTRADA - DD PIURA", "DD-PIURA"),
                    Map.entry("DIRECCION DESCONCENTRADA - DD PUNO", "DD-PUNO"),
                    Map.entry("DIRECCION DESCONCENTRADA - DD TUMBES", "DD-TUMBES"),
                    Map.entry("DIRECCION DESCONCENTRADA - DDI AMAZONAS", "DD-AMAZONAS"),
                    Map.entry("DIRECCION DESCONCENTRADA - DDI ANCASH", "DD-ANCASH"),
                    Map.entry("DIRECCION DESCONCENTRADA - DDI APURIMAC", "DD-APURIMAC"),
                    Map.entry("DIRECCION DESCONCENTRADA - DDI AREQUIPA", "DD-AREQUIPA"),
                    Map.entry("DIRECCION DESCONCENTRADA - DDI CUSCO", "DD-CUSCO"),
                    Map.entry("DIRECCION DESCONCENTRADA - DDI HUANCAVELICA", "DD-HUANCAVELICA"),
                    Map.entry("DIRECCION DESCONCENTRADA - DDI ICA", "DD-ICA"),
                    Map.entry("DIRECCION DESCONCENTRADA - DDI JUNIN", "DD-JUNIN"),
                    Map.entry("DIRECCION DESCONCENTRADA - DDI MADRE DE DIOS", "DD-MADRE_DE_DIOS"),
                    Map.entry("DIRECCION DESCONCENTRADA - DDI MOQUEGUA", "DD-MOQUEGUA"),
                    Map.entry("DIRECCION DESCONCENTRADA - DDI PASCO", "DD-PASCO"),
                    Map.entry("DIRECCION DESCONCENTRADA - DDI PIURA", "DD-PIURA"),
                    Map.entry("DIRECCION DESCONCENTRADA - DDI SAN MARTIN", "DD-SAN_MARTIN"),
                    Map.entry("DIRECCION DESCONCENTRADA - DDI TACNA", "DD-TACNA"),
                    Map.entry("DIRECCION DESCONCENTRADA - DDI UCAYALI", "DD-UCAYALI"),
                    Map.entry("DIRECCION DESCONCENTRADA DE AREQUIPA", "DD-AREQUIPA"),
                    Map.entry("DIRECCION DESCONCENTRADA DE CALLAO", "DD-CALLAO"),
                    Map.entry("DIRECCION DESCONCENTRADA DE LORETO", "DD-LORETO"),
                    Map.entry("DIRECCION DESCONCENTRADA DE PIURA", "DD-PIURA"),
                    Map.entry("DIRECCION DESCONCENTRADA DE TACNA", "DD-TACNA"),
                    Map.entry("DIRES - ALMACEN FAUCETT", "DIRES"),
                    Map.entry("DIRES - ALMACEN PUNTA HERMOSA", "DIRES"),
                    Map.entry("GERENCIA GENERAL", "GG"),
                    Map.entry("GERENCIA GENERAL- FONDES", "GG"),
                    Map.entry("JEFATURA", "JEFATURA"),
                    Map.entry("OFICINA DE ADMINISTRACION", "OAD"),
                    Map.entry("OFICINA DE ASESORIA JURIDICA", "OAJ"),
                    Map.entry("OFICINA DE COMUNICACION SOCIAL", "OCS"),
                    Map.entry("OFICINA DE COOPERACION Y ASUNTOS INTERBANCO DE LA NACIONALES", "OCAI"),
                    Map.entry("OFICINA DE COOPERACION Y ASUNTOS INTERNACIONALES", "OCAI"),
                    Map.entry("OFICINA DE PLANEAMIENTO Y PRESUPUESTO", "OPP"),
                    Map.entry("OFICINA DE PLANIFICACION Y PRESUPUESTO", "OPP"),
                    Map.entry("OFICINA DE TECNOLOGIAS DE LA INFORMACION Y COMUNICACIONES", "OTIC"),
                    Map.entry("ORGANO DE CONTROL INSTITUCIONAL", "OCI"),
                    Map.entry("UNIDAD DE ABASTECIMIENTO", "UAB"),
                    Map.entry("UNIDAD DE ABASTECIMIENTO - ALMACEN SEDE ARGENTINA", "UAB"),
                    Map.entry("UNIDAD DE ABASTECIMIENTO - ALMACEN SEDE PUNTA HERMOSA", "UAB"),
                    Map.entry("UNIDAD DE ABASTECIMIENTO ALMACEN - SEDE FAUCETT", "UAB"),
                    Map.entry("UNIDAD DE CONTABILIDAD", "UCO"),
                    Map.entry("UNIDAD DE RECURSOS HUMANOS", "URH"),
                    Map.entry("UNIDAD DE SERVICIOS GENERALES", "USG"),
                    Map.entry("UNIDAD DE TESORERIA", "UTE"),
                    Map.entry("UNIDAD RECURSOS HUMANOS", "URH"))));

    /**
     * Traduce un texto del Excel al valor canónico del catálogo.
     *
     * @param catalogo catálogo al que pertenece el texto
     * @param texto    valor crudo de la celda
     * @return el canónico; el mismo texto normalizado si no hay equivalencia registrada;
     *         {@code null} si la celda está vacía o el valor significa "sin dato".
     */
    public String canonico(Catalogo catalogo, String texto) {
        final String clave = TextoNormalizador.clave(texto);
        if (clave == null) {
            return null;
        }
        final String canonico = EQUIVALENCIAS.getOrDefault(catalogo, Map.of()).get(clave);
        if (canonico == null) {
            return clave; // sin equivalencia registrada → se usa tal cual (normalizado)
        }
        return canonico.isEmpty() ? null : canonico;
    }

    /** {@code true} si el texto tiene una equivalencia explícita (útil para reportar el saneo). */
    public boolean tieneEquivalencia(Catalogo catalogo, String texto) {
        final String clave = TextoNormalizador.clave(texto);
        return clave != null && EQUIVALENCIAS.getOrDefault(catalogo, Map.of()).containsKey(clave);
    }
}
