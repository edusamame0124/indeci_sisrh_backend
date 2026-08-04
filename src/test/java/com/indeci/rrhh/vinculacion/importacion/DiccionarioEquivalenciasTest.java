package com.indeci.rrhh.vinculacion.importacion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.indeci.rrhh.vinculacion.importacion.DiccionarioEquivalencias.Catalogo;

/**
 * El diccionario traduce el texto del Excel a los valores <b>reales</b> de los catálogos
 * en BD (verificados con scripts/diagnostico-catalogos-vinculacion.sql).
 */
class DiccionarioEquivalenciasTest {

    private final DiccionarioEquivalencias diccionario = new DiccionarioEquivalencias();

    @Test
    @DisplayName("CRÍTICO: 'CAS' resuelve al código real del catálogo, que es '1057'")
    void casMapeaA1057() {
        // INDECI_REGIMEN_LABORAL no tiene el código 'CAS': tiene 1057 = CONTRATO
        // ADMINISTRATIVO DE SERVICIOS. Sin este alias, las ~588 filas CAS del Excel
        // quedarían sin régimen y el motor no podría calcular.
        assertThat(diccionario.canonico(Catalogo.REGIMEN_LABORAL, "CAS")).isEqualTo("1057");
        assertThat(diccionario.canonico(Catalogo.REGIMEN_LABORAL, "cas ")).isEqualTo("1057");
        assertThat(diccionario.canonico(Catalogo.REGIMEN_LABORAL, "1057")).isEqualTo("1057");
    }

    @Test
    @DisplayName("CRÍTICO: SERVIR y 'LEY 30057' resuelven a '30057'")
    void servirMapeaA30057() {
        assertThat(diccionario.canonico(Catalogo.REGIMEN_LABORAL, "30057")).isEqualTo("30057");
        assertThat(diccionario.canonico(Catalogo.REGIMEN_LABORAL, "SERVIR")).isEqualTo("30057");
        assertThat(diccionario.canonico(Catalogo.REGIMEN_LABORAL, "LEY 30057")).isEqualTo("30057");
    }

    @Test
    @DisplayName("Bancos: las 12 variantes del Excel colapsan a los nombres reales de BANKS")
    void bancosColapsanANombresReales() {
        // En BD: BANCO DE CREDITO DEL PERU, BBVA PERU, BANCO DE LA NACION, INTERBANK,
        // SCOTIABANK PERU (+ BANBIF y BANCO PICHINCHA que agrega V012_37).
        assertThat(diccionario.canonico(Catalogo.BANCO, "BCP")).isEqualTo("BANCO DE CREDITO DEL PERU");
        assertThat(diccionario.canonico(Catalogo.BANCO, "BBVA")).isEqualTo("BBVA PERU");
        assertThat(diccionario.canonico(Catalogo.BANCO, "CONTINENTAL")).isEqualTo("BBVA PERU");
        assertThat(diccionario.canonico(Catalogo.BANCO, "NACION")).isEqualTo("BANCO DE LA NACION");
        assertThat(diccionario.canonico(Catalogo.BANCO, "BANCO DE LA NACIÓN"))
                .isEqualTo("BANCO DE LA NACION");
        // Residuo del buscar-y-reemplazar del archivo de origen.
        assertThat(diccionario.canonico(Catalogo.BANCO, "BANCO DE LA BANCO DE LA NACION"))
                .isEqualTo("BANCO DE LA NACION");
        // El Excel escribe 'SCOTIABANK'; en BD el nombre real lleva 'PERU'.
        assertThat(diccionario.canonico(Catalogo.BANCO, "SCOTIABANK")).isEqualTo("SCOTIABANK PERU");
        assertThat(diccionario.canonico(Catalogo.BANCO, "BANBINF")).isEqualTo("BANBIF");
        assertThat(diccionario.canonico(Catalogo.BANCO, "PICHINCHA")).isEqualTo("BANCO PICHINCHA");
    }

    @Test
    @DisplayName("Estado civil: las 11 variantes colapsan a 5 canónicos y '0' es sin dato")
    void estadoCivilColapsa() {
        assertThat(diccionario.canonico(Catalogo.ESTADO_CIVIL, "Casada")).isEqualTo("CASADO");
        assertThat(diccionario.canonico(Catalogo.ESTADO_CIVIL, "Casado")).isEqualTo("CASADO");
        assertThat(diccionario.canonico(Catalogo.ESTADO_CIVIL, "Concuvina")).isEqualTo("CONVIVIENTE");
        assertThat(diccionario.canonico(Catalogo.ESTADO_CIVIL, "Concuvino")).isEqualTo("CONVIVIENTE");
        assertThat(diccionario.canonico(Catalogo.ESTADO_CIVIL, "Soltero ")).isEqualTo("SOLTERO");
        assertThat(diccionario.canonico(Catalogo.ESTADO_CIVIL, "0")).isNull();
    }

