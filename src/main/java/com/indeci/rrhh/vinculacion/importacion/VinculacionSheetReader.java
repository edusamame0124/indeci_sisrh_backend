package com.indeci.rrhh.vinculacion.importacion;

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
 * Lee la hoja {@code VINCULACION} del machote oficial y la convierte en filas crudas.
 *
 * <p><b>Única responsabilidad (SRP): leer.</b> No valida reglas de negocio, no resuelve
 * catálogos y no toca la BD — eso vive en el validador, el resolver y el upsert. Aquí
 * solo se traduce Excel → {@link VinculacionRowRaw}.
 *
 * <p>El layout lo define {@link VinculacionColumna} (fuente única de verdad).
 */
@Component
public class VinculacionSheetReader {

    /**
     * @param contenidoXlsx bytes del .xlsx subido.
     * @return una fila por registro con datos; las filas totalmente vacías se descartan.
     * @throws NegocioException si el archivo no es legible o no trae la hoja esperada.
     */
    public List<VinculacionRowRaw> leer(byte[] contenidoXlsx) {
        final List<VinculacionRowRaw> filas = new ArrayList<>();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(contenidoXlsx))) {
            final Sheet hoja = wb.getSheet(VinculacionColumna.HOJA);
            if (hoja == null) {
                throw new NegocioException(
                        "El archivo no contiene la hoja '" + VinculacionColumna.HOJA
                                + "'. Use la plantilla oficial.");
            }
            for (int i = VinculacionColumna.PRIMERA_FILA_DATOS; i <= hoja.getLastRowNum(); i++) {
                final Row fila = hoja.getRow(i);
                if (fila == null) {
                    continue;
                }
                final VinculacionRowRaw raw = leerFila(fila, i);
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

    private VinculacionRowRaw leerFila(Row fila, int indiceFila) {
        final VinculacionRowRaw raw = new VinculacionRowRaw(indiceFila + 1); // Excel es 1-based
        for (VinculacionColumna columna : VinculacionColumna.values()) {
            raw.put(columna, valorDeCelda(fila.getCell(columna.getIndice())));
        }
        return raw;
    }

    /**
     * Devuelve el valor con su tipo natural: fecha, número, booleano o texto. Mantener el
     * tipo (en vez de formatear todo a String) permite que {@link VinculacionRowRaw}
     * distinga una fecha real de un texto como {@code "Indeterminado"}.
     */
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
            case BOOLEAN -> celda.getBooleanCellValue();
            case NUMERIC -> DateUtil.isCellDateFormatted(celda)
                    ? celda.getLocalDateTimeCellValue()
                    : celda.getNumericCellValue();
            default -> null;
        };
    }
}
