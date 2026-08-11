package com.indeci.rrhh.service.asistencia;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.AsistenciaMarcacionRowDto;
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
import java.util.Locale;

/**
 * Exporta a XLSX el reporte de marcaciones diarias, formato institucional
 * {@code docs/EJEMPLO REPORTE EXCEL SISTEMA.xlsx} (tabla plana, sin cabecera
 * fusionada — 1 fila por día x empleado).
 */
@Component
public class AsistenciaMarcacionesXlsxWriter {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy", new Locale("es", "PE"));

    private static final String[] HEADERS = {
            "DNI", "Nombre y apellido", "Regimen laboral", "Unidad Organica / Oficina",
            "Dia de semana", "Fecha", "Horario de entrada", "Horario de salida",
            "Marcacion de entrada", "Marcacion de salida", "Horas extras del dia",
            "Condicion", "Permiso / Papeleta / Teletrabajo",
            "Salida anticipada", "Horas/Minutos dejados de trabajar",
    };

    public byte[] generar(List<AsistenciaMarcacionRowDto> filas) {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = wb.createSheet("Marcaciones");
            CellStyle headerStyle = headerStyle(wb);

            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            int r = 1;
            for (AsistenciaMarcacionRowDto f : filas) {
                Row row = sheet.createRow(r++);
                int c = 0;
                setText(row, c++, f.getDni());
                setText(row, c++, f.getNombreCompleto());
                setText(row, c++, f.getRegimen());
                setText(row, c++, f.getUnidadOrganica());
                setText(row, c++, f.getDiaSemana());
                setText(row, c++, f.getFecha() != null ? FECHA.format(f.getFecha()) : "");
                setText(row, c++, f.getHorarioEntrada());
                setText(row, c++, f.getHorarioSalida());
                setText(row, c++, f.getMarcaEntrada());
                setText(row, c++, f.getMarcaSalida());
                setMinutos(row, c++, f.getHorasExtraDiaMin());
                setText(row, c++, f.getCondicion());
                setText(row, c++, f.getPermisoPapeletaTeletrabajo());
                setText(row, c++, f.getMinutosSalidaAnticipada() != null ? "Si" : "");
                setMinutos(row, c, f.getMinutosSalidaAnticipada());
            }

            for (int i = 0; i < HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }
            sheet.createFreezePane(0, 1);

            wb.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new NegocioException("No se pudo generar el archivo Excel de marcaciones.");
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
        style.setWrapText(true);
        return style;
    }

    private void setText(Row row, int col, String value) {
        row.createCell(col).setCellValue(value != null ? value : "");
    }

    /** Minutos como "Xh Ym" legible, o vacío si no se pudo calcular (marcas incompletas). */
    private void setMinutos(Row row, int col, Integer minutos) {
        if (minutos == null) {
            row.createCell(col).setCellValue("");
            return;
        }
        int h = minutos / 60;
        int m = Math.abs(minutos % 60);
        String signo = minutos < 0 ? "-" : "";
        row.createCell(col).setCellValue(h == 0 ? signo + m + "m" : signo + Math.abs(h) + "h " + m + "m");
    }
}
