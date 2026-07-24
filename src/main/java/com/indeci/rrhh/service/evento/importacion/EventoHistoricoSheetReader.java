package com.indeci.rrhh.service.evento.importacion;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import com.indeci.exception.NegocioException;

/**
 * V012_42 F2 — Lee la hoja {@code sistema} del Excel histórico "DEDUCCIONES DEL TIEMPO DE
 * SERVICIOS" y la convierte en filas crudas. Única responsabilidad: leer (mismo patrón que
 * {@code VinculacionSheetReader} — no valida reglas, no toca la BD).
 */
@Component
public class EventoHistoricoSheetReader {

    /**
     * @param contenidoXlsx bytes del .xlsx subido.
     * @return una fila por registro con datos; las filas totalmente vacías se descartan.
     * @throws NegocioException si el archivo no es legible o no trae la hoja "sistema".
     */
    public List<EventoHistoricoRowRaw> leer(byte[] contenidoXlsx) {
        final List<EventoHistoricoRowRaw> filas = new ArrayList<>();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(contenidoXlsx))) {
            final Sheet hoja = wb.getSheet(EventoHistoricoColumna.HOJA);
            if (hoja == null) {
                throw new NegocioException(
                        "El archivo no contiene la hoja '" + EventoHistoricoColumna.HOJA
                                + "'. Use el Excel oficial de RR.HH.");
            }
            for (int i = EventoHistoricoColumna.PRIMERA_FILA_DATOS; i <= hoja.getLastRowNum(); i++) {
                final Row fila = hoja.getRow(i);
                if (fila == null) {
                    continue;
                }
                final EventoHistoricoRowRaw raw = leerFila(fila, i);
                if (!raw.estaVacia()) {
                    filas.add(raw);
                }
            }
        } catch (NegocioException e) {
            throw e;
        } catch (Exception e) {
            throw new NegocioException("No se pudo leer el Excel: " + e.getMessage());
        }
        return filas;
    }

    private EventoHistoricoRowRaw leerFila(Row fila, int indiceFila) {
        final EventoHistoricoRowRaw raw = new EventoHistoricoRowRaw(indiceFila + 1); // Excel 1-based
        for (EventoHistoricoColumna columna : EventoHistoricoColumna.values()) {
            raw.put(columna, valorDeCelda(fila.getCell(columna.getIndice())));
        }
        return raw;
    }

    private Object valorDeCelda(Cell celda) {
        if (celda == null) {
            return null;
        }
        CellType tipo = celda.getCellType();
        if (tipo == CellType.FORMULA) {
            tipo = celda.getCachedFormulaResultType();
        }
        return switch (tipo) {
            case STRING -> celda.getStringCellValue();
            case NUMERIC -> DateUtil.isCellDateFormatted(celda)
                    ? celda.getLocalDateTimeCellValue()
                    : celda.getNumericCellValue();
            default -> null;
        };
    }
}
