package com.indeci.rrhh.report.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.sf.jasperreports.engine.JREmptyDataSource;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;

/**
 * Papeleta de Descanso Médico (formato institucional) — valida que la plantilla
 * {@code papeleta_descanso_medico.jrxml} COMPILA y LLENA sin error, con y sin documentos
 * sustentatorios marcados.
 */
class PapeletaDescansoMedicoTemplateTest {

    private Map<String, Object> paramsBase() {
        Map<String, Object> params = new HashMap<>();
        params.put("P_HEADER", getClass().getResourceAsStream("/reportes/img/header_formato.jpg"));
        params.put("P_NOMBRE_TRABAJADOR", "AGUILAR SOTO SANDRA ROCÍO");
        params.put("P_DEPENDENCIA", "UNIDAD DE CONTABILIDAD");
        params.put("P_REGIMEN_NOMBRE", "SERVICIO CIVIL");
        params.put("P_CARGO", "ESPECIALISTA ADMINISTRATIVO");
        params.put("P_NOMBRE_MEDICO", "DR. JUAN PÉREZ RAMOS");
        params.put("P_COLEGIATURA", "CMP 123456");
        params.put("P_FECHA_INICIO", "12/07/2026");
        params.put("P_FECHA_FIN", "15/07/2026");
        params.put("P_DIAS", "4");
        params.put("P_DOC_CITT", Boolean.FALSE);
        params.put("P_DOC_COMPROBANTE_ATENCION", Boolean.FALSE);
        params.put("P_DOC_RECETA", Boolean.FALSE);
        params.put("P_DOC_COMPROBANTE_TRATAMIENTO", Boolean.FALSE);
        params.put("P_FECHA_EMISION", "Lima, 12 de julio de 2026");
        return params;
    }

    private JasperReport compilar() throws Exception {
        try (InputStream jrxml = getClass().getResourceAsStream("/reportes/rrhh/papeleta_descanso_medico.jrxml")) {
            assertNotNull(jrxml, "No se encontró papeleta_descanso_medico.jrxml en el classpath");
            return JasperCompileManager.compileReport(jrxml);
        }
    }

    @Test
    @DisplayName("Con los 4 documentos marcados: compila, llena y exporta PDF de una sola hoja")
    void todosLosDocumentos_generaPdf() throws Exception {
        JasperReport report = compilar();

        Map<String, Object> params = paramsBase();
        params.put("P_DOC_CITT", Boolean.TRUE);
        params.put("P_DOC_COMPROBANTE_ATENCION", Boolean.TRUE);
        params.put("P_DOC_RECETA", Boolean.TRUE);
        params.put("P_DOC_COMPROBANTE_TRATAMIENTO", Boolean.TRUE);

        JasperPrint print = JasperFillManager.fillReport(report, params, new JREmptyDataSource());
        byte[] pdf = JasperExportManager.exportReportToPdf(print);

        assertTrue(pdf != null && pdf.length > 0, "El PDF de la papeleta no debe estar vacío");
        assertEquals(1, print.getPages().size(), "La papeleta debe generarse en una sola página");
    }

    @Test
    @DisplayName("Sin ningún documento marcado: compila, llena y cabe en una hoja")
    void sinDocumentos_generaPdf() throws Exception {
        JasperReport report = compilar();

        JasperPrint print = JasperFillManager.fillReport(report, paramsBase(), new JREmptyDataSource());
        byte[] pdf = JasperExportManager.exportReportToPdf(print);

        assertTrue(pdf != null && pdf.length > 0);
        assertEquals(1, print.getPages().size());
    }
}
