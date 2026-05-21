package com.indeci.rrhh.service;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.MovimientoPlanillaDetalleResponseDto;
import com.indeci.rrhh.dto.PersonaEmpleadoResponseDto;
import com.indeci.rrhh.entity.MovimientoPlanilla;
import com.indeci.rrhh.repository.MovimientoPlanillaRepository;

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

import java.awt.Color;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/**
 * Spec 011 / B1 — M06 Boleta de pago en PDF (server-side, OpenPDF).
 *
 * Formato compacto institucional (sector público): ocupa la mitad superior de A4,
 * ingresos/descuentos en columnas paralelas, tipografía densa pero legible.
 */
@Service
@RequiredArgsConstructor
public class BoletaPdfService {

    private static final Locale PE = new Locale("es", "PE");

    private final MovimientoPlanillaRepository movimientoRepository;
    private final MovimientoPlanillaDetalleService detalleService;
    private final PersonaService personaService;

    /** Construye el PDF de la boleta del empleado en el período. */
    public byte[] generar(Long empleadoId, String periodo) {

        MovimientoPlanilla mov = movimientoRepository
                .findByEmpleadoIdAndPeriodoAndActivo(empleadoId, periodo, 1)
                .orElseThrow(() -> new NegocioException(
                        "No hay planilla generada para el empleado en el período"));

        List<MovimientoPlanillaDetalleResponseDto> detalle =
                detalleService.listarDetalle(empleadoId, periodo);

        PersonaEmpleadoResponseDto persona = personaService.listar().stream()
                .filter(p -> empleadoId.equals(p.getEmpleadoId()))
                .findFirst()
                .orElse(null);

        List<MovimientoPlanillaDetalleResponseDto> ingresos = detalle.stream()
                .filter(d -> "INGRESO".equalsIgnoreCase(d.getTipoConcepto()))
                .toList();
        List<MovimientoPlanillaDetalleResponseDto> descuentos = detalle.stream()
                .filter(d -> "DESCUENTO".equalsIgnoreCase(d.getTipoConcepto()))
                .toList();
        List<MovimientoPlanillaDetalleResponseDto> aportes = detalle.stream()
                .filter(d -> "APORTE".equalsIgnoreCase(d.getTipoConcepto()))
                .toList();

        double subtotalEssalud = aportes.stream()
                .mapToDouble(d -> d.getMonto() != null ? d.getMonto() : 0d)
                .sum();
        double neto = mov.getNetoPagar() != null ? mov.getNetoPagar() : 0d;
        double cucTotal = neto + subtotalEssalud;

        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        // Márgenes reducidos — boleta compacta centrada en la hoja.
        Document doc = new Document(PageSize.A4, 40f, 40f, 36f, 36f);
        try {
            PdfWriter.getInstance(doc, salida);
            doc.open();

            Font fEntidad = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
            Font fSistema = FontFactory.getFont(FontFactory.HELVETICA, 7);
            Font fTituloDoc = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
            Font fMeta = FontFactory.getFont(FontFactory.HELVETICA, 8);
            Font fSeccion = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8);
            Font fLabel = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7);
            Font fTexto = FontFactory.getFont(FontFactory.HELVETICA, 8);
            Font fNota = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 7);
            Font fNeto = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);
            Color azulInst = new Color(30, 58, 95);

            doc.add(encabezadoInstitucional(periodo, fEntidad, fSistema, fTituloDoc, fMeta));
            doc.add(datosTrabajador(persona, empleadoId, fLabel, fTexto));
            doc.add(bloqueConceptosParalelo(
                    ingresos, descuentos, fSeccion, fLabel, fTexto,
                    mov.getTotalIngresos(), mov.getTotalDescuentos()));
            doc.add(bloqueAportesEmpleador(aportes, subtotalEssalud, cucTotal, fSeccion, fNota, fLabel, fTexto));
            doc.add(barraNeto(mov.getNetoPagar(), fNeto, azulInst));

            doc.close();
        } catch (Exception e) {
            throw new NegocioException(
                    "No se pudo generar el PDF de la boleta: " + e.getMessage());
        }
        return salida.toByteArray();
    }

    // ============================ BLOQUES ============================

    private PdfPTable encabezadoInstitucional(
            String periodo, Font fEntidad, Font fSistema, Font fTituloDoc, Font fMeta) {

        PdfPTable header = new PdfPTable(2);
        header.setWidthPercentage(100);
        header.setSpacingAfter(6f);

        PdfPCell izq = celdaSinBorde();
        Paragraph pIzq = new Paragraph();
        pIzq.add(new Phrase("INSTITUTO NACIONAL DE DEFENSA CIVIL\n", fEntidad));
        pIzq.add(new Phrase("SISRH — Sistema Integrado de RR. HH.", fSistema));
        izq.addElement(pIzq);
        izq.setHorizontalAlignment(Element.ALIGN_LEFT);
        izq.setPadding(0f);
        header.addCell(izq);

        PdfPCell der = celdaSinBorde();
        Paragraph pDer = new Paragraph();
        pDer.setAlignment(Element.ALIGN_RIGHT);
        pDer.add(new Phrase("BOLETA DE PAGO\n", fTituloDoc));
        pDer.add(new Phrase("Período: " + periodo + "\n", fMeta));
        pDer.add(new Phrase("Emisión: " + fechaHoy(), fMeta));
        der.addElement(pDer);
        der.setHorizontalAlignment(Element.ALIGN_RIGHT);
        der.setPadding(0f);
        header.addCell(der);

        return header;
    }

    private PdfPTable datosTrabajador(
            PersonaEmpleadoResponseDto persona, Long empleadoId, Font fLabel, Font fTexto) {

        PdfPTable grid = new PdfPTable(4);
        grid.setWidthPercentage(100);
        grid.setSpacingBefore(2f);
        grid.setSpacingAfter(8f);
        try {
            grid.setWidths(new float[]{2.2f, 1.2f, 1.2f, 1f});
        } catch (Exception ignored) {
            // fallback ancho uniforme
        }

        String nombre = persona != null ? persona.getNombreCompleto() : "Empleado " + empleadoId;
        String dni = persona != null && persona.getDni() != null ? persona.getDni() : "—";
        String codigo = persona != null && persona.getCodigoInterno() != null
                ? persona.getCodigoInterno() : "—";
        String estado = persona != null && persona.getEstado() != null ? persona.getEstado() : "—";

        celdaDato(grid, "Trabajador", nombre, fLabel, fTexto);
        celdaDato(grid, "DNI", dni, fLabel, fTexto);
        celdaDato(grid, "Código", codigo, fLabel, fTexto);
        celdaDato(grid, "Estado", estado, fLabel, fTexto);

        return grid;
    }

    private PdfPTable bloqueConceptosParalelo(
            List<MovimientoPlanillaDetalleResponseDto> ingresos,
            List<MovimientoPlanillaDetalleResponseDto> descuentos,
            Font fSeccion, Font fLabel, Font fTexto,
            Double totalIngresos, Double totalDescuentos) throws Exception {

        PdfPTable cols = new PdfPTable(2);
        cols.setWidthPercentage(100);
        cols.setWidths(new float[]{1f, 1f});
        cols.setSpacingAfter(6f);

        PdfPCell cIngresos = celdaSinBorde();
        cIngresos.addElement(tablaSeccion(
                "INGRESOS", ingresos, fSeccion, fLabel, fTexto,
                "Total ingresos", totalIngresos));
        cIngresos.setPaddingRight(4f);
        cols.addCell(cIngresos);

        PdfPCell cDescuentos = celdaSinBorde();
        cDescuentos.addElement(tablaSeccion(
                "DESCUENTOS", descuentos, fSeccion, fLabel, fTexto,
                "Total descuentos", totalDescuentos));
        cDescuentos.setPaddingLeft(4f);
        cols.addCell(cDescuentos);

        return cols;
    }

    private PdfPTable bloqueAportesEmpleador(
            List<MovimientoPlanillaDetalleResponseDto> aportes,
            double subtotalEssalud, double cucTotal,
            Font fSeccion, Font fNota, Font fLabel, Font fTexto) {

        PdfPTable bloque = new PdfPTable(1);
        bloque.setWidthPercentage(100);
        bloque.setSpacingAfter(6f);

        PdfPCell titulo = celdaSinBorde();
        Paragraph pTit = new Paragraph("APORTES EMPLEADOR (INFORMATIVO)", fSeccion);
        pTit.setSpacingAfter(2f);
        titulo.addElement(pTit);
        titulo.addElement(new Paragraph(
                "No descuenta al trabajador — costo entidad (ESSALUD / CUC).", fNota));
        titulo.setPaddingBottom(4f);
        bloque.addCell(titulo);

        PdfPCell tablaCell = celdaSinBorde();
        tablaCell.addElement(tablaConceptos(aportes, fLabel, fTexto));
        bloque.addCell(tablaCell);

        PdfPTable resumen = new PdfPTable(2);
        resumen.setWidthPercentage(55);
        resumen.setHorizontalAlignment(Element.ALIGN_RIGHT);
        filaTotal(resumen, "Subtotal ESSALUD", subtotalEssalud, fLabel, fTexto);
        filaTotal(resumen, "CUC (neto + ESSALUD)", cucTotal, fLabel, fLabel);

        PdfPCell resCell = celdaSinBorde();
        resCell.addElement(resumen);
        resCell.setPaddingTop(2f);
        bloque.addCell(resCell);

        return bloque;
    }

    private PdfPTable barraNeto(Double neto, Font fNeto, Color fondo) {
        PdfPTable barra = new PdfPTable(2);
        barra.setWidthPercentage(100);
        barra.setSpacingBefore(4f);

        PdfPCell lbl = celda(new Phrase("NETO A PAGAR", fNeto), Element.ALIGN_LEFT);
        lbl.setBackgroundColor(fondo);
        lbl.setBorderColor(fondo);
        lbl.setPadding(5f);

        PdfPCell val = celda(new Phrase("S/ " + monto(neto), fNeto), Element.ALIGN_RIGHT);
        val.setBackgroundColor(fondo);
        val.setBorderColor(fondo);
        val.setPadding(5f);

        barra.addCell(lbl);
        barra.addCell(val);
        return barra;
    }

    // ============================ HELPERS ============================

    private PdfPTable tablaSeccion(
            String titulo,
            List<MovimientoPlanillaDetalleResponseDto> filas,
            Font fSeccion, Font fLabel, Font fTexto,
            String etiquetaTotal, Double total) {

        PdfPTable wrap = new PdfPTable(1);
        wrap.setWidthPercentage(100);

        PdfPCell titCell = celdaSinBorde();
        Paragraph p = new Paragraph(titulo, fSeccion);
        p.setSpacingAfter(3f);
        titCell.addElement(p);
        titCell.setPadding(0f);
        wrap.addCell(titCell);

        PdfPCell tblCell = celdaSinBorde();
        tblCell.addElement(tablaConceptos(filas, fLabel, fTexto));
        wrap.addCell(tblCell);

        if (total != null) {
            PdfPTable pie = new PdfPTable(2);
            pie.setWidthPercentage(100);
            filaTotal(pie, etiquetaTotal, total, fLabel, fLabel);
            PdfPCell pieCell = celdaSinBorde();
            pieCell.addElement(pie);
            pieCell.setPaddingTop(2f);
            wrap.addCell(pieCell);
        }

        return wrap;
    }

    private void celdaDato(PdfPTable grid, String etiqueta, String valor,
                         Font fLabel, Font fTexto) {
        PdfPCell c = new PdfPCell();
        Paragraph p = new Paragraph();
        p.add(new Phrase(etiqueta.toUpperCase() + "\n", fLabel));
        p.add(new Phrase(valor, fTexto));
        c.addElement(p);
        c.setPadding(3f);
        c.setBackgroundColor(new Color(248, 250, 252));
        grid.addCell(c);
    }

    private PdfPTable tablaConceptos(
            List<MovimientoPlanillaDetalleResponseDto> filas, Font fLabel, Font fTexto) {

        PdfPTable tabla = new PdfPTable(2);
        tabla.setWidthPercentage(100);

        tabla.addCell(celdaEncabezado(new Phrase("Concepto", fLabel), Element.ALIGN_LEFT));
        tabla.addCell(celdaEncabezado(new Phrase("S/", fLabel), Element.ALIGN_RIGHT));

        if (filas.isEmpty()) {
            PdfPCell vacia = celda(new Phrase("—", fTexto), Element.ALIGN_LEFT);
            vacia.setColspan(2);
            tabla.addCell(vacia);
            return tabla;
        }
        for (MovimientoPlanillaDetalleResponseDto d : filas) {
            tabla.addCell(celda(new Phrase(d.getConcepto(), fTexto), Element.ALIGN_LEFT));
            tabla.addCell(celda(new Phrase(monto(d.getMonto()), fTexto), Element.ALIGN_RIGHT));
        }
        return tabla;
    }

    private void filaTotal(PdfPTable tabla, String etiqueta, double valor,
                           Font fEtiqueta, Font fValor) {
        tabla.addCell(celda(new Phrase(etiqueta, fEtiqueta), Element.ALIGN_LEFT));
        tabla.addCell(celda(new Phrase("S/ " + monto(valor), fValor), Element.ALIGN_RIGHT));
    }

    private PdfPCell celdaSinBorde() {
        PdfPCell c = new PdfPCell();
        c.setBorder(PdfPCell.NO_BORDER);
        return c;
    }

    private PdfPCell celda(Phrase contenido, int alineacion) {
        PdfPCell celda = new PdfPCell(contenido);
        celda.setHorizontalAlignment(alineacion);
        celda.setPaddingTop(2.5f);
        celda.setPaddingBottom(2.5f);
        celda.setPaddingLeft(4f);
        celda.setPaddingRight(4f);
        return celda;
    }

    private PdfPCell celdaEncabezado(Phrase contenido, int alineacion) {
        PdfPCell celda = celda(contenido, alineacion);
        celda.setBackgroundColor(new Color(241, 245, 249));
        celda.setPaddingTop(3f);
        celda.setPaddingBottom(3f);
        return celda;
    }

    private String monto(Double valor) {
        return monto(valor != null ? valor.doubleValue() : 0d);
    }

    private String monto(double valor) {
        NumberFormat nf = NumberFormat.getNumberInstance(PE);
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        return nf.format(valor);
    }

    private String fechaHoy() {
        return java.time.LocalDate.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }
}
