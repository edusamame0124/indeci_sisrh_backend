package com.indeci.rrhh.service;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.AsistenciaDiaDto;
import com.indeci.rrhh.dto.AsistenciaResponseDto;
import com.indeci.rrhh.dto.PersonaResumenDto;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AsistenciaPdfService {

    private static final Locale PE = new Locale("es", "PE");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy", PE);

    private final AsistenciaService asistenciaService;
    private final PersonaService personaService;

    public byte[] generar(Long empleadoId, String periodo) {
        AsistenciaResponseDto asistencia = asistenciaService.obtener(empleadoId, periodo);
        PersonaResumenDto persona = personaService.listar().stream()
                .filter(p -> empleadoId.equals(p.getEmpleadoId()))
                .findFirst()
                .orElse(null);

        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36f, 36f, 32f, 32f);
        try {
            PdfWriter.getInstance(doc, salida);
            doc.open();

            Font titulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
            Font seccion = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8);
            Font label = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7);
            Font texto = FontFactory.getFont(FontFactory.HELVETICA, 8);
            Color navy = new Color(10, 34, 64);

            doc.add(encabezado(periodo, titulo, texto));
            doc.add(datosTrabajador(persona, empleadoId, label, texto));
            doc.add(resumen(asistencia, seccion, label, texto, navy));
            doc.add(detalle(asistencia.getDias(), label, texto));
            doc.close();
        } catch (Exception ex) {
            throw new NegocioException("No se pudo generar el PDF de asistencia.");
        }
        return salida.toByteArray();
    }

    private PdfPTable encabezado(String periodo, Font titulo, Font texto) {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingAfter(8f);

        PdfPCell izq = celdaSinBorde();
        izq.addElement(new Paragraph("INSTITUTO NACIONAL DE DEFENSA CIVIL", titulo));
        izq.addElement(new Paragraph("SISRH - Sistema Integrado de RR. HH.", texto));
        table.addCell(izq);

        PdfPCell der = celdaSinBorde();
        Paragraph p = new Paragraph();
        p.setAlignment(Element.ALIGN_RIGHT);
        p.add(new Phrase("REPORTE DE ASISTENCIA\n", titulo));
        p.add(new Phrase("Periodo: " + periodo + "\n", texto));
        p.add(new Phrase("Emision: " + FECHA.format(LocalDate.now()), texto));
        der.addElement(p);
        table.addCell(der);
        return table;
    }

    private PdfPTable datosTrabajador(PersonaResumenDto persona, Long empleadoId, Font label, Font texto) {
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setSpacingAfter(8f);
        celdaDato(table, "Trabajador", persona != null ? persona.getNombreCompleto() : "Empleado " + empleadoId,
                label, texto);
        celdaDato(table, "DNI", persona != null ? persona.getDni() : "-", label, texto);
        celdaDato(table, "Codigo", persona != null ? persona.getCodigoInterno() : "-", label, texto);
        celdaDato(table, "Estado", persona != null ? persona.getEstado() : "-", label, texto);
        return table;
    }

    private PdfPTable resumen(AsistenciaResponseDto asistencia, Font seccion, Font label, Font texto, Color navy) {
        PdfPTable table = new PdfPTable(6);
        table.setWidthPercentage(100);
        table.setSpacingAfter(8f);
        PdfPCell title = new PdfPCell(new Phrase("RESUMEN", seccion));
        title.setColspan(6);
        title.setBackgroundColor(navy);
        title.setPadding(5f);
        title.setBorderColor(Color.LIGHT_GRAY);
        table.addCell(title);
        celdaDato(table, "Estado", asistencia.getEstado(), label, texto);
        celdaDato(table, "Laborados", asistencia.getDiasLaborados(), label, texto);
        celdaDato(table, "Faltas", asistencia.getDiasFalta(), label, texto);
        celdaDato(table, "Min. tardanza", asistencia.getTotalMinTardanza(), label, texto);
        celdaDato(table, "Desc. tardanza", asistencia.getDescuentoTardanza(), label, texto);
        celdaDato(table, "Desc. falta", asistencia.getDescuentoFalta(), label, texto);
        return table;
    }

    private PdfPTable detalle(List<AsistenciaDiaDto> dias, Font label, Font texto) {
        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        encabezadoTabla(table, "Fecha", label);
        encabezadoTabla(table, "Tipo", label);
        encabezadoTabla(table, "Tardanza", label);
        encabezadoTabla(table, "Marcas", label);
        encabezadoTabla(table, "Observacion", label);
        for (AsistenciaDiaDto dia : dias) {
            table.addCell(celdaTexto(dia.getDia() != null ? FECHA.format(dia.getDia()) : "-", texto));
            table.addCell(celdaTexto(valor(dia.getTipoDia()), texto));
            table.addCell(celdaTexto(String.valueOf(valor(dia.getMinutosTardanza())), texto));
            table.addCell(celdaTexto(valor(dia.getMarcaEntrada()) + " / " + valor(dia.getMarcaSalida()), texto));
            table.addCell(celdaTexto(valor(dia.getObservacion()), texto));
        }
        return table;
    }

    private void celdaDato(PdfPTable table, String etiqueta, Object valor, Font label, Font texto) {
        PdfPCell cell = celdaTexto(etiqueta + "\n" + valor(valor), texto);
        cell.setPhrase(new Phrase(etiqueta + "\n" + valor(valor), label));
        table.addCell(cell);
    }

    private void encabezadoTabla(PdfPTable table, String texto, Font label) {
        PdfPCell cell = new PdfPCell(new Phrase(texto, label));
        cell.setPadding(4f);
        cell.setBackgroundColor(new Color(245, 246, 248));
        cell.setBorderColor(Color.LIGHT_GRAY);
        table.addCell(cell);
    }

    private PdfPCell celdaTexto(String texto, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(valor(texto), font));
        cell.setPadding(4f);
        cell.setBorderColor(Color.LIGHT_GRAY);
        return cell;
    }

    private PdfPCell celdaSinBorde() {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(PdfPCell.NO_BORDER);
        return cell;
    }

    private String valor(Object value) {
        return value != null ? value.toString() : "-";
    }

    private int valor(Integer value) {
        return value != null ? value : 0;
    }
}
