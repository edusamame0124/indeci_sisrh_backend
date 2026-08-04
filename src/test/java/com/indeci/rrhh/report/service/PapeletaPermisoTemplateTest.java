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
 * Papeleta de Permiso (formato institucional) — valida que la plantilla
 * {@code papeleta_permiso.jrxml} COMPILA y LLENA sin error para los distintos motivos
 * (códigos 001-007), incluyendo las líneas opcionales de Lugar (Comisión de Servicio) y
 * Descripción (Otros). No verifica layout pixel-perfect, pero blinda contra plantillas rotas.
 */
class PapeletaPermisoTemplateTest {

    private Map<String, Object> paramsBase() {
        Map<String, Object> params = new HashMap<>();
        params.put("P_HEADER", getClass().getResourceAsStream("/reportes/img/header_formato.jpg"));
        params.put("P_NOMBRE_TRABAJADOR", "AGUILAR SOTO SANDRA ROCÍO");
        params.put("P_DEPENDENCIA", "UNIDAD DE CONTABILIDAD");
        params.put("P_REGIMEN_NOMBRE", "SERVICIO CIVIL");
        params.put("P_CARGO", "ESPECIALISTA ADMINISTRATIVO");
        params.put("P_PERMISO_ASUNTOS", Boolean.FALSE);
        params.put("P_CITA_MEDICA", Boolean.FALSE);
        params.put("P_CITACION", Boolean.FALSE);
        params.put("P_OMISION_REGISTRO", Boolean.FALSE);
        params.put("P_TARDANZA", Boolean.FALSE);
        params.put("P_COMISION_SERVICIO", Boolean.FALSE);
        params.put("P_OTROS", Boolean.FALSE);
        params.put("P_LUGAR", "");
        params.put("P_DESCRIPCION_OTROS", "");
        params.put("P_FECHA_PERMISO", "12/07/2026");
        params.put("P_HORAS_DIARIAS", "4");
        params.put("P_HORA_SALIDA", "08:00");
        params.put("P_HORA_INGRESO", "12:00");
        params.put("P_FECHA_EMISION", "Lima, 12 de julio de 2026");
        return params;
    }

    private JasperReport compilar() throws Exception {
        try (InputStream jrxml = getClass().getResourceAsStream("/reportes/rrhh/papeleta_permiso.jrxml")) {
            assertNotNull(jrxml, "No se encontró papeleta_permiso.jrxml en el classpath");
            return JasperCompileManager.compileReport(jrxml);
        }
    }

    @Test
    @DisplayName("Cita Médica (sin texto extra): compila, llena y exporta PDF de una sola hoja")
    void citaMedica_generaPdf() throws Exception {
        JasperReport report = compilar();

        Map<String, Object> params = paramsBase();
        params.put("P_CITA_MEDICA", Boolean.TRUE);

        JasperPrint print = JasperFillManager.fillReport(report, params, new JREmptyDataSource());
        byte[] pdf = JasperExportManager.exportReportToPdf(print);

        assertTrue(pdf != null && pdf.length > 0, "El PDF de la papeleta no debe estar vacío");
        assertEquals(1, print.getPages().size(), "La papeleta debe generarse en una sola página");
    }

    @Test
    @DisplayName("Comisión de Servicio (con Lugar): compila, llena y cabe en una hoja")
    void comisionServicio_conLugar_generaPdf() throws Exception {
        JasperReport report = compilar();

        Map<String, Object> params = paramsBase();
        params.put("P_COMISION_SERVICIO", Boolean.TRUE);
        params.put("P_LUGAR", "Chiclayo - Lambayeque");

        JasperPrint print = JasperFillManager.fillReport(report, params, new JREmptyDataSource());
        byte[] pdf = JasperExportManager.exportReportToPdf(print);

        assertTrue(pdf != null && pdf.length > 0);
        assertEquals(1, print.getPages().size());
    }

    @Test
    @DisplayName("Otros (con descripción larga): reajusta y sigue en una sola hoja")
    void otros_conDescripcionLarga_generaPdf() throws Exception {
        JasperReport report = compilar();

        Map<String, Object> params = paramsBase();
        params.put("P_OTROS", Boolean.TRUE);
        params.put("P_DESCRIPCION_OTROS",
                "Trámite personal ante entidad bancaria para regularización de cuenta de haberes, "
                        + "requiere presencia física en horario de atención al público.");

        JasperPrint print = JasperFillManager.fillReport(report, params, new JREmptyDataSource());
        byte[] pdf = JasperExportManager.exportReportToPdf(print);

        assertTrue(pdf != null && pdf.length > 0);
        assertEquals(1, print.getPages().size());
    }
}