    @Test
    @DisplayName("Grado académico y posgrado usan los nombres reales del catálogo")
    void gradosUsanNombresReales() {
        assertThat(diccionario.canonico(Catalogo.GRADO_ACADEMICO, "TITULADO(A)")).isEqualTo("Titulado");
        assertThat(diccionario.canonico(Catalogo.GRADO_ACADEMICO, "TITULADO (A)")).isEqualTo("Titulado");
        assertThat(diccionario.canonico(Catalogo.GRADO_ACADEMICO, "EGRESADO(A)"))
                .isEqualTo("Egresado Universitario");
        assertThat(diccionario.canonico(Catalogo.GRADO_ACADEMICO, "-")).isNull();
        // 'MAGISTER \n' y 'MAESTRO ' son la misma cosa que MAESTRIA → grado 'Maestro'.
        assertThat(diccionario.canonico(Catalogo.NIVEL_POSGRADO, "MAGISTER \n")).isEqualTo("Maestro");
        assertThat(diccionario.canonico(Catalogo.NIVEL_POSGRADO, "MAESTRIA ")).isEqualTo("Maestro");
        assertThat(diccionario.canonico(Catalogo.NIVEL_POSGRADO, "DOCTORADO")).isEqualTo("Doctor");
    }

    @Test
    @DisplayName("Dependencia: 'DD' y 'DDI' son la misma Dirección Desconcentrada regional")
    void dependenciaFusionaDdYDdi() {
        // Moquegua y Piura aparecen en el Excel real con AMBOS prefijos para la misma
        // región (confirmado en INFORME/bug-dependencia-vacia-papeletas) — no son dos
        // oficinas distintas, es inconsistencia de tipeo de RR.HH.
        assertThat(diccionario.canonico(Catalogo.DEPENDENCIA, "DIRECCION DESCONCENTRADA - DD MOQUEGUA"))
                .isEqualTo("DD-MOQUEGUA");
        assertThat(diccionario.canonico(Catalogo.DEPENDENCIA, "DIRECCION DESCONCENTRADA - DDI MOQUEGUA"))
                .isEqualTo("DD-MOQUEGUA");
        assertThat(diccionario.canonico(Catalogo.DEPENDENCIA, "DIRECCION DESCONCENTRADA - DD PIURA"))
                .isEqualTo("DD-PIURA");
        assertThat(diccionario.canonico(Catalogo.DEPENDENCIA, "DIRECCION DESCONCENTRADA - DDI PIURA"))
                .isEqualTo("DD-PIURA");
        // Tercera variante de redacción para la misma región (sin "DD"/"DDI").
        assertThat(diccionario.canonico(Catalogo.DEPENDENCIA, "DIRECCION DESCONCENTRADA DE PIURA"))
                .isEqualTo("DD-PIURA");
    }

    @Test
    @DisplayName("CRÍTICO: el texto con error de tipeo (76 filas reales) resuelve a COEN")
    void dependenciaCoenConErrorDeTipeoResuelve() {
        // "...BANCO DE LA NACIONAL" es basura de copy-paste del Excel real, pero es el
        // texto más frecuente de la columna (76/663 filas) — decisión RR.HH. 2026-08-04.
        assertThat(diccionario.canonico(Catalogo.DEPENDENCIA,
                "CENTRO DE OPERACIONES DE EMERGENCIA BANCO DE LA NACIONAL"))
                .isEqualTo("COEN");
        assertThat(diccionario.canonico(Catalogo.DEPENDENCIA,
                "CENTRO DE OPERACIONES DE EMERGENCIA NACIONAL"))
                .isEqualTo("COEN");
    }

    @Test
    @DisplayName("Dependencia: texto sin equivalencia registrada no inventa nada (catálogo CERRADO)")
    void dependenciaSinEquivalenciaDevuelveClaveTalCual() {
        // Sin entrada en el diccionario, canonico() devuelve la clave normalizada tal cual
        // (comportamiento genérico de DiccionarioEquivalencias); es CatalogoTexto.resolver
        // quien decide no inventar una Dependencia nueva cuando esa clave no matchea
        // ninguna SIGLA/NOMBRE real — eso se prueba en CatalogoTextResolver, no aquí.
        assertThat(diccionario.canonico(Catalogo.DEPENDENCIA, "OFICINA QUE NO EXISTE EN NINGUN CATALOGO"))
                .isEqualTo("OFICINA QUE NO EXISTE EN NINGUN CATALOGO");
    }

    @Test
    @DisplayName("La normalización sola ya colapsa tildes, NBSP y espacios")
    void normalizacionColapsaRuido() {
        // ' TÉCNICO' (NBSP + tilde) y 'TECNICO ' llegan como la misma clave.
        assertThat(TextoNormalizador.clave(" TÉCNICO")).isEqualTo("TECNICO");
        assertThat(TextoNormalizador.clave("TECNICO ")).isEqualTo("TECNICO");
        assertThat(TextoNormalizador.clave("TÉCNICA COMPLETA"))
                .isEqualTo(TextoNormalizador.clave("tecnica  completa"));
        // Sin equivalencia registrada, se devuelve la clave normalizada tal cual.
        assertThat(diccionario.canonico(Catalogo.NACIONALIDAD, "PERUANO")).isEqualTo("PERUANA");
    }
}
