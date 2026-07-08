package com.indeci.rrhh.service.asistencia;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class FormatoDetectorTest {

    private final FormatoDetector detector = new FormatoDetector();

    @Test
    void detecta_reporteCoen_porTitulo() {
        String coen = "\"REPORTE DE MARCAS DEL PERSONAL\",\"Del 01/06/2026 al 30/06/2026\","
                + "\"#\",\"Trabajador\",\"Fecha\",\"Hora\",\"Estado\",\"Tipo\",\"Característica\","
                + "1,\"AGUIRRE SAENZ, HUGO RAFAEL\",\"01/06/2026\",\"07:44:50\",\"Inválido\",,\"Ingreso\"";

        assertThat(detector.detectar(coen)).isEqualTo(FormatoMarcador.RELOJ2_COEN);
        assertThat(detector.detectar(coen.getBytes(StandardCharsets.UTF_8)))
                .isEqualTo(FormatoMarcador.RELOJ2_COEN);
    }

    @Test
    void detecta_reporteCoen_porCabeceraEventos_sinTitulo() {
        String coen = "\"#\",\"Trabajador\",\"Fecha\",\"Hora\",\"Estado\",\"Tipo\",\"Característica\"\n"
                + "1,\"PEREZ, JUAN\",\"01/06/2026\",\"08:00:00\",\"Válido\",,\"Ingreso\"";

        assertThat(detector.detectar(coen)).isEqualTo(FormatoMarcador.RELOJ2_COEN);
    }

    @Test
    void detecta_reloj1Diario_porCabeceraConDniYMarca1() {
        String diario = "Dia;Fecha;DNI;Nombre;Entrada;Salida;Marca1;Marca2;Tard.\n"
                + "Lun;1/06/2026;44426142;ACAL HERRERA JOSE;08:30;17:30;08:33;18:56;00:03";

        assertThat(detector.detectar(diario)).isEqualTo(FormatoMarcador.RELOJ1_DIARIO);
    }

    @Test
    void textoVacioONulo_esDesconocido() {
        assertThat(detector.detectar("")).isEqualTo(FormatoMarcador.DESCONOCIDO);
        assertThat(detector.detectar((String) null)).isEqualTo(FormatoMarcador.DESCONOCIDO);
        assertThat(detector.detectar(new byte[0])).isEqualTo(FormatoMarcador.DESCONOCIDO);
    }

    @Test
    void contenidoNoReconocido_esDesconocido() {
        assertThat(detector.detectar("hola,mundo\n1,2,3")).isEqualTo(FormatoMarcador.DESCONOCIDO);
    }
}
