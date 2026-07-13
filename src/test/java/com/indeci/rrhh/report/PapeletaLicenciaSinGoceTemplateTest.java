package com.indeci.rrhh.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;

import org.junit.jupiter.api.Test;

import net.sf.jasperreports.engine.JRParameter;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperReport;

/**
 * SPEC_VACACIONES F9.1-bis — valida que la plantilla de papeleta de licencia sin goce
 * COMPILA (estructura .jrxml correcta). La plantilla se compila en runtime, así que este
 * test es la red de seguridad que atrapa un XML malformado en tiempo de test.
 */
class PapeletaLicenciaSinGoceTemplateTest {

    @Test
    void plantilla_licencia_sin_goce_compila_y_declara_parametros() throws Exception {
        try (InputStream jrxml = getClass()
                .getResourceAsStream("/reportes/rrhh/papeleta_licencia_sin_goce.jrxml")) {

            assertThat(jrxml).as("la plantilla debe existir en resources").isNotNull();

            JasperReport report = JasperCompileManager.compileReport(jrxml);
            assertThat(report).isNotNull();

            // Plantilla unificada Con Goce / Sin Goce: la modalidad y el motivo van en
            // parámetros separados (P_MODALIDAD_LICENCIA / P_MOTIVO_LICENCIA).
            assertThat(report.getParameters())
                    .extracting(JRParameter::getName)
                    .contains("P_HEADER", "P_MODALIDAD_LICENCIA", "P_MOTIVO_LICENCIA", "P_DIAS",
                            "P_FECHA_INICIO", "P_FECHA_FIN", "P_CODIGO_FIRMA",
                            "P_NOMBRE_TRABAJADOR", "P_DEPENDENCIA");
        }
    }
}
