package com.indeci.rrhh.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.indeci.rrhh.entity.ConceptoPlanilla;
import com.indeci.rrhh.entity.Empleado;
import com.indeci.rrhh.entity.EmpleadoPlanilla;
import com.indeci.rrhh.entity.ExportArchivo;
import com.indeci.rrhh.entity.MovimientoPlanilla;
import com.indeci.rrhh.entity.MovimientoPlanillaDetalle;
import com.indeci.rrhh.entity.Persona;
import com.indeci.rrhh.entity.RegimenLaboral;
import com.indeci.rrhh.repository.ConceptoPlanillaRepository;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.EmpleadoRepository;
import com.indeci.rrhh.repository.ExportArchivoRepository;
import com.indeci.rrhh.repository.MovimientoPlanillaDetalleRepository;
import com.indeci.rrhh.repository.MovimientoPlanillaRepository;
import com.indeci.rrhh.repository.PersonaRepository;
import com.indeci.rrhh.repository.RegimenLaboralRepository;

import lombok.RequiredArgsConstructor;

/**
 * B1 — Genera la Planilla Única Consolidada en XLSX (Apache POI).
 *
 * <p>Cabecera doble: fila 1 = código MEF (para AIRHSP), fila 2 = nombre
 * del concepto en español (para revisión del analista).</p>
 *
 * <p>Colores por bloque:</p>
 * <ul>
 *   <li>INGRESO        → verde  (#C6EFCE / #EBFEEB)</li>
 *   <li>DESCUENTO      → salmón (#FFC7CE / #FFEEED)</li>
 *   <li>APORTE_EMPLEADOR → azul (#BDD7EE / #E8F1F8)</li>
 *   <li>Columnas fijas y totales → gris (#D9D9D9)</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class PlanillaXlsxService {

    private static final String TIPO_XLSX      = "XLSX_PLANILLA";
    private static final String TIPO_INGRESO   = "INGRESO";
    private static final String TIPO_DESCUENTO = "DESCUENTO";
    private static final String TIPO_APORTE    = "APORTE_EMPLEADOR";

    private final MovimientoPlanillaRepository        movimientoRepo;
    private final MovimientoPlanillaDetalleRepository detalleRepo;
    private final ConceptoPlanillaRepository          conceptoRepo;
    private final EmpleadoPlanillaRepository          planillaRepo;
    private final EmpleadoRepository                  empleadoRepo;
    private final PersonaRepository                   personaRepo;
    private final RegimenLaboralRepository            regimenRepo;
    private final ExportArchivoRepository             exportRepo;

    // ── Columnas fijas (inicio) ───────────────────────────────────────────────
    private static final String[] F_CODE  = {"N°","AIRHSP","DNI","NOMBRES Y APELLIDOS","RÉGIMEN"};
    private static final String[] F_LABEL = {"N°","Cód. AIRHSP","DNI","Nombres y Apellidos","Régimen laboral"};
    // Columnas fijas (final)
    private static final String[] T_CODE  = {"NETO","ESSALUD_EMP","CUC"};
    private static final String[] T_LABEL = {"Neto (MUC)","EsSalud empleador","CUC (Costo entidad)"};

    // ═════════════════════════════════════════════════════════════════════════
    // API pública
    // ═════════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public byte[] generarYRegistrar(String periodo) {
        byte[] bytes = generar(periodo);
        guardarHistorial(periodo, bytes);
        return bytes;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Generación
    // ═════════════════════════════════════════════════════════════════════════

    private byte[] generar(String periodo) {
        List<MovimientoPlanilla> movs = movimientoRepo.findByPeriodoAndActivo(periodo, 1);
        if (movs.isEmpty()) return vacio(periodo);

        // ── Carga en batch ───────────────────────────────────────────────────
        List<Long> movIds = movs.stream().map(MovimientoPlanilla::getId).toList();
        List<MovimientoPlanillaDetalle> todosDetalles = detalleRepo.findByMovimientoPlanillaIdIn(movIds);

        Map<Long, ConceptoPlanilla> conceptoPorId = conceptoRepo.findByActivo(1)
                .stream().collect(Collectors.toMap(ConceptoPlanilla::getId, c -> c));

        // Empleado → Persona (sin N+1)
        List<Long> empIds = movs.stream().map(MovimientoPlanilla::getEmpleadoId).distinct().toList();
        Map<Long, Empleado> empleadoPorId = empleadoRepo.findAllById(empIds)
                .stream().collect(Collectors.toMap(Empleado::getId, e -> e));
        List<Long> personaIds = empleadoPorId.values().stream()
                .map(Empleado::getPersonaId).filter(pid -> pid != null).distinct().toList();
        Map<Long, Persona> personaPorId = personaRepo.findAllById(personaIds)
                .stream().collect(Collectors.toMap(Persona::getId, p -> p));
        // personaPorEmpId: empleadoId → Persona
        Map<Long, Persona> personaPorEmpId = new HashMap<>();
        for (Empleado emp : empleadoPorId.values()) {
            if (emp.getPersonaId() != null) {
                Persona p = personaPorId.get(emp.getPersonaId());
                if (p != null) personaPorEmpId.put(emp.getId(), p);
            }
        }

        // EmpleadoPlanilla
        Map<Long, EmpleadoPlanilla> planillaPorEmp = planillaRepo.findByActivo(1)
                .stream().collect(Collectors.toMap(EmpleadoPlanilla::getEmpleadoId, p -> p, (a, b) -> a));

        // Regímenes
        Map<Long, String> regimenNombre = regimenRepo.findAll()
                .stream().collect(Collectors.toMap(RegimenLaboral::getId, RegimenLaboral::getCodigo));

        // Detalles por movimiento
        Map<Long, List<MovimientoPlanillaDetalle>> detallesPorMov = todosDetalles
                .stream().collect(Collectors.groupingBy(MovimientoPlanillaDetalle::getMovimientoPlanillaId));

        // ── Columnas dinámicas ───────────────────────────────────────────────
        LinkedHashMap<Long, ColDef> cols = descubrirColumnas(todosDetalles, conceptoPorId);

        // ── Workbook ─────────────────────────────────────────────────────────
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Planilla " + periodo);
            sheet.createFreezePane(5, 2); // fija columnas fijas y dos cabeceras
            Estilos e = new Estilos(wb);

            escribirHeaders(sheet, cols, e);
            int nextRow = escribirDatos(sheet, movs, detallesPorMov,
                    planillaPorEmp, personaPorEmpId, regimenNombre, cols, e);
            escribirTotales(sheet, nextRow, cols, e);
            ajustarAnchos(sheet, cols.size());

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Error generando XLSX planilla " + periodo, ex);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Descubrimiento de columnas dinámicas
    // ═════════════════════════════════════════════════════════════════════════

    private record ColDef(String mef, String tipo, String nombre) {}

    private LinkedHashMap<Long, ColDef> descubrirColumnas(
            List<MovimientoPlanillaDetalle> detalles,
            Map<Long, ConceptoPlanilla> conceptoPorId) {

        Map<Long, ColDef> vistos = new HashMap<>();
        for (MovimientoPlanillaDetalle d : detalles) {
            if (d.getMonto() == null || Math.abs(d.getMonto()) < 0.001) continue;
            Long cid = d.getConceptoPlanillaId();
            if (cid == null || vistos.containsKey(cid)) continue;
            ConceptoPlanilla c = conceptoPorId.get(cid);
            if (c == null) continue;
            String tipo = c.getTipoConcepto() != null ? c.getTipoConcepto() : "OTRO";
            String mef  = c.getCodigoMef()    != null ? c.getCodigoMef()    : cid.toString();
            vistos.put(cid, new ColDef(mef, tipo, c.getNombre() != null ? c.getNombre() : mef));
        }
        return vistos.entrySet().stream()
                .sorted(Comparator
                        .comparingInt((Map.Entry<Long, ColDef> en) -> tipoOrden(en.getValue().tipo()))
                        .thenComparing(en -> en.getValue().mef()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));
    }

    private static int tipoOrden(String tipo) {
        return switch (tipo) {
            case TIPO_INGRESO   -> 0;
            case TIPO_DESCUENTO -> 1;
            case TIPO_APORTE    -> 2;
            default             -> 3;
        };
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Escritura de filas
    // ═════════════════════════════════════════════════════════════════════════

    private void escribirHeaders(Sheet sheet, LinkedHashMap<Long, ColDef> cols, Estilos e) {
        Row r1 = sheet.createRow(0);
        Row r2 = sheet.createRow(1);
        r1.setHeightInPoints(22);
        r2.setHeightInPoints(18);
        int c = 0;
        for (int i = 0; i < F_CODE.length; i++, c++) {
            cel(r1, c, F_CODE[i],  e.hFijo);
            cel(r2, c, F_LABEL[i], e.hFijo);
        }
        for (ColDef cd : cols.values()) {
            CellStyle h = hdrPorTipo(cd.tipo(), e);
            cel(r1, c, cd.mef(),    h);
            cel(r2, c, cd.nombre(), h);
            c++;
        }
        for (int i = 0; i < T_CODE.length; i++, c++) {
            cel(r1, c, T_CODE[i],  e.hTotal);
            cel(r2, c, T_LABEL[i], e.hTotal);
        }
    }

    private int escribirDatos(
            Sheet sheet,
            List<MovimientoPlanilla> movs,
            Map<Long, List<MovimientoPlanillaDetalle>> detallesPorMov,
            Map<Long, EmpleadoPlanilla> planillaPorEmp,
            Map<Long, Persona> personaPorEmpId,
            Map<Long, String> regimenNombre,
            LinkedHashMap<Long, ColDef> cols,
            Estilos e) {

        List<Long> colKeys = new ArrayList<>(cols.keySet());
        int rowNum = 2;
        int nro = 1;

        for (MovimientoPlanilla mov : movs) {
            Row row = sheet.createRow(rowNum++);
            Persona pers = personaPorEmpId.get(mov.getEmpleadoId());
            EmpleadoPlanilla pl = planillaPorEmp.get(mov.getEmpleadoId());

            int c = 0;
            // Fijos inicio
            celNum(row, c++, nro++, e.dFijo);
            cel(row, c++, pl != null && pl.getCodigoAirhsp() != null ? pl.getCodigoAirhsp() : "", e.dFijo);
            cel(row, c++, pers != null && pers.getDni() != null ? pers.getDni() : "", e.dFijo);
            cel(row, c++, pers != null ? pers.getNombreCompleto() : "Emp." + mov.getEmpleadoId(), e.dNombre);
            String regCod = "";
            if (pl != null && pl.getRegimenLaboralId() != null) {
                regCod = regimenNombre.getOrDefault(pl.getRegimenLaboralId(), "");
            }
            cel(row, c++, regCod, e.dFijo);

            // Monto por concepto
            List<MovimientoPlanillaDetalle> dets = detallesPorMov.getOrDefault(mov.getId(), List.of());
            Map<Long, Double> montoPorCid = dets.stream()
                    .filter(d -> d.getMonto() != null && d.getConceptoPlanillaId() != null)
                    .collect(Collectors.toMap(MovimientoPlanillaDetalle::getConceptoPlanillaId,
                            MovimientoPlanillaDetalle::getMonto, Double::sum));

            for (Long cid : colKeys) {
                ColDef cd = cols.get(cid);
                celMonto(row, c++, montoPorCid.getOrDefault(cid, 0.0), datoPorTipo(cd.tipo(), e));
            }

            // Fijos final
            double essalud = essaludEmpleador(dets);
            double neto    = orZ(mov.getNetoPagar());
            celMonto(row, c++, neto,           e.dTotal);
            celMonto(row, c++, essalud,         e.dTotal);
            celMonto(row, c,   neto + essalud,  e.dTotal);
        }
        return rowNum;
    }

    private void escribirTotales(Sheet sheet, int nextRow,
                                 LinkedHashMap<Long, ColDef> cols, Estilos e) {
        Row row = sheet.createRow(nextRow);
        row.setHeightInPoints(18);
        int total = F_CODE.length + cols.size() + T_CODE.length;
        for (int c = 0; c < total; c++) {
            if (c == 0) { cel(row, c, "TOTALES", e.hTotal); continue; }
            if (c < F_CODE.length) { cel(row, c, "", e.hTotal); continue; }
            Cell cell = row.createCell(c);
            String col = colLetra(c);
            cell.setCellFormula("SUM(" + col + "3:" + col + nextRow + ")");
            cell.setCellStyle(e.dTotal);
        }
    }

    private void ajustarAnchos(Sheet sheet, int numDyn) {
        sheet.setColumnWidth(0, 1400);
        sheet.setColumnWidth(1, 4000);
        sheet.setColumnWidth(2, 3200);
        sheet.setColumnWidth(3, 10000);
        sheet.setColumnWidth(4, 3800);
        for (int i = 0; i < numDyn; i++) sheet.setColumnWidth(5 + i, 3800);
        int fin = 5 + numDyn;
        sheet.setColumnWidth(fin,     3600);
        sheet.setColumnWidth(fin + 1, 3800);
        sheet.setColumnWidth(fin + 2, 3600);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Helpers de dominio
    // ═════════════════════════════════════════════════════════════════════════

    private static double orZ(Double v) { return v != null ? v : 0.0; }

    private static double essaludEmpleador(List<MovimientoPlanillaDetalle> dets) {
        // El motor graba el aporte empleador como concepto MEF 06001/06002;
        // los campos extra del detalle (essalud675 + copagoEps) cubren el split con EPS.
        return dets.stream()
                .mapToDouble(d -> orZ(d.getEssalud675()) + orZ(d.getCopagoEps()))
                .sum();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Historial de exports
    // ═════════════════════════════════════════════════════════════════════════

    private void guardarHistorial(String periodo, byte[] bytes) {
        try {
            ExportArchivo ex = new ExportArchivo();
            ex.setPeriodo(periodo);
            ex.setTipoArchivo(TIPO_XLSX);
            ex.setNombreArchivo("planilla_" + periodo + "_" + System.currentTimeMillis() + ".xlsx");
            ex.setHashSha256(sha256(bytes));
            ex.setNroLineas(bytes.length);
            ex.setFechaGenerado(LocalDateTime.now());
            exportRepo.save(ex);
        } catch (Exception ignored) {
            // El historial es informativo — no bloquea la entrega del archivo.
        }
    }

    private static String sha256(byte[] data) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) { return "no-hash"; }
    }

    private static byte[] vacio(String periodo) {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            wb.createSheet("Planilla " + periodo)
              .createRow(0).createCell(0)
              .setCellValue("Sin movimientos para el período " + periodo);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) { throw new IllegalStateException(e); }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Utilidades de celda
    // ═════════════════════════════════════════════════════════════════════════

    private static void cel(Row row, int c, String v, CellStyle cs) {
        Cell cell = row.createCell(c);
        cell.setCellValue(v != null ? v : "");
        cell.setCellStyle(cs);
    }

    private static void celNum(Row row, int c, int v, CellStyle cs) {
        Cell cell = row.createCell(c);
        cell.setCellValue(v);
        cell.setCellStyle(cs);
    }

    private static void celMonto(Row row, int c, double v, CellStyle cs) {
        Cell cell = row.createCell(c);
        cell.setCellValue(v);
        cell.setCellStyle(cs);
    }

    private static String colLetra(int idx) {
        StringBuilder sb = new StringBuilder();
        for (int i = idx; i >= 0; i = i / 26 - 1) {
            sb.insert(0, (char) ('A' + i % 26));
        }
        return sb.toString();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Estilos POI
    // ═════════════════════════════════════════════════════════════════════════

    private CellStyle hdrPorTipo(String t, Estilos e) {
        return switch (t) { case TIPO_INGRESO -> e.hIngreso;
            case TIPO_DESCUENTO -> e.hDesc; case TIPO_APORTE -> e.hAporte; default -> e.hFijo; };
    }

    private CellStyle datoPorTipo(String t, Estilos e) {
        return switch (t) { case TIPO_INGRESO -> e.dIngreso;
            case TIPO_DESCUENTO -> e.dDesc; case TIPO_APORTE -> e.dAporte; default -> e.dFijo; };
    }

    private static class Estilos {
        final XSSFCellStyle hFijo, hIngreso, hDesc, hAporte, hTotal;
        final XSSFCellStyle dFijo, dNombre, dIngreso, dDesc, dAporte, dTotal;

        Estilos(XSSFWorkbook wb) {
            org.apache.poi.ss.usermodel.Font bold = wb.createFont();
            bold.setBold(true); bold.setFontHeightInPoints((short)10);
            org.apache.poi.ss.usermodel.Font norm = wb.createFont();
            norm.setFontHeightInPoints((short)10);

            hFijo    = hdr(wb, bold, new byte[]{(byte)0xD9,(byte)0xD9,(byte)0xD9});
            hIngreso = hdr(wb, bold, new byte[]{(byte)0xC6,(byte)0xEF,(byte)0xCE});
            hDesc    = hdr(wb, bold, new byte[]{(byte)0xFF,(byte)0xC7,(byte)0xCE});
            hAporte  = hdr(wb, bold, new byte[]{(byte)0xBD,(byte)0xD7,(byte)0xEE});
            hTotal   = hdr(wb, bold, new byte[]{(byte)0xD9,(byte)0xD9,(byte)0xD9});

            short fmt = wb.createDataFormat().getFormat("#,##0.00");
            dFijo    = dato(wb, norm, null,       fmt, false);
            dNombre  = dato(wb, norm, null,       (short)0, false);
            dIngreso = dato(wb, norm, new byte[]{(byte)0xEB,(byte)0xF7,(byte)0xEB}, fmt, true);
            dDesc    = dato(wb, norm, new byte[]{(byte)0xFF,(byte)0xEE,(byte)0xED}, fmt, true);
            dAporte  = dato(wb, norm, new byte[]{(byte)0xE8,(byte)0xF1,(byte)0xF8}, fmt, true);
            dTotal   = dato(wb, bold, new byte[]{(byte)0xD9,(byte)0xD9,(byte)0xD9}, fmt, true);
        }

        private static XSSFCellStyle hdr(XSSFWorkbook wb, org.apache.poi.ss.usermodel.Font f, byte[] rgb) {
            XSSFCellStyle cs = wb.createCellStyle();
            cs.setFont(f);
            cs.setFillForegroundColor(new XSSFColor(rgb, null));
            cs.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            cs.setAlignment(HorizontalAlignment.CENTER);
            cs.setWrapText(true);
            borde(cs);
            return cs;
        }

        private static XSSFCellStyle dato(XSSFWorkbook wb, org.apache.poi.ss.usermodel.Font f,
                                          byte[] rgb, short fmt, boolean right) {
            XSSFCellStyle cs = wb.createCellStyle();
            cs.setFont(f);
            if (rgb != null) {
                cs.setFillForegroundColor(new XSSFColor(rgb, null));
                cs.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            }
            cs.setAlignment(right ? HorizontalAlignment.RIGHT : HorizontalAlignment.LEFT);
            if (fmt > 0) cs.setDataFormat(fmt);
            borde(cs);
            return cs;
        }

        private static void borde(XSSFCellStyle cs) {
            cs.setBorderBottom(BorderStyle.THIN);
            cs.setBorderLeft(BorderStyle.THIN);
            cs.setBorderRight(BorderStyle.THIN);
            cs.setBorderTop(BorderStyle.THIN);
            cs.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
            cs.setLeftBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
            cs.setRightBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
            cs.setTopBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        }
    }
}
