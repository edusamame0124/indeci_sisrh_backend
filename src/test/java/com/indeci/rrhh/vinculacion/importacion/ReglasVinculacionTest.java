package com.indeci.rrhh.vinculacion.importacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.indeci.rrhh.vinculacion.importacion.reglas.FechasYCeseRule;
import com.indeci.rrhh.vinculacion.importacion.reglas.RegimenGobiernaCamposRule;
import com.indeci.rrhh.vinculacion.importacion.reglas.VinculoDatosRule;

/**
 * Tests de las reglas de validación (REGLA-07: caso feliz, error normativo y borde).
 *
 * <p>Cada regla se prueba aislada, sin Spring ni BD: son objetos puros por diseño.
 */
class ReglasVinculacionTest {

    /**
     * Construye una fila CAS mínima válida. Los pares {@code (columna, valor)} sobrescriben
     * la base; un valor {@code null} <b>elimina</b> esa columna (celda vacía).
     */
    private static VinculacionRowRaw fila(Object... paresColumnaValor) {
        final Map<VinculacionColumna, Object> valores = new EnumMap<>(VinculacionColumna.class);
        valores.put(VinculacionColumna.DNI, "24485494");
        valores.put(VinculacionColumna.NOMBRE_COMPLETO, "QUISPE MAMANI, ROSA");
        valores.put(VinculacionColumna.CODIGO_AIRHSP, "000102");
        valores.put(VinculacionColumna.REGIMEN_LABORAL, "CAS");
        valores.put(VinculacionColumna.MODALIDAD_CAS, "NECESIDAD TRANSITORIA");
        valores.put(VinculacionColumna.NUMERO_CONTRATO, "052-2008");
        valores.put(VinculacionColumna.MONTO_CONTRATO, 3500d);
        valores.put(VinculacionColumna.FECHA_INGRESO, LocalDateTime.of(2020, 1, 2, 0, 0));
        valores.put(VinculacionColumna.FECHA_INICIO_CONTRATO, LocalDateTime.of(2020, 1, 2, 0, 0));

        for (int i = 0; i < paresColumnaValor.length; i += 2) {
            final VinculacionColumna columna = (VinculacionColumna) paresColumnaValor[i];
            final Object valor = paresColumnaValor[i + 1];
            if (valor == null) {
                valores.remove(columna);
            } else {
                valores.put(columna, valor);
            }
        }

        final VinculacionRowRaw f = new VinculacionRowRaw(10);
        valores.forEach(f::put);
        return f;
    }

    private static List<RowIssue> errores(List<RowIssue> issues) {
        return issues.stream().filter(i -> i.severidad() == RowIssue.Severidad.ERROR).toList();
    }

    @Nested
    @DisplayName("RegimenGobiernaCamposRule")
    class Regimen {

        private final RegimenGobiernaCamposRule regla = new RegimenGobiernaCamposRule();

        @Test
        @DisplayName("caso feliz: CAS con modalidad no genera errores")
        void casConModalidad() {
            assertThat(errores(regla.validar(fila()))).isEmpty();
        }

        @Test
        @DisplayName("error normativo: CAS no puede tener asignación familiar")
        void casSinAsignacionFamiliar() {
            final List<RowIssue> issues = regla.validar(
                    fila(VinculacionColumna.TIENE_ASIGNACION_FAMILIAR, "S"));
            assertThat(errores(issues))
                    .extracting(RowIssue::columna)
                    .contains(VinculacionColumna.TIENE_ASIGNACION_FAMILIAR);
        }

        @Test
        @DisplayName("error normativo: SERVIR sin grupo caería al fallback L003 del motor")
        void servirExigeGrupo() {
            final List<RowIssue> issues = regla.validar(fila(
                    VinculacionColumna.REGIMEN_LABORAL, "30057",
                    VinculacionColumna.MODALIDAD_CAS, null));
            assertThat(errores(issues))
                    .extracting(RowIssue::columna)
                    .contains(VinculacionColumna.GRUPO_SERVIDOR_CIVIL);
        }

        @Test
        @DisplayName("borde: 'COMPLEMENTARIAS' equivale a ACTIVIDADES_COMPLEMENTARIAS")
        void aceptaAliasDeComplementarias() {
            final List<RowIssue> issues = regla.validar(fila(
                    VinculacionColumna.REGIMEN_LABORAL, "30057",
                    VinculacionColumna.GRUPO_SERVIDOR_CIVIL, "COMPLEMENTARIAS"));
            assertThat(errores(issues)).isEmpty();
        }

