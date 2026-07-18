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
        BANCO
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
                    Map.entry("SCOTIABANK PERU", "SCOTIABANK PERU"))));

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
