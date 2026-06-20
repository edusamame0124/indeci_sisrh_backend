package com.indeci.rrhh.service.asistencia;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.entity.AsistenciaImportacion;
import com.indeci.rrhh.entity.AsistenciaImportacionFila;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

/**
 * F4 — Exporta a Excel (XLSX) las filas con ERROR / OBSERVADA / WARN de una
 * importación de asistencia (req 15). Una fila por registro del CSV, con todas
 * las columnas + minutos numéricos + mensaje de validación + línea original.
 */
@Component
public class AsistenciaImportErroresXlsxWriter {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final Set<String> EXPORTABLES = Set.of("ERROR", "OBSERVADA", "WARN");
    private static final String[] HEADERS = {
            "Línea", "Estado", "DNI", "Empleado (sistema)", "Nombre (CSV)", "Fecha", "Día",
            "Entrada prog.", "Salida prog.", "Marca 1", "Marca 2", "Marca 3", "Marca 4",
            "Tardanza (min)", "Refrigerio (min)", "Exc. refrig. (min)", "T. refrig. (min)",
            "T. antes salida (min)", "H. trabajadas (min)", "HE 25% (min)", "HE 35% (min)",
            "HE 100% (min)", "HE total (min)", "Observaciones", "Mensaje de validación",
            "Línea original"
    };

    public byte[] generar(AsistenciaImportacion importacion, List<AsistenciaImportacionFila> filas) {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = wb.createSheet("Errores y observados");
            CellStyle headerStyle = headerStyle(wb);

            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            int r = 1;
            for (AsistenciaImportacionFila f : filas) {
                if (f.getEstadoFila() == null || !EXPORTABLES.contains(f.getEstadoFila())) {
                    continue;
                }
                Row row = sheet.createRow(r++);
                int c = 0;
                setNum(row, c++, f.getNumeroFila());
                setText(row, c++, f.getEstadoFila());
                setText(row, c++, f.getDni());
                setText(row, c++, f.getNombreSistema());
                setText(row, c++, f.getNombreCsv());
                setText(row, c++, f.getFecha() != null ? DATE.format(f.getFecha()) : "");
                setText(row, c++, f.getDiaSemana());
                setText(row, c++, f.getEntradaProg());
                setText(row, c++, f.getSalidaProg());
                setText(row, c++, f.getMarca1());
                setText(row, c++, f.getMarca2());
                setText(row, c++, f.getMarca3());
                setText(row, c++, f.getMarca4());
                setNum(row, c++, f.getTardanzaMin());
                setNum(row, c++, f.getRefrigerioMin());
                setNum(row, c++, f.getExcesoRefrigMin());
                setNum(row, c++, f.getTiempoRefrigMin());
                setNum(row, c++, f.getTiempoAntesSalMin());
                setNum(row, c++, f.getHorasTrabMin());
                setNum(row, c++, f.getHorasExtra25Min());
                setNum(row, c++, f.getHorasExtra35Min());
                setNum(row, c++, f.getHorasExtra100Min());
                setNum(row, c++, f.getHorasExtraTotalMin());
                setText(row, c++, f.getObservacionMarcador());
                setText(row, c++, f.getMensajeValidacion());
                setText(row, c, f.getLineaOriginal());
            }

            for (int i = 0; i < HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }
            sheet.createFreezePane(0, 1);

            wb.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new NegocioException("No se pudo generar el archivo Excel de errores.");
        }
    }

    private CellStyle headerStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_50_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private void setText(Row row, int col, String value) {
        row.createCell(col).setCellValue(value != null ? value : "");
    }

    private void setNum(Row row, int col, Integer value) {
        row.createCell(col).setCellValue(value != null ? value : 0);
    }
}