        @Test
        @DisplayName("borde: el alias '1057' es CAS y 'SERVIR' es 30057")
        void aceptaAliasDeRegimen() {
            assertThat(errores(regla.validar(fila(VinculacionColumna.REGIMEN_LABORAL, "1057"))))
                    .isEmpty();
            assertThat(errores(regla.validar(fila(
                    VinculacionColumna.REGIMEN_LABORAL, "SERVIR",
                    VinculacionColumna.GRUPO_SERVIDOR_CIVIL, "DIRECTIVO"))))
                    .isEmpty();
        }

        @Test
        @DisplayName("error: régimen desconocido")
        void regimenDesconocido() {
            assertThat(errores(regla.validar(fila(VinculacionColumna.REGIMEN_LABORAL, "LEY 30057"))))
                    .isNotEmpty();
        }
    }

    @Nested
    @DisplayName("VinculoDatosRule")
    class Vinculo {

        private final VinculoDatosRule regla = new VinculoDatosRule();

        @Test
        @DisplayName("caso feliz: AIRHSP de 6 dígitos, contrato y monto válidos")
        void filaValida() {
            assertThat(errores(regla.validar(fila()))).isEmpty();
        }

        @Test
        @DisplayName("borde: AIRHSP con basura se sanea, no se rechaza")
        void airhspConBasuraSeSanea() {
            final List<RowIssue> issues = regla.validar(
                    fila(VinculacionColumna.CODIGO_AIRHSP, "´000104"));
            assertThat(errores(issues)).isEmpty();
            assertThat(issues).anyMatch(i -> i.severidad() == RowIssue.Severidad.SANEADO);
        }

        @Test
        @DisplayName("borde: monto en texto con moneda se interpreta")
        void montoConMonedaSeInterpreta() {
            final List<RowIssue> issues = regla.validar(
                    fila(VinculacionColumna.MONTO_CONTRATO, "S/. 18,707.14"));
            assertThat(errores(issues)).isEmpty();
        }

        @Test
        @DisplayName("error: monto cero")
        void montoCero() {
            assertThat(errores(regla.validar(fila(VinculacionColumna.MONTO_CONTRATO, 0d))))
                    .isNotEmpty();
        }

        @Test
        @DisplayName("error: sin N° de contrato (es la llave de upsert)")
        void sinNumeroContrato() {
            assertThat(errores(regla.validar(fila(VinculacionColumna.NUMERO_CONTRATO, null))))
                    .extracting(RowIssue::columna)
                    .contains(VinculacionColumna.NUMERO_CONTRATO);
        }
    }

    @Nested
    @DisplayName("FechasYCeseRule")
    class Fechas {

        private final FechasYCeseRule regla = new FechasYCeseRule();

        @Test
        @DisplayName("caso feliz: fechas coherentes")
        void fechasCoherentes() {
            assertThat(errores(regla.validar(fila()))).isEmpty();
        }

        @Test
        @DisplayName("error: inicio de contrato anterior al ingreso")
        void inicioAnteriorAlIngreso() {
            assertThat(errores(regla.validar(fila(
                    VinculacionColumna.FECHA_INICIO_CONTRATO, LocalDateTime.of(2019, 1, 1, 0, 0)))))
                    .isNotEmpty();
        }

        @Test
        @DisplayName("error normativo: cese sin motivo ni documento no habilita LBS")
        void ceseIncompleto() {
            final List<RowIssue> issues = regla.validar(fila(
                    VinculacionColumna.FECHA_CESE, LocalDateTime.of(2026, 5, 31, 0, 0)));
            assertThat(errores(issues))
                    .extracting(RowIssue::columna)
                    .contains(VinculacionColumna.MOTIVO_CESE, VinculacionColumna.DOCUMENTO_CESE);
        }

        @Test
        @DisplayName("borde: 'Indeterminado' en fecha fin = sin término previsto, no error")
        void fechaFinIndeterminado() {
            final List<RowIssue> issues = regla.validar(
                    fila(VinculacionColumna.FECHA_FIN, "Indeterminado"));
            assertThat(errores(issues)).isEmpty();
            assertThat(issues).anyMatch(i -> i.severidad() == RowIssue.Severidad.SANEADO);
        }
    }
}
