package com.indeci.rrhh.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import com.indeci.rrhh.dto.export.PlanillaConsolidadaRowDto;

/**
 * P0 — Escritor POI de la Planilla CAS Consolidada.
 *
 * <p>Define las columnas de forma <b>declarativa</b> (lista ordenada con bloque,
 * título, tipo y extractor). El XLSX agrupa columnas por bloque con encabezado
 * de color, congela cabeceras, activa autofiltro, totaliza columnas monetarias
 * y añade hojas de Parámetros y Leyenda.</p>
 *
 * <p>Celdas sin dato salen vacías (la columna nunca se omite — spec).</p>
 */
@Component
public class PlanillaCasExcelWriter {

    private static final DateTimeFormatter F_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter F_FECHAHORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private enum Tipo { TEXTO, CODIGO, NUMERO, MONEDA, FECHA, FECHAHORA }

    private record Col(String bloque, String titulo, Tipo tipo, boolean totalizar,
                       Function<PlanillaConsolidadaRowDto, Object> ext) {}

    /** Color de cabecera por bloque (RGB). */
    private static final Map<String, byte[]> COLOR_BLOQUE = new LinkedHashMap<>();
    static {
        COLOR_BLOQUE.put("1. Datos personales",       rgb(0x2F,0x54,0x96));
        COLOR_BLOQUE.put("2. Datos laborales",        rgb(0x37,0x56,0x23));
        COLOR_BLOQUE.put("3. Banco / Abono",          rgb(0x83,0x3C,0x00));
        COLOR_BLOQUE.put("4. Régimen pensionario",    rgb(0x44,0x72,0xC4));
        COLOR_BLOQUE.put("5. Suspensión 4ta",         rgb(0x70,0x30,0xA0));
        COLOR_BLOQUE.put("6. Presupuesto / AIRHSP",   rgb(0x1F,0x49,0x7D));
        COLOR_BLOQUE.put("7. Contrato / Plaza",       rgb(0x7F,0x7F,0x7F));
        COLOR_BLOQUE.put("8. Remuneración / DS",      rgb(0x15,0x73,0x47));
        COLOR_BLOQUE.put("9. Asistencia",             rgb(0xE3,0x6C,0x09));
        COLOR_BLOQUE.put("10. Base imponible",        rgb(0x16,0x36,0x5C));
        COLOR_BLOQUE.put("11. EsSalud / EPS",         rgb(0x00,0x70,0xC0));
        COLOR_BLOQUE.put("12. IR 4ta categoría",      rgb(0xC0,0x00,0x00));
        COLOR_BLOQUE.put("13. AFP / ONP",             rgb(0x4F,0x62,0x28));
        COLOR_BLOQUE.put("14. Descuentos terceros",   rgb(0x95,0x37,0x35));
        COLOR_BLOQUE.put("15. Total / Neto",          rgb(0x1F,0x49,0x7D));
        COLOR_BLOQUE.put("16. Validación 50%",        rgb(0xBF,0x90,0x00));
        COLOR_BLOQUE.put("17. Validación EsSalud",    rgb(0x00,0x80,0xB0));
        COLOR_BLOQUE.put("18. Comparativo anterior",  rgb(0x70,0x30,0xA0));
        COLOR_BLOQUE.put("19. Observaciones / Audit.",rgb(0x59,0x59,0x59));
    }

    private static final List<Col> COLS = construirColumnas();

