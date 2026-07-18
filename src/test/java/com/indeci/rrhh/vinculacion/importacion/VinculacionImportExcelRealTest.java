package com.indeci.rrhh.vinculacion.importacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.indeci.rrhh.vinculacion.importacion.reglas.FechasYCeseRule;
import com.indeci.rrhh.vinculacion.importacion.reglas.IdentidadRule;
import com.indeci.rrhh.vinculacion.importacion.reglas.PensionRule;
import com.indeci.rrhh.vinculacion.importacion.reglas.RegimenGobiernaCamposRule;
import com.indeci.rrhh.vinculacion.importacion.reglas.VinculoDatosRule;

/**
 * Ejercita el lector y las reglas contra el <b>Excel oficial real</b> entregado por el
 * especialista de RR.HH. (617 filas). Es la prueba que de verdad demuestra que el import
 * puede procesar el archivo de producción, no un fixture inventado.
 *
 * <p>Si el archivo no está presente (p. ej. en CI), el test se omite en vez de fallar.
 */
class VinculacionImportExcelRealTest {

    private static final Path EXCEL = Paths.get(
            "..", "docs", "PLANTILLA_IMPORT_VINCULACION_oficial.xlsx");

    private final VinculacionSheetReader reader = new VinculacionSheetReader();
    private final VinculacionRowValidator validator = new VinculacionRowValidator(List.of(
            new IdentidadRule(),
            new RegimenGobiernaCamposRule(),
            new VinculoDatosRule(),
            new FechasYCeseRule(),
            new PensionRule(new PensionExcelParser())));

    private List<VinculacionRowRaw> leerExcelReal() throws Exception {
        assumeTrue(Files.exists(EXCEL), "Excel oficial no disponible; test omitido.");
        return reader.leer(Files.readAllBytes(EXCEL));
    }

    @Test
    @DisplayName("Lee las 617 filas del Excel oficial")
    void leeTodasLasFilas() throws Exception {
        final List<VinculacionRowRaw> filas = leerExcelReal();
        assertThat(filas).hasSize(617);
        assertThat(filas.get(0).texto(VinculacionColumna.NOMBRE_COMPLETO))
                .isEqualTo("CUNO VERA BORIS ALBERTO");
        assertThat(filas.get(0).digitos(VinculacionColumna.DNI)).isEqualTo("24485494");
    }

    @Test
    @DisplayName("Los datos sucios conocidos quedan saneados, no rechazados")
    void saneaDatosSucios() throws Exception {
        final Map<Integer, VinculacionRowRaw> porFila = leerExcelReal().stream()
                .collect(Collectors.toMap(VinculacionRowRaw::getNumeroFila, f -> f));

        // f597: monto 'S/. 18,707.14' (texto) y AIRHSP '´000104' (tilde inicial).
        final VinculacionRowRaw f597 = porFila.get(597);
        assertThat(f597.numero(VinculacionColumna.MONTO_CONTRATO))
                .isEqualByComparingTo("18707.14");
        assertThat(TextoNormalizador.soloDigitos(f597.texto(VinculacionColumna.CODIGO_AIRHSP)))
                .isEqualTo("000104");

        // f496/f556: CCI con separadores → 20 dígitos exactos tras el saneo.
        assertThat(TextoNormalizador.soloDigitos(porFila.get(496).texto(VinculacionColumna.CCI)))
                .hasSize(20);
        assertThat(TextoNormalizador.soloDigitos(porFila.get(556).texto(VinculacionColumna.CCI)))
                .hasSize(20);
    }

    @Test
    @DisplayName("Los importables son exactamente el total menos las filas con error")
    void importablesEsTotalMenosErrores() throws Exception {
        final List<VinculacionRowValidator.Resultado> resultados =
                validator.validarTodas(leerExcelReal());

        final long conError = resultados.stream()
                .filter(VinculacionRowValidator.Resultado::tieneErrores).count();
        final long sinError = resultados.stream()
                .filter(r -> !r.tieneErrores()).count();

        // Invariante robusto ante ediciones del Excel: cada fila es importable o tiene error.
        assertThat(sinError + conError).isEqualTo(617);
        // La abrumadora mayoría del padrón es importable (una fila mala no bloquea al resto).
        assertThat(sinError).isGreaterThan(600);
    }

    @Test
    @DisplayName("Toda fila con error explica la causa anclada a una columna concreta")
    void losErroresApuntanAUnaColumna() throws Exception {
        final List<VinculacionRowValidator.Resultado> conError =
                validator.validarTodas(leerExcelReal()).stream()
                        .filter(VinculacionRowValidator.Resultado::tieneErrores)
                        .toList();

        // Cada hallazgo de tipo ERROR referencia la columna exacta a corregir; sin esto,
        // RR.HH. no sabría dónde está el problema.
        assertThat(conError).allSatisfy(resultado ->
                assertThat(columnasConError(resultado))
                        .as("fila %d", resultado.numeroFila())
                        .isNotEmpty()
                        .doesNotContainNull());
    }

    private List<VinculacionColumna> columnasConError(VinculacionRowValidator.Resultado resultado) {
        return resultado.issues().stream()
                .filter(i -> i.severidad() == RowIssue.Severidad.ERROR)
                .map(RowIssue::columna)
                .toList();
    }

    @Test
    @DisplayName("Los 29 SERVIR traen grupo válido y ninguno cae al fallback L003")
    void servirTieneGrupoValido() throws Exception {
        final List<VinculacionRowRaw> servir = leerExcelReal().stream()
                .filter(f -> "30057".equals(f.clave(VinculacionColumna.REGIMEN_LABORAL)))
                .toList();

        assertThat(servir).hasSize(29);
        assertThat(servir).allSatisfy(fila ->
                assertThat(fila.clave(VinculacionColumna.GRUPO_SERVIDOR_CIVIL))
                        .isIn("FUNCIONARIO", "DIRECTIVO", "CARRERA", "ACTIVIDADES_COMPLEMENTARIAS"));

        // El Jefe Institucional es el único FUNCIONARIO (concepto MEF L001).
        assertThat(servir.stream()
                .filter(f -> "FUNCIONARIO".equals(f.clave(VinculacionColumna.GRUPO_SERVIDOR_CIVIL)))
                .count()).isEqualTo(1);
    }

    @Test
    @DisplayName("La pensión se desagrega: sistema, AFP y condición de retiro")
    void desagregaPension() throws Exception {
        final PensionExcelParser parser = new PensionExcelParser();
        final List<PensionExcelParser.PensionLeida> pensiones = leerExcelReal().stream()
                .map(parser::parsear)
                .toList();

        // Toda fila resuelve un sistema: el Excel no deja a nadie sin régimen pensionario.
        assertThat(pensiones).allSatisfy(p -> assertThat(p.sistema()).isNotNull());
        assertThat(pensiones).extracting(PensionExcelParser.PensionLeida::sistema)
                .containsOnly("AFP", "ONP", "CPMP");

        // El sufijo pegado al nombre se separa a la condición especial (campo propio del modelo).
        assertThat(pensiones).filteredOn(p -> p.condicionEspecialAfp() != null)
                .isNotEmpty()
                .allSatisfy(p -> assertThat(p.condicionEspecialAfp()).isEqualTo("RETIRO_955"));

        // Invariante del parser (independiente de qué tan limpio esté el Excel): la AFP solo
        // se rellena cuando el sistema es AFP; nunca aparece pegada a ONP o CPMP.
        assertThat(pensiones)
                .allSatisfy(p -> assertThat(p.afp() == null || p.esAfp()).isTrue());
    }
}
