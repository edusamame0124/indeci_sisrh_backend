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

import com.indeci.rrhh.report.dto.CompensacionReporteDto;

import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;

/**
 * Papeleta de Permiso Compensable (formato institucional) — valida que la plantilla
 * {@code papeleta_permiso_compensable.jrxml} COMPILA y LLENA sin error (XML, expresiones y
 * datasource de detalle DETALLE DE COMPENSACIONES).
 */
class PapeletaPermisoCompensableTemplateTest {

    private Map<String, Object> paramsBase() {
        Map<String, Object> params = new HashMap<>();
        params.put("P_HEADER", getClass().getResourceAsStream("/reportes/img/header_formato.jpg"));
        params.put("P_NOMBRE_TRABAJADOR", "AGUILAR SOTO SANDRA ROCÍO");
        params.put("P_DEPENDENCIA", "UNIDAD DE CONTABILIDAD");
        params.put("P_REGIMEN_NOMBRE", "SERVICIO CIVIL");
        params.put("P_CARGO", "ESPECIALISTA ADMINISTRATIVO");
        params.put("P_FECHA_PERMISO", "12/07/2026");
        params.put("P_HORAS_PERMISO", "4");
        params.put("P_HORA_INICIO", "08:00");
        params.put("P_HORA_FIN", "12:00");
        params.put("P_FECHA_EMISION", "Lima, 12 de julio de 2026");
        return params;
    }

    private JasperReport compilar() throws Exception {
        try (InputStream jrxml =
                getClass().getResourceAsStream("/reportes/rrhh/papeleta_permiso_compensable.jrxml")) {
            assertNotNull(jrxml, "No se encontró papeleta_permiso_compensable.jrxml en el classpath");
            return JasperCompileManager.compileReport(jrxml);
        }
    }

    @Test
    @DisplayName("1 compensación: compila, llena y exporta PDF de una sola hoja")
    void unaCompensacion_generaPdf() throws Exception {
        JasperReport report = compilar();

        List<CompensacionReporteDto> filas = List.of(
                new CompensacionReporteDto("4", "13/07/2026", "08:00", "12:00"));

        JasperPrint print = JasperFillManager.fillReport(
                report, paramsBase(), new JRBeanCollectionDataSource(filas));
        byte[] pdf = JasperExportManager.exportReportToPdf(print);

        assertTrue(pdf != null && pdf.length > 0, "El PDF de la papeleta no debe estar vacío");
        assertEquals(1, print.getPages().size(), "La papeleta debe generarse en una sola página");
    }

    @Test
    @DisplayName("Varias compensaciones (peor caso): la tabla reajusta y sigue en una hoja")
    void variasCompensaciones_generaPdf() throws Exception {
        JasperReport report = compilar();

        List<CompensacionReporteDto> filas = List.of(
                new CompensacionReporteDto("4", "13/07/2026", "08:00", "12:00"),
                new CompensacionReporteDto("4", "20/07/2026", "08:00", "12:00"),
                new CompensacionReporteDto("4", "27/07/2026", "08:00", "12:00"),
                new CompensacionReporteDto("4", "03/08/2026", "08:00", "12:00"),
                new CompensacionReporteDto("4", "10/08/2026", "08:00", "12:00"),
                new CompensacionReporteDto("4", "17/08/2026", "08:00", "12:00"));

        JasperPrint print = JasperFillManager.fillReport(
                report, paramsBase(), new JRBeanCollectionDataSource(filas));
        byte[] pdf = JasperExportManager.exportReportToPdf(print);

        assertTrue(pdf != null && pdf.length > 0);
        assertEquals(1, print.getPages().size(), "6 compensaciones deben caber en una sola página");
    }
}
