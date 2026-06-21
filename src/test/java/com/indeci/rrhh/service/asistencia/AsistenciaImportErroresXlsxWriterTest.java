package com.indeci.rrhh.service.asistencia;

import com.indeci.rrhh.entity.AsistenciaImportacion;
import com.indeci.rrhh.entity.AsistenciaImportacionFila;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AsistenciaImportErroresXlsxWriterTest {

    private final AsistenciaImportErroresXlsxWriter writer = new AsistenciaImportErroresXlsxWriter();

    @Test
    void generar_soloIncluyeFilasNoLimpias_conColumnasYMinutos() throws Exception {
        AsistenciaImportacion imp = new AsistenciaImportacion();
        imp.setId(5L);
        imp.setPeriodo("2026-05");
        imp.setNombreArchivo("mayo.csv");

        byte[] bytes = writer.generar(imp, List.of(
                fila(1, "VALIDA", "12345678", 0),
                fila(2, "OBSERVADA", "87654321", 5),
                fila(3, "ERROR", "00000000", 0)));

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheetAt(0);
            // cabecera (fila 0) + 2 filas no limpias (OBSERVADA, ERROR)
            assertThat(sheet.getLastRowNum()).isEqualTo(2);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Línea");
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("OBSERVADA");
            assertThat(sheet.getRow(1).getCell(2).getStringCellValue()).isEqualTo("87654321");
            // columna "Tardanza (min)" es la 14 (índice 13), numérica
            assertThat(sheet.getRow(1).getCell(13).getNumericCellValue()).isEqualTo(5.0);
        }
    }

    private AsistenciaImportacionFila fila(int numero, String estado, String dni, int tardanzaMin) {
        AsistenciaImportacionFila f = new AsistenciaImportacionFila();
        f.setNumeroFila(numero);
        f.setEstadoFila(estado);
        f.setDni(dni);
        f.setFecha(LocalDate.of(2026, 5, 10));
        f.setTardanzaMin(tardanzaMin);
        return f;
    }
}
