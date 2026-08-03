package com.indeci.rrhh.service.asistencia;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.AsistenciaResumenPeriodoRowDto;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Exporta a XLSX el reporte de asistencia consolidado por período, formato
 * institucional {@code docs/reporte_asistencia.xlsx} (grupos TARDANZA /
 * PERMISOS PARTICULARES / FALTAS con cabecera fusionada de 2 filas).
 */
@Component
public class AsistenciaResumenPeriodoXlsxWriter {

    private static final String[] HEADERS = {
            "Periodo", "DNI", "Apellidos y Nombres", "Regimen", "Direccion o Unidad",
            "Remuneracion Mensual", "Costo Dia", "Costo Hora", "Costo Minuto",
            "Minutos X Descuento 1", "S/ Descuento 1 - diaria (> 10 min)",
            "Minutos <= 10 min Total Acumulados", "Minutos <= 10 min Excedentes al tope",
            "S/ Descuento 2 - mensual (Excedentes al tope)",
            "Total Minutos por descuento 1 y 2", "S/ Descuento 1 y 2",
            "Minutos Permisos particulares", "S/ Descuento por permisos particulares",
            "Total de Faltas", "Descuento por faltas",
    };

    /** [inicio, fin] 0-based inclusive de cada grupo, para la fila de cabecera fusionada. */
    private static final int TARDANZA_INI = 9;
    private static final int TARDANZA_FIN = 15;
    private static final int PERMISOS_INI = 16;
    private static final int PERMISOS_FIN = 17;
    private static final int FALTAS_INI = 18;
    private static final int FALTAS_FIN = 19;

    public byte[] generar(List<AsistenciaResumenPeriodoRowDto> filas) {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = wb.createSheet("Asistencia consolidada");
            CellStyle grupoStyle = grupoStyle(wb);
            CellStyle headerStyle = headerStyle(wb);

            escribirGrupos(sheet, grupoStyle);
            escribirCabeceras(sheet, headerStyle);
            escribirFilas(sheet, filas);

            for (int i = 0; i < HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }
            sheet.createFreezePane(0, 2);

            wb.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new NegocioException("No se pudo generar el archivo Excel de asistencia.");
        }
    }

    private void escribirGrupos(Sheet sheet, CellStyle grupoStyle) {
        Row grupos = sheet.createRow(0);
        setGrupo(grupos, TARDANZA_INI, "TARDANZA", grupoStyle);
        setGrupo(grupos, PERMISOS_INI, "PERMISOS PARTICULARES", grupoStyle);
        setGrupo(grupos, FALTAS_INI, "FALTAS", grupoStyle);

        sheet.addMergedRegion(new CellRangeAddress(0, 0, TARDANZA_INI, TARDANZA_FIN));
        sheet.addMergedRegion(new CellRangeAddress(0, 0, PERMISOS_INI, PERMISOS_FIN));
        sheet.addMergedRegion(new CellRangeAddress(0, 0, FALTAS_INI, FALTAS_FIN));
    }

    private void setGrupo(Row row, int col, String texto, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(texto);
        cell.setCellStyle(style);
    }

    private void escribirCabeceras(Sheet sheet, CellStyle headerStyle) {
        Row header = sheet.createRow(1);
        for (int i = 0; i < HEADERS.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(HEADERS[i]);
            cell.setCellStyle(headerStyle);
        }
    }

    private void escribirFilas(Sheet sheet, List<AsistenciaResumenPeriodoRowDto> filas) {
        int r = 2;
        for (AsistenciaResumenPeriodoRowDto f : filas) {
            Row row = sheet.createRow(r++);
            int c = 0;
            setText(row, c++, f.getPeriodo());
            setText(row, c++, f.getDni());
            setText(row, c++, f.getApellidosNombres());
            setText(row, c++, f.getRegimen());
            setText(row, c++, f.getDireccionUnidad());
            setNum(row, c++, f.getRemuneracionMensual());
            setNum(row, c++, f.getCostoDia());
            setNum(row, c++, f.getCostoHora());
            setNum(row, c++, f.getCostoMinuto());
            setNum(row, c++, f.getMinutosDescuento1());
            setNum(row, c++, f.getDescuento1Diaria());
            setNum(row, c++, f.getMinutosMenorAcumTotal());
            setNum(row, c++, f.getMinutosExcedentesTope());
            setNum(row, c++, f.getDescuento2Mensual());
            setNum(row, c++, f.getTotalMinutosDescuento1y2());
            setNum(row, c++, f.getTotalDescuento1y2());
            setNum(row, c++, f.getMinutosPermisosParticulares());
            setNum(row, c++, f.getDescuentoPermisosParticulares());
            setNum(row, c++, f.getTotalFaltas());
            setNum(row, c, f.getDescuentoFaltas());
        }
    }

    private CellStyle grupoStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
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

    private void setNum(Row row, int col, double value) {
        row.createCell(col).setCellValue(value);
    }

    private void setNum(Row row, int col, int value) {
        row.createCell(col).setCellValue(value);
    }
}
