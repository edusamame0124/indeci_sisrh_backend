package com.indeci.rrhh.report.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.indeci.rrhh.report.dto.VacacionDetReporteDto;

import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;

/**
 * Papeleta de Vacaciones (formato institucional) — valida que la plantilla
 * {@code papeleta_vacaciones.jrxml} COMPILA y LLENA sin error (XML, expresiones y datasource
 * de detalle). No verifica el layout pixel-perfect (eso se revisa visualmente en el primer
 * render), pero blinda contra plantillas rotas antes de desplegar.
 */
class PapeletaVacacionesTemplateTest {

    private Map<String, Object> paramsBase() {
        Map<String, Object> params = new HashMap<>();
        params.put("P_HEADER", getClass().getResourceAsStream("/reportes/img/header_formato.jpg"));
        params.put("P_NOMBRE_TRABAJADOR", "AGUILAR SOTO SANDRA ROCÍO");
        params.put("P_DEPENDENCIA", "UNIDAD DE CONTABILIDAD");
        params.put("P_REGIMEN_NOMBRE", "SERVICIO CIVIL");
        params.put("P_CARGO", "ESPECIALISTA ADMINISTRATIVO");
        params.put("P_CHK_PROGRAMACION", Boolean.TRUE);
        params.put("P_CHK_ADELANTO", Boolean.FALSE);
        params.put("P_CHK_FRACCIONAMIENTO", Boolean.FALSE);
        params.put("P_CHK_REPROGRAMACION", Boolean.FALSE);
        params.put("P_FECHA_EMISION", "Lima, 12 de julio de 2026");
        return params;
    }

    private JasperReport compilar() throws Exception {
        try (InputStream jrxml = getClass().getResourceAsStream("/reportes/rrhh/papeleta_vacaciones.jrxml")) {
            assertNotNull(jrxml, "No se encontró papeleta_vacaciones.jrxml en el classpath");
            return JasperCompileManager.compileReport(jrxml);
        }
    }

    @Test
    @DisplayName("Programación (1 período): compila, llena y exporta PDF no vacío")
    void programacion_unaFila_generaPdf() throws Exception {
        JasperReport report = compilar();

        List<VacacionDetReporteDto> filas = List.of(
                new VacacionDetReporteDto("13/07/2026", "04/08/2026", "23"));

        JasperPrint print = JasperFillManager.fillReport(
                report, paramsBase(), new JRBeanCollectionDataSource(filas));
        byte[] pdf = JasperExportManager.exportReportToPdf(print);

        assertTrue(pdf != null && pdf.length > 0, "El PDF de la papeleta no debe estar vacío");
        // El fix: firmas y todo el contenido deben caber en UNA sola hoja.
        assertEquals(1, print.getPages().size(), "La papeleta debe generarse en una sola página");
    }

    @Test
    @DisplayName("Fraccionamiento (4 períodos = peor caso): el detalle reflows y sigue en una hoja")
    void fraccionamiento_cuatroFilas_generaPdf() throws Exception {
        JasperReport report = compilar();

        Map<String, Object> params = paramsBase();
        params.put("P_CHK_PROGRAMACION", Boolean.FALSE);
        params.put("P_CHK_FRACCIONAMIENTO", Boolean.TRUE);

        List<VacacionDetReporteDto> filas = List.of(
                new VacacionDetReporteDto("13/07/2026", "19/07/2026", "7"),
                new VacacionDetReporteDto("03/08/2026", "09/08/2026", "7"),
                new VacacionDetReporteDto("14/09/2026", "20/09/2026", "7"),
                new VacacionDetReporteDto("05/10/2026", "13/10/2026", "9"));

        JasperPrint print = JasperFillManager.fillReport(
                report, params, new JRBeanCollectionDataSource(filas));
        byte[] pdf = JasperExportManager.exportReportToPdf(print);

        assertTrue(pdf != null && pdf.length > 0);
        // Peor caso (4 fracciones): todo (firmas incluidas) debe caber en una sola hoja.
        assertEquals(1, print.getPages().size(), "4 fracciones deben caber en una sola página");
    }
}
