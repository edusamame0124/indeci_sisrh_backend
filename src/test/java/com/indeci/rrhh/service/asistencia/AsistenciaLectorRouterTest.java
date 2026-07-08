package com.indeci.rrhh.service.asistencia;

import com.indeci.exception.NegocioException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AsistenciaLectorRouterTest {

    private final AsistenciaLectorRouter router = new AsistenciaLectorRouter(
            new FormatoDetector(), new AsistenciaCsvParser(), new AsistenciaEventosReader());

    @Test
    void archivoCoen_enrutaAlLectorDeEventos() throws IOException {
        byte[] bytes = Files.readAllBytes(Path.of("data", "asistencia", "import", "coen_sample.csv"));

        AsistenciaLectorRouter.ResultadoLectura r = router.leer(bytes);

        assertThat(r.formato()).isEqualTo(FormatoMarcador.RELOJ2_COEN);
        assertThat(r.parseResult().getFilas()).isNotEmpty();
    }

    @Test
    void archivoReloj1Diario_enrutaAlParserDiario() {
        String diario = "Dia;Fecha;DNI;Nombre;Entrada;Salida;Marca1;Marca2;Marca3;Marca4;Tard.\n"
                + "Lun;1/06/2026;44426142;ACAL HERRERA JOSE;08:30;17:30;08:33;18:56;;;00:03";

        AsistenciaLectorRouter.ResultadoLectura r = router.leer(diario.getBytes(StandardCharsets.UTF_8));

        assertThat(r.formato()).isEqualTo(FormatoMarcador.RELOJ1_DIARIO);
        assertThat(r.parseResult().getFilas()).isNotEmpty();
    }

    @Test
    void formatoDesconocido_lanzaNegocioException() {
        byte[] basura = "hola,mundo\n1,2,3".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> router.leer(basura))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("No se reconoce el formato");
    }
}
