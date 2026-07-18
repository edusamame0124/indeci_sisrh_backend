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