    public byte[] escribir(String periodo, List<PlanillaConsolidadaRowDto> filas) {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Estilos e = new Estilos(wb);
            Sheet sheet = wb.createSheet("Planilla CAS " + periodo);

            escribirTitulo(sheet, periodo, e);
            escribirGruposDeBloque(sheet, e);
            escribirCabeceras(sheet, e);
            sheet.createFreezePane(4, 3);
            sheet.setAutoFilter(new CellRangeAddress(2, 2, 0, COLS.size() - 1));

            int rowIdx = 3;
            for (PlanillaConsolidadaRowDto fila : filas) {
                escribirFila(sheet, rowIdx++, fila, e);
            }
            escribirTotales(sheet, rowIdx, filas, e);
            ajustarAnchos(sheet);

            escribirHojaParametros(wb, periodo, filas.size(), e);
            escribirHojaLeyenda(wb, e);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Error generando Planilla CAS Consolidada " + periodo, ex);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Filas de cabecera
    // ═════════════════════════════════════════════════════════════════════════

    private void escribirTitulo(Sheet sheet, String periodo, Estilos e) {
        Row r = sheet.createRow(0);
        r.setHeightInPoints(26);
        Cell c = r.createCell(0);
        c.setCellValue("INDECI — PLANILLA CAS CONSOLIDADA — Período " + periodo);
        c.setCellStyle(e.titulo);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, COLS.size() - 1));
    }

    private void escribirGruposDeBloque(Sheet sheet, Estilos e) {
        Row r = sheet.createRow(1);
        r.setHeightInPoints(20);
        String bloqueActual = null;
        int inicioBloque = 0;
        for (int i = 0; i < COLS.size(); i++) {
            String b = COLS.get(i).bloque();
            if (bloqueActual == null) { bloqueActual = b; inicioBloque = 0; }
            if (!b.equals(bloqueActual)) {
                pintarGrupo(sheet, r, inicioBloque, i - 1, bloqueActual, e);
                bloqueActual = b; inicioBloque = i;
            }
        }
        pintarGrupo(sheet, r, inicioBloque, COLS.size() - 1, bloqueActual, e);
    }

    private void pintarGrupo(Sheet sheet, Row r, int desde, int hasta, String bloque, Estilos e) {
        Cell c = r.createCell(desde);
        c.setCellValue(bloque);
        c.setCellStyle(e.grupoPorBloque(bloque));
        if (hasta > desde) {
            sheet.addMergedRegion(new CellRangeAddress(1, 1, desde, hasta));
        }
    }

    private void escribirCabeceras(Sheet sheet, Estilos e) {
        Row r = sheet.createRow(2);
        r.setHeightInPoints(30);
        for (int i = 0; i < COLS.size(); i++) {
            Cell c = r.createCell(i);
            c.setCellValue(COLS.get(i).titulo());
            c.setCellStyle(e.cabeceraPorBloque(COLS.get(i).bloque()));
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Datos
    // ═════════════════════════════════════════════════════════════════════════

    private void escribirFila(Sheet sheet, int rowIdx, PlanillaConsolidadaRowDto d, Estilos e) {
        Row r = sheet.createRow(rowIdx);
        for (int i = 0; i < COLS.size(); i++) {
            Col col = COLS.get(i);
            Cell cell = r.createCell(i);
            Object v = col.ext().apply(d);
            if (v == null) { cell.setBlank(); cell.setCellStyle(e.dato); continue; }
            switch (col.tipo()) {
                case MONEDA -> { cell.setCellValue(((BigDecimal) v).doubleValue()); cell.setCellStyle(e.moneda); }
                case NUMERO -> { cell.setCellValue(((Number) v).doubleValue()); cell.setCellStyle(e.numero); }
                case FECHA -> { cell.setCellValue(((LocalDate) v).format(F_FECHA)); cell.setCellStyle(e.dato); }
                case FECHAHORA -> { cell.setCellValue(((LocalDateTime) v).format(F_FECHAHORA)); cell.setCellStyle(e.dato); }
                case CODIGO -> { cell.setCellValue(v.toString()); cell.setCellStyle(e.codigo); }
                default -> { cell.setCellValue(v.toString()); cell.setCellStyle(e.dato); }
            }
        }
    }

    private void escribirTotales(Sheet sheet, int rowIdx, List<PlanillaConsolidadaRowDto> filas, Estilos e) {
        if (filas.isEmpty()) return;
        Row r = sheet.createRow(rowIdx);
        r.setHeightInPoints(18);
        Cell etiqueta = r.createCell(0);
        etiqueta.setCellValue("TOTALES");
        etiqueta.setCellStyle(e.totalLabel);
        for (int i = 1; i < COLS.size(); i++) {
            Col col = COLS.get(i);
            Cell cell = r.createCell(i);
            if (col.totalizar()) {
                double suma = 0d;
                for (PlanillaConsolidadaRowDto d : filas) {
                    Object v = col.ext().apply(d);
                    if (v instanceof BigDecimal bd) suma += bd.doubleValue();
                }
                cell.setCellValue(suma);
                cell.setCellStyle(e.totalMoneda);
            } else {
                cell.setBlank();
                cell.setCellStyle(e.totalLabel);
            }
        }
    }

    private void ajustarAnchos(Sheet sheet) {
        for (int i = 0; i < COLS.size(); i++) {
            sheet.setColumnWidth(i, anchoPorTipo(COLS.get(i).tipo()));
        }
    }

    private static int anchoPorTipo(Tipo t) {
        return switch (t) {
            case TEXTO -> 28 * 256;
            case CODIGO -> 16 * 256;
            case MONEDA -> 14 * 256;
            case NUMERO -> 10 * 256;
            case FECHA -> 12 * 256;
            case FECHAHORA -> 17 * 256;
        };
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Hojas auxiliares
    // ═════════════════════════════════════════════════════════════════════════

    private void escribirHojaParametros(XSSFWorkbook wb, String periodo, int nroFilas, Estilos e) {
        Sheet s = wb.createSheet("Parámetros");
        s.setColumnWidth(0, 32 * 256);
        s.setColumnWidth(1, 32 * 256);
        int i = 0;
        filaParam(s, i++, "Reporte", "Planilla CAS Consolidada", e);
        filaParam(s, i++, "Período", periodo, e);
        filaParam(s, i++, "Fecha/hora de exportación",
                LocalDateTime.now().format(F_FECHAHORA), e);
        filaParam(s, i++, "Total de registros", String.valueOf(nroFilas), e);
        filaParam(s, i++, "Nota",
                "Las columnas sin fuente en el sistema se exportan vacías (ver hoja Leyenda).", e);
    }

    private void filaParam(Sheet s, int idx, String k, String v, Estilos e) {
        Row r = s.createRow(idx);
        Cell ck = r.createCell(0); ck.setCellValue(k); ck.setCellStyle(e.paramKey);
        Cell cv = r.createCell(1); cv.setCellValue(v); cv.setCellStyle(e.dato);
    }

    private void escribirHojaLeyenda(XSSFWorkbook wb, Estilos e) {
        Sheet s = wb.createSheet("Leyenda");
        s.setColumnWidth(0, 70 * 256);
        String[] notas = {
            "ESTADOS DE COLUMNA (auditoría de la exportación):",
            "• CON_DATO: la columna tiene valor desde el sistema.",
            "• Vacía: el sistema aún no captura/calcula ese dato (no es error).",
            "",
            "Columnas que hoy salen vacías por no tener fuente en el sistema:",
            "  - Estado LSGR, Afiliado sindicato, Condición AIRHSP (decisión RR.HH.).",
            "  - Bloque presupuestal: categoría, producto, actividad, finalidad,",
            "    clasificador de gasto, secuencia funcional, unidad orgánica.",
            "  - Plaza / CAP (decisión RR.HH.).",
            "  - Asistencia: permisos personales y feriados compensables.",
            "  - Observaciones de cuenta, pensión, contrato, trabajador, RR.HH.",
            "  - Comparativo contra período anterior.",
            "  - DS proporcionales (cálculo pendiente).",
            "",
            "Cargo y Dependencia requieren el módulo de Puesto laboral (no incluido en P0).",
        };
        for (int i = 0; i < notas.length; i++) {
            Row r = s.createRow(i);
            Cell c = r.createCell(0);
            c.setCellValue(notas[i]);
            c.setCellStyle(i == 0 ? e.paramKey : e.dato);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Estilos
    // ═════════════════════════════════════════════════════════════════════════

    private static final class Estilos {
        final XSSFCellStyle titulo, dato, codigo, moneda, numero,
                paramKey, totalLabel, totalMoneda;
        private final XSSFWorkbook wb;
        private final Font bold, norm, white;
        private final Map<String, XSSFCellStyle> gruposCache = new java.util.HashMap<>();
        private final Map<String, XSSFCellStyle> cabecerasCache = new java.util.HashMap<>();

        Estilos(XSSFWorkbook wb) {
            this.wb = wb;
            bold = wb.createFont(); bold.setBold(true); bold.setFontHeightInPoints((short) 10);
            norm = wb.createFont(); norm.setFontHeightInPoints((short) 10);
            white = wb.createFont(); white.setBold(true); white.setColor(org.apache.poi.ss.usermodel.IndexedColors.WHITE.getIndex());
            white.setFontHeightInPoints((short) 10);

            titulo = wb.createCellStyle();
            Font tf = wb.createFont(); tf.setBold(true); tf.setFontHeightInPoints((short) 14);
            tf.setColor(org.apache.poi.ss.usermodel.IndexedColors.WHITE.getIndex());
            titulo.setFont(tf);
            titulo.setFillForegroundColor(new XSSFColor(rgb(0x1F,0x49,0x7D), null));
            titulo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            titulo.setAlignment(HorizontalAlignment.CENTER);
            titulo.setVerticalAlignment(VerticalAlignment.CENTER);

            short fmt = wb.createDataFormat().getFormat("#,##0.00");
            dato = base(norm, HorizontalAlignment.LEFT, (short) 0);
            codigo = base(norm, HorizontalAlignment.LEFT, (short) 0); // texto puro (DNI/CCI)
            moneda = base(norm, HorizontalAlignment.RIGHT, fmt);
            numero = base(norm, HorizontalAlignment.RIGHT, (short) 0);
            paramKey = base(bold, HorizontalAlignment.LEFT, (short) 0);

            totalLabel = wb.createCellStyle();
            totalLabel.cloneStyleFrom(base(bold, HorizontalAlignment.LEFT, (short) 0));
            totalLabel.setFillForegroundColor(new XSSFColor(rgb(0xD9,0xD9,0xD9), null));
            totalLabel.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            totalMoneda = wb.createCellStyle();
            totalMoneda.cloneStyleFrom(base(bold, HorizontalAlignment.RIGHT, fmt));
            totalMoneda.setFillForegroundColor(new XSSFColor(rgb(0xD9,0xD9,0xD9), null));
            totalMoneda.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }

        private XSSFCellStyle base(Font f, HorizontalAlignment al, short fmt) {
            XSSFCellStyle cs = wb.createCellStyle();
            cs.setFont(f);
            cs.setAlignment(al);
            cs.setVerticalAlignment(VerticalAlignment.CENTER);
            if (fmt > 0) cs.setDataFormat(fmt);
            borde(cs);
            return cs;
        }

        XSSFCellStyle grupoPorBloque(String bloque) {
            return gruposCache.computeIfAbsent(bloque, b -> {
                XSSFCellStyle cs = wb.createCellStyle();
                cs.setFont(white);
                cs.setFillForegroundColor(new XSSFColor(COLOR_BLOQUE.getOrDefault(b, rgb(0x59,0x59,0x59)), null));
                cs.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                cs.setAlignment(HorizontalAlignment.CENTER);
                cs.setVerticalAlignment(VerticalAlignment.CENTER);
                borde(cs);
                return cs;
            });
        }

        XSSFCellStyle cabeceraPorBloque(String bloque) {
            return cabecerasCache.computeIfAbsent(bloque, b -> {
                XSSFCellStyle cs = wb.createCellStyle();
                cs.setFont(bold);
                byte[] base = COLOR_BLOQUE.getOrDefault(b, rgb(0x59,0x59,0x59));
                cs.setFillForegroundColor(new XSSFColor(aclarar(base), null));
                cs.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                cs.setAlignment(HorizontalAlignment.CENTER);
                cs.setVerticalAlignment(VerticalAlignment.CENTER);
                cs.setWrapText(true);
                borde(cs);
                return cs;
            });
        }

        private static void borde(XSSFCellStyle cs) {
            cs.setBorderBottom(BorderStyle.THIN);
            cs.setBorderTop(BorderStyle.THIN);
            cs.setBorderLeft(BorderStyle.THIN);
            cs.setBorderRight(BorderStyle.THIN);
        }
    }

    private static byte[] rgb(int r, int g, int b) {
        return new byte[]{(byte) r, (byte) g, (byte) b};
    }

    /** Aclara un color de bloque para la fila de cabecera (mezcla con blanco). */
    private static byte[] aclarar(byte[] c) {
        int r = c[0] & 0xFF, g = c[1] & 0xFF, b = c[2] & 0xFF;
        r = r + (255 - r) * 60 / 100;
        g = g + (255 - g) * 60 / 100;
        b = b + (255 - b) * 60 / 100;
        return new byte[]{(byte) r, (byte) g, (byte) b};
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Definición declarativa de columnas (orden = orden del XLSX)
    // ═════════════════════════════════════════════════════════════════════════

    private static List<Col> construirColumnas() {
        List<Col> c = new ArrayList<>();
        final String B1 = "1. Datos personales";
        c.add(new Col(B1, "N°", Tipo.NUMERO, false, PlanillaConsolidadaRowDto::getNro));
        c.add(new Col(B1, "DNI", Tipo.CODIGO, false, PlanillaConsolidadaRowDto::getDni));
        c.add(new Col(B1, "RUC", Tipo.CODIGO, false, PlanillaConsolidadaRowDto::getRuc));
        c.add(new Col(B1, "Apellidos y nombres", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getApellidosNombres));
        c.add(new Col(B1, "Fecha nacimiento", Tipo.FECHA, false, PlanillaConsolidadaRowDto::getFechaNacimiento));
        c.add(new Col(B1, "Edad", Tipo.NUMERO, false, PlanillaConsolidadaRowDto::getEdad));
        c.add(new Col(B1, "Sexo", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getSexo));
        c.add(new Col(B1, "Estado civil", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getEstadoCivil));
        c.add(new Col(B1, "Persona", Tipo.NUMERO, false, PlanillaConsolidadaRowDto::getPersonaId));
        c.add(new Col(B1, "Celular", Tipo.CODIGO, false, PlanillaConsolidadaRowDto::getCelular));
        c.add(new Col(B1, "Correo", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getCorreo));
        c.add(new Col(B1, "Dirección", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getDireccion));
        c.add(new Col(B1, "Distrito", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getDistrito));
        c.add(new Col(B1, "Provincia", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getProvincia));
        c.add(new Col(B1, "Departamento", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getDepartamento));

        final String B2 = "2. Datos laborales";
        c.add(new Col(B2, "Estado laboral", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getEstadoLaboral));
        c.add(new Col(B2, "Estado LSGR", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getEstadoLsgr));
        c.add(new Col(B2, "Afiliado sindicato", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getAfiliadoSindicato));
        c.add(new Col(B2, "Código SISPER", Tipo.CODIGO, false, PlanillaConsolidadaRowDto::getCodigoSisper));
        c.add(new Col(B2, "Registro AIRHSP", Tipo.CODIGO, false, PlanillaConsolidadaRowDto::getRegistroAirhsp));
        c.add(new Col(B2, "Monto AIRHSP", Tipo.MONEDA, false, PlanillaConsolidadaRowDto::getMontoAirhsp));
        c.add(new Col(B2, "Condición AIRHSP", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getCondicionAirhsp));
        c.add(new Col(B2, "Cargo", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getCargo));
        c.add(new Col(B2, "Dependencia", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getDependencia));
        c.add(new Col(B2, "Abrev. dependencia", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getAbreviaturaDependencia));
        c.add(new Col(B2, "Condición laboral", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getCondicionLaboral));
        c.add(new Col(B2, "Cond. laboral reportes", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getCondicionLaboralReportes));
        c.add(new Col(B2, "Fecha ingreso", Tipo.FECHA, false, PlanillaConsolidadaRowDto::getFechaIngreso));
        c.add(new Col(B2, "Fecha término", Tipo.FECHA, false, PlanillaConsolidadaRowDto::getFechaTermino));

        final String B3 = "3. Banco / Abono";
        c.add(new Col(B3, "Banco", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getBanco));
        c.add(new Col(B3, "Número de cuenta", Tipo.CODIGO, false, PlanillaConsolidadaRowDto::getNumeroCuenta));
        c.add(new Col(B3, "CCI", Tipo.CODIGO, false, PlanillaConsolidadaRowDto::getCci));
        c.add(new Col(B3, "N° cuenta tesorería", Tipo.CODIGO, false, PlanillaConsolidadaRowDto::getNumeroCuentaTesoreria));
        c.add(new Col(B3, "Estado cuenta", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getEstadoCuentaBancaria));
        c.add(new Col(B3, "Cuenta principal", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getCuentaPrincipal));
        c.add(new Col(B3, "Fecha registro cuenta", Tipo.FECHA, false, PlanillaConsolidadaRowDto::getFechaRegistroCuenta));
        c.add(new Col(B3, "Observación cuenta", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getObservacionCuenta));

        final String B4 = "4. Régimen pensionario";
        c.add(new Col(B4, "Régimen pensionario", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getRegimenPensionario));
        c.add(new Col(B4, "AFP", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getAfp));
        c.add(new Col(B4, "ONP", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getOnp));
        c.add(new Col(B4, "Tipo comisión", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getTipoComision));
        c.add(new Col(B4, "Comisión %", Tipo.MONEDA, false, PlanillaConsolidadaRowDto::getComisionPct));
        c.add(new Col(B4, "CUSPP", Tipo.CODIGO, false, PlanillaConsolidadaRowDto::getCuspp));
        c.add(new Col(B4, "Pensionista/Retiro 95.5%", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getPensionistaSinRegimenRetiro));
        c.add(new Col(B4, "Estado config. pensión", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getEstadoConfigPension));
        c.add(new Col(B4, "Fecha inicio régimen", Tipo.FECHA, false, PlanillaConsolidadaRowDto::getFechaInicioRegPension));
        c.add(new Col(B4, "Observación pensión", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getObservacionPension));

        final String B5 = "5. Suspensión 4ta";
        c.add(new Col(B5, "Fecha suspensión", Tipo.FECHA, false, PlanillaConsolidadaRowDto::getFechaSuspension));
        c.add(new Col(B5, "N° operación", Tipo.CODIGO, false, PlanillaConsolidadaRowDto::getNroOperacion));
        c.add(new Col(B5, "Suspensión (1/0)", Tipo.NUMERO, false, PlanillaConsolidadaRowDto::getSuspensionFlag));
        c.add(new Col(B5, "Suspensión renta 4ta", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getSuspensionRenta4ta));
        c.add(new Col(B5, "Fecha emisión", Tipo.FECHA, false, PlanillaConsolidadaRowDto::getFechaEmisionConstancia));
        c.add(new Col(B5, "Inicio vigencia", Tipo.FECHA, false, PlanillaConsolidadaRowDto::getFechaInicioVigencia));
        c.add(new Col(B5, "Fin vigencia", Tipo.FECHA, false, PlanillaConsolidadaRowDto::getFechaFinVigencia));
        c.add(new Col(B5, "Estado suspensión", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getEstadoSuspension));
        c.add(new Col(B5, "Observación suspensión", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getObservacionSuspension));

        final String B6 = "6. Presupuesto / AIRHSP";
        c.add(new Col(B6, "Meta 2026", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getMeta2026));
        c.add(new Col(B6, "Centro de costo", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getCentroCosto2026));
        c.add(new Col(B6, "Categoría presupuestal", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getCategoriaPresupuestal));
        c.add(new Col(B6, "Producto", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getProducto));
        c.add(new Col(B6, "Actividad", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getActividad));
        c.add(new Col(B6, "Finalidad", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getFinalidad));
        c.add(new Col(B6, "Fuente financiamiento", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getFuenteFinanciamiento));
        c.add(new Col(B6, "Clasificador de gasto", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getClasificadorGasto));
        c.add(new Col(B6, "Secuencia funcional", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getSecuenciaFuncional));
        c.add(new Col(B6, "Unidad orgánica presup.", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getUnidadOrganicaPresupuestal));

        final String B7 = "7. Contrato / Plaza";
        c.add(new Col(B7, "Número contrato", Tipo.CODIGO, false, PlanillaConsolidadaRowDto::getNumeroContrato));
        c.add(new Col(B7, "Plaza", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getPlaza));
        c.add(new Col(B7, "Condición laboral", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getCondicionLaboralContrato));
        c.add(new Col(B7, "Fecha ingreso", Tipo.FECHA, false, PlanillaConsolidadaRowDto::getFechaIngresoContrato));
        c.add(new Col(B7, "Fecha término", Tipo.FECHA, false, PlanillaConsolidadaRowDto::getFechaTerminoContrato));
        c.add(new Col(B7, "Monto contrato", Tipo.MONEDA, true, PlanillaConsolidadaRowDto::getMontoContrato));
        c.add(new Col(B7, "Estado contrato", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getEstadoContrato));
        c.add(new Col(B7, "Tipo contrato", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getTipoContrato));
        c.add(new Col(B7, "Observación contrato", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getObservacionContrato));

        final String B8 = "8. Remuneración / DS";
        c.add(new Col(B8, "Monto contrato", Tipo.MONEDA, true, PlanillaConsolidadaRowDto::getMontoContratoRem));
        c.add(new Col(B8, "DS 311-2022", Tipo.MONEDA, true, PlanillaConsolidadaRowDto::getDs311Mensual));
        c.add(new Col(B8, "DS 313-2023", Tipo.MONEDA, true, PlanillaConsolidadaRowDto::getDs313Mensual));
        c.add(new Col(B8, "DS 265-2024", Tipo.MONEDA, true, PlanillaConsolidadaRowDto::getDs265Mensual));
        c.add(new Col(B8, "DS 279-2024", Tipo.MONEDA, true, PlanillaConsolidadaRowDto::getDs279Mensual));
        c.add(new Col(B8, "DS 327-2025", Tipo.MONEDA, true, PlanillaConsolidadaRowDto::getDs327Mensual));
        c.add(new Col(B8, "Remuneración mensual", Tipo.MONEDA, true, PlanillaConsolidadaRowDto::getRemuneracionMensual));
        c.add(new Col(B8, "Días laborados", Tipo.NUMERO, false, PlanillaConsolidadaRowDto::getDiasLaborados));
        c.add(new Col(B8, "Rem. proporcional", Tipo.MONEDA, true, PlanillaConsolidadaRowDto::getRemProporcionalContrato));
        c.add(new Col(B8, "DS 311 proporcional", Tipo.MONEDA, true, PlanillaConsolidadaRowDto::getDs311Proporcional));
        c.add(new Col(B8, "DS 313 proporcional", Tipo.MONEDA, true, PlanillaConsolidadaRowDto::getDs313Proporcional));
        c.add(new Col(B8, "DS 265 proporcional", Tipo.MONEDA, true, PlanillaConsolidadaRowDto::getDs265Proporcional));
        c.add(new Col(B8, "DS 279 proporcional", Tipo.MONEDA, true, PlanillaConsolidadaRowDto::getDs279Proporcional));
        c.add(new Col(B8, "DS 327 proporcional", Tipo.MONEDA, true, PlanillaConsolidadaRowDto::getDs327Proporcional));
        c.add(new Col(B8, "Pago diferencial", Tipo.MONEDA, true, PlanillaConsolidadaRowDto::getPagoDiferencial));
        c.add(new Col(B8, "Días reintegro", Tipo.NUMERO, false, PlanillaConsolidadaRowDto::getDiasReintegro));
        c.add(new Col(B8, "S/ reintegro", Tipo.MONEDA, true, PlanillaConsolidadaRowDto::getMontoReintegro));
        c.add(new Col(B8, "Total remuneración mensual", Tipo.MONEDA, true, PlanillaConsolidadaRowDto::getTotalRemuneracionMensual));

        final String B9 = "9. Asistencia";
        c.add(new Col(B9, "Días no laborados", Tipo.NUMERO, false, PlanillaConsolidadaRowDto::getDiasNoLaborados));
        c.add(new Col(B9, "Reducción tardanzas", Tipo.MONEDA, true, PlanillaConsolidadaRowDto::getReduccionTardanzas));
        c.add(new Col(B9, "Permisos personales", Tipo.MONEDA, true, PlanillaConsolidadaRowDto::getPermisosPersonales));
        c.add(new Col(B9, "Feriados compensables", Tipo.MONEDA, true, PlanillaConsolidadaRowDto::getFeriadosCompensables));
        c.add(new Col(B9, "Inasistencia/faltas", Tipo.MONEDA, true, PlanillaConsolidadaRowDto::getInasistenciaFaltas));
        c.add(new Col(B9, "Descuento total asistencia", Tipo.MONEDA, true, PlanillaConsolidadaRowDto::getDescuentoTotalAsistencia));
        c.add(new Col(B9, "Minutos tardanza", Tipo.NUMERO, false, PlanillaConsolidadaRowDto::getMinutosTardanza));
        c.add(new Col(B9, "Minutos permisos", Tipo.NUMERO, false, PlanillaConsolidadaRowDto::getMinutosPermisosPersonales));
        c.add(new Col(B9, "Minutos feriados", Tipo.NUMERO, false, PlanillaConsolidadaRowDto::getMinutosFeriadosCompensables));
        c.add(new Col(B9, "Días desc. inasistencia", Tipo.NUMERO, false, PlanillaConsolidadaRowDto::getDiasDescuentoInasistencia));
        c.add(new Col(B9, "Observación asistencia", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getObservacionAsistencia));

        final String B10 = "10. Base imponible";
        c.add(new Col(B10, "Base imponible ing.-egr.", Tipo.MONEDA, false, PlanillaConsolidadaRowDto::getBaseImponibleIngresosEgresos));
        c.add(new Col(B10, "Aguinaldo jul/dic", Tipo.MONEDA, true, PlanillaConsolidadaRowDto::getAguinaldoJulioDiciembre));
        c.add(new Col(B10, "Remuneración bruta", Tipo.MONEDA, false, PlanillaConsolidadaRowDto::getRemuneracionBruta));
        c.add(new Col(B10, "Base AFP/ONP", Tipo.MONEDA, false, PlanillaConsolidadaRowDto::getBaseAfpOnp));
        c.add(new Col(B10, "Base EsSalud", Tipo.MONEDA, false, PlanillaConsolidadaRowDto::getBaseEssalud));
        c.add(new Col(B10, "Base IR4ta", Tipo.MONEDA, false, PlanillaConsolidadaRowDto::getBaseIr4ta));
        c.add(new Col(B10, "Base neta valid. 50%", Tipo.MONEDA, false, PlanillaConsolidadaRowDto::getBaseNetaValidacion50));

        final String B11 = "11. EsSalud / EPS";
        c.add(new Col(B11, "Aporte EsSalud", Tipo.MONEDA, true, PlanillaConsolidadaRowDto::getAporteEssalud));
        c.add(new Col(B11, "EsSalud sin EPS 9%", Tipo.MONEDA, true, PlanillaConsolidadaRowDto::getEssaludSinEps9pct));
        c.add(new Col(B11, "EsSalud 6.75%", Tipo.MONEDA, true, PlanillaConsolidadaRowDto::getEssalud675pct));
        c.add(new Col(B11, "EPS 2.25%", Tipo.MONEDA, true, PlanillaConsolidadaRowDto::getEps225pct));
        c.add(new Col(B11, "Copago EPS", Tipo.MONEDA, true, PlanillaConsolidadaRowDto::getCopagoEpsTrabajador));
        c.add(new Col(B11, "Estado EsSalud", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getEstadoEssalud));
        c.add(new Col(B11, "Tope 45% UIT", Tipo.MONEDA, false, PlanillaConsolidadaRowDto::getTope45pctUit));
        c.add(new Col(B11, "EsSalud mínimo", Tipo.MONEDA, false, PlanillaConsolidadaRowDto::getEssaludMinimo));
        c.add(new Col(B11, "EsSalud máximo", Tipo.MONEDA, false, PlanillaConsolidadaRowDto::getEssaludMaximo));

        final String B12 = "12. IR 4ta categoría";
        c.add(new Col(B12, "Indicador suspensión", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getIndicadorSuspension));
        c.add(new Col(B12, "IR4ta remuneración", Tipo.MONEDA, true, PlanillaConsolidadaRowDto::getIr4taRemuneracion));
        c.add(new Col(B12, "IR4ta aguinaldo", Tipo.MONEDA, true, PlanillaConsolidadaRowDto::getIr4taAguinaldo));
        c.add(new Col(B12, "IR4ta total", Tipo.MONEDA, true, PlanillaConsolidadaRowDto::getIr4taTotal));
        c.add(new Col(B12, "Código interno IR4TA", Tipo.CODIGO, false, PlanillaConsolidadaRowDto::getCodigoInternoIr4ta));
        c.add(new Col(B12, "Cód. tributo SUNAT", Tipo.CODIGO, false, PlanillaConsolidadaRowDto::getCodigoTributoSunat));
        c.add(new Col(B12, "Base inafecta mensual", Tipo.MONEDA, false, PlanillaConsolidadaRowDto::getBaseInafectaMensualIr4ta));
        c.add(new Col(B12, "Tasa IR4ta", Tipo.MONEDA, false, PlanillaConsolidadaRowDto::getTasaIr4ta));
        c.add(new Col(B12, "N° operación suspensión", Tipo.CODIGO, false, PlanillaConsolidadaRowDto::getNroOperacionSuspension));
        c.add(new Col(B12, "Estado suspensión", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getEstadoSuspension4ta));
        c.add(new Col(B12, "Observación IR4ta", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getObservacionIr4ta));

        final String B13 = "13. AFP / ONP";
        c.add(new Col(B13, "Aporte AFP 10%", Tipo.MONEDA, true, PlanillaConsolidadaRowDto::getAporteAfp10pct));
        c.add(new Col(B13, "Comisión %", Tipo.MONEDA, false, PlanillaConsolidadaRowDto::getComisionAfpPct));
        c.add(new Col(B13, "Comisión S/", Tipo.MONEDA, true, PlanillaConsolidadaRowDto::getComisionAfpMonto));
        c.add(new Col(B13, "Prima 1.37%", Tipo.MONEDA, true, PlanillaConsolidadaRowDto::getPrima137pct));
        c.add(new Col(B13, "Total AFP", Tipo.MONEDA, true, PlanillaConsolidadaRowDto::getTotalAfp));
        c.add(new Col(B13, "ONP SNP", Tipo.MONEDA, true, PlanillaConsolidadaRowDto::getOnpSnp));
        c.add(new Col(B13, "Régimen pensionario", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getRegimenPensionarioBlq13));
        c.add(new Col(B13, "AFP", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getAfpBlq13));
        c.add(new Col(B13, "Tipo comisión", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getTipoComisionBlq13));
        c.add(new Col(B13, "CUSPP", Tipo.CODIGO, false, PlanillaConsolidadaRowDto::getCusppBlq13));
        c.add(new Col(B13, "Tope seguro AFP", Tipo.MONEDA, false, PlanillaConsolidadaRowDto::getTopeSeguroAfp));
        c.add(new Col(B13, "Base AFP", Tipo.MONEDA, false, PlanillaConsolidadaRowDto::getBaseAfp));
        c.add(new Col(B13, "Base ONP", Tipo.MONEDA, false, PlanillaConsolidadaRowDto::getBaseOnp));

        final String B14 = "14. Descuentos terceros";
        c.add(new Col(B14, "Descuento judicial", Tipo.MONEDA, true, PlanillaConsolidadaRowDto::getDescuentoJudicial));
        c.add(new Col(B14, "Coop. La Rehabilitadora", Tipo.MONEDA, true, PlanillaConsolidadaRowDto::getCooperativaRehabilitadora));
        c.add(new Col(B14, "Otros descuentos", Tipo.MONEDA, true, PlanillaConsolidadaRowDto::getOtrosDescuentos));
        c.add(new Col(B14, "FESALUD S.A.", Tipo.MONEDA, true, PlanillaConsolidadaRowDto::getFesalud));
        c.add(new Col(B14, "Inter/Sura", Tipo.MONEDA, true, PlanillaConsolidadaRowDto::getInterSura));
        c.add(new Col(B14, "+Vida EsSalud", Tipo.MONEDA, true, PlanillaConsolidadaRowDto::getMasVidaEssalud));
        c.add(new Col(B14, "Coop. San Miguel", Tipo.MONEDA, true, PlanillaConsolidadaRowDto::getCoopSanMiguel));
        c.add(new Col(B14, "Coop. Serfinco", Tipo.MONEDA, true, PlanillaConsolidadaRowDto::getCoopSerfinco));
        c.add(new Col(B14, "Bancom", Tipo.MONEDA, true, PlanillaConsolidadaRowDto::getBancom));
        c.add(new Col(B14, "Rímac Seguros", Tipo.MONEDA, true, PlanillaConsolidadaRowDto::getRimacSeguros));
        c.add(new Col(B14, "Copago EPS", Tipo.MONEDA, true, PlanillaConsolidadaRowDto::getCopagoEpsDescuento));
        c.add(new Col(B14, "Cuota sindical", Tipo.MONEDA, true, PlanillaConsolidadaRowDto::getCuotaSindical));
        c.add(new Col(B14, "Otros seguros", Tipo.MONEDA, true, PlanillaConsolidadaRowDto::getOtrosSeguros));
        c.add(new Col(B14, "Otros préstamos", Tipo.MONEDA, true, PlanillaConsolidadaRowDto::getOtrosPrestamos));
        c.add(new Col(B14, "Total desc. terceros", Tipo.MONEDA, true, PlanillaConsolidadaRowDto::getTotalDescuentosTerceros));

        final String B15 = "15. Total / Neto";
        c.add(new Col(B15, "Total descuento", Tipo.MONEDA, true, PlanillaConsolidadaRowDto::getTotalDescuento));
        c.add(new Col(B15, "Neto a pagar", Tipo.MONEDA, true, PlanillaConsolidadaRowDto::getNetoPagar));
        c.add(new Col(B15, "Remuneración bruta", Tipo.MONEDA, true, PlanillaConsolidadaRowDto::getRemuneracionBrutaBlq15));
        c.add(new Col(B15, "Total aportes trabajador", Tipo.MONEDA, true, PlanillaConsolidadaRowDto::getTotalAportesTrabajador));
        c.add(new Col(B15, "Total desc. legales", Tipo.MONEDA, true, PlanillaConsolidadaRowDto::getTotalDescuentosLegales));
        c.add(new Col(B15, "Total desc. judiciales", Tipo.MONEDA, true, PlanillaConsolidadaRowDto::getTotalDescuentosJudiciales));
        c.add(new Col(B15, "Total desc. terceros", Tipo.MONEDA, true, PlanillaConsolidadaRowDto::getTotalDescuentosTercerosBlq15));

        final String B16 = "16. Validación 50%";
        c.add(new Col(B16, "50% remuneración", Tipo.MONEDA, false, PlanillaConsolidadaRowDto::getCincuentaPctRemuneracion));
        c.add(new Col(B16, "Estado 50%", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getEstado50pct));
        c.add(new Col(B16, "Base validación 50%", Tipo.MONEDA, false, PlanillaConsolidadaRowDto::getBaseValidacion50));
        c.add(new Col(B16, "Desc. legales considerados", Tipo.MONEDA, false, PlanillaConsolidadaRowDto::getDescuentosLegalesConsiderados));
        c.add(new Col(B16, "Desc. judicial considerado", Tipo.MONEDA, false, PlanillaConsolidadaRowDto::getDescuentoJudicialConsiderado));
        c.add(new Col(B16, "Margen disp. terceros", Tipo.MONEDA, false, PlanillaConsolidadaRowDto::getMargenDisponibleTerceros));
        c.add(new Col(B16, "Observación validación 50%", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getObservacionValidacion50));

        final String B17 = "17. Validación EsSalud";
        c.add(new Col(B17, "Estado EsSalud", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getEstadoEssaludBlq17));
        c.add(new Col(B17, "Base EsSalud", Tipo.MONEDA, false, PlanillaConsolidadaRowDto::getBaseEssaludBlq17));
        c.add(new Col(B17, "EsSalud calculado", Tipo.MONEDA, false, PlanillaConsolidadaRowDto::getEssaludCalculado));
        c.add(new Col(B17, "EsSalud mínimo", Tipo.MONEDA, false, PlanillaConsolidadaRowDto::getEssaludMinimoBlq17));
        c.add(new Col(B17, "EsSalud máximo", Tipo.MONEDA, false, PlanillaConsolidadaRowDto::getEssaludMaximoBlq17));
        c.add(new Col(B17, "Tope 45% UIT", Tipo.MONEDA, false, PlanillaConsolidadaRowDto::getTope45pctUitBlq17));
        c.add(new Col(B17, "Observación EsSalud", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getObservacionEssalud));

        final String B18 = "18. Comparativo anterior";
        c.add(new Col(B18, "Planilla anterior", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getPlanillaAnterior));
        c.add(new Col(B18, "Neto período anterior", Tipo.MONEDA, false, PlanillaConsolidadaRowDto::getNetoPeriodoAnterior));
        c.add(new Col(B18, "Neto actual", Tipo.MONEDA, false, PlanillaConsolidadaRowDto::getNetoActual));
        c.add(new Col(B18, "Diferencia", Tipo.MONEDA, false, PlanillaConsolidadaRowDto::getDiferencia));
        c.add(new Col(B18, "Motivo diferencia", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getMotivoDiferencia));
        c.add(new Col(B18, "Tipo diferencia", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getTipoDiferencia));
        c.add(new Col(B18, "Requiere revisión", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getRequiereRevision));
        c.add(new Col(B18, "Observación RR.HH.", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getObservacionRrhhComp));

        final String B19 = "19. Observaciones / Audit.";
        c.add(new Col(B19, "Observación planilla", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getObservacionPlanilla));
        c.add(new Col(B19, "Observación trabajador", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getObservacionTrabajador));
        c.add(new Col(B19, "Observación RR.HH.", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getObservacionRrhh));
        c.add(new Col(B19, "Usuario generación", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getUsuarioGeneracion));
        c.add(new Col(B19, "Fecha generación", Tipo.FECHAHORA, false, PlanillaConsolidadaRowDto::getFechaGeneracion));
        c.add(new Col(B19, "Usuario exportación", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getUsuarioExportacion));
        c.add(new Col(B19, "Fecha exportación", Tipo.FECHAHORA, false, PlanillaConsolidadaRowDto::getFechaExportacion));
        c.add(new Col(B19, "Estado período", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getEstadoPeriodo));
        c.add(new Col(B19, "Estado registro", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getEstadoRegistro));
        c.add(new Col(B19, "Motivo exclusión", Tipo.TEXTO, false, PlanillaConsolidadaRowDto::getMotivoExclusion));

        return List.copyOf(c);
    }
}
