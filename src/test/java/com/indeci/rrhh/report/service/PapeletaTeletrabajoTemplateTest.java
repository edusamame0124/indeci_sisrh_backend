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

import com.indeci.rrhh.report.dto.TeletrabajoActividadReporteDto;

import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;

/**
 * Papeleta de Teletrabajo — valida que {@code papeleta_teletrabajo.jrxml} COMPILA, LLENA y
 * EXPORTA PDF (actividades del día por datasource). El layout pixel-perfect se revisa
 * visualmente en el primer render.
 */
class PapeletaTeletrabajoTemplateTest {

    private Map<String, Object> paramsBase() {
        Map<String, Object> params = new HashMap<>();
        params.put("P_HEADER", getClass().getResourceAsStream("/reportes/img/header_formato.jpg"));
        params.put("P_NOMBRE_TRABAJADOR", "JUAN PÉREZ GARCÍA");
        params.put("P_DNI", "41868447");
        params.put("P_CARGO", "ANALISTA ADMINISTRATIVO");
        params.put("P_DEPENDENCIA", "OFICINA DE ADMINISTRACIÓN");
        params.put("P_MODALIDAD", "PARCIAL");
        params.put("P_FECHA_REPORTE", "22/07/2026");
        params.put("P_MEDIO_VERIFICACION", "Correo institucional, videollamada, sistema interno y archivos remitidos.");
        return params;
    }

    private JasperReport compilar() throws Exception {
        try (InputStream jrxml = getClass().getResourceAsStream("/reportes/rrhh/papeleta_teletrabajo.jrxml")) {
            assertNotNull(jrxml, "No se encontró papeleta_teletrabajo.jrxml en el classpath");
            return JasperCompileManager.compileReport(jrxml);
        }
    }

    @Test
    @DisplayName("4 actividades: compila, llena y exporta PDF en una sola hoja")
    void cuatroActividades_generaPdf() throws Exception {
        JasperReport report = compilar();

        List<TeletrabajoActividadReporteDto> actividades = List.of(
                new TeletrabajoActividadReporteDto("1", "Revisión y atención de correos institucionales."),
                new TeletrabajoActividadReporteDto("2", "Elaboración de informe de seguimiento de actividades."),
                new TeletrabajoActividadReporteDto("3", "Coordinación virtual con el equipo de trabajo."),
                new TeletrabajoActividadReporteDto("4", "Actualización de documentos y registro en sistema."));

        JasperPrint print = JasperFillManager.fillReport(
                report, paramsBase(), new JRBeanCollectionDataSource(actividades));
        byte[] pdf = JasperExportManager.exportReportToPdf(print);

        assertTrue(pdf != null && pdf.length > 0, "El PDF de teletrabajo no debe estar vacío");
        assertEquals(1, print.getPages().size(), "La papeleta debe generarse en una sola página");
    }
}
