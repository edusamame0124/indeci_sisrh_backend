package com.indeci.rrhh.service;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.indeci.audit.annotation.Auditable;
import com.indeci.audit.context.AuditoriaContext;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.ImportacionVacacionesResultDto;
import com.indeci.rrhh.entity.Empleado;
import com.indeci.rrhh.entity.Persona;
import com.indeci.rrhh.entity.VacacionSaldo;
import com.indeci.rrhh.repository.EmpleadoRepository;
import com.indeci.rrhh.repository.PersonaRepository;
import com.indeci.rrhh.repository.VacacionSaldoRepository;

import lombok.RequiredArgsConstructor;

/**
 * Importador one-shot de la línea base vacacional — SPEC_VACACIONES F8 / D7.
 * Lee la hoja DATOS de la MATRIZ_VACACIONES (Excel del especialista) y congela los
 * saldos en INDECI_VACACION_SALDO con ORIGEN='MIGRACION_INICIAL_2026'. A partir del
 * corte, el motor toma la operación (el Excel queda depreciado).
 *
 * <p>Solo importa el <b>snapshot numérico</b> (DNI, días que corresponden, días gozados,
 * fecha de corte); el historial textual de goces (col X) NO se migra.
 */
@Service
@RequiredArgsConstructor
public class ImportadorVacacionesService {

    public static final String ORIGEN_MIGRACION = "MIGRACION_INICIAL_2026";

    // Índices de columna de la hoja DATOS (0-based).
    private static final int COL_DNI = 1;          // B — N° DE DNI
    private static final int COL_NOMBRE = 2;       // C — APELLIDOS Y NOMBRES
    private static final int COL_FECHA_CORTE = 10; // K — FECHA DE CORTE
    private static final int COL_GANADOS = 15;     // P — N° días que corresponden
    private static final int COL_GOZADOS = 16;     // Q — N° días gozados

    private final PersonaRepository personaRepository;
    private final EmpleadoRepository empleadoRepository;
    private final VacacionSaldoRepository vacacionSaldoRepository;
    private final AuditoriaContext auditoriaContext;

    @Auditable(accion = "IMPORTAR_BASELINE_VACACIONES")
    @Transactional
    public ImportacionVacacionesResultDto importar(byte[] contenidoXlsx) {
        int total = 0;
        int importados = 0;
        final List<String> noEncontrados = new ArrayList<>();
        final List<String> errores = new ArrayList<>();
        LocalDate corteGlobal = null;

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(contenidoXlsx))) {
            final Sheet sheet = sheetDatos(wb);
            if (sheet == null) {
                throw new NegocioException("El archivo no contiene datos (hoja DATOS).");
            }
            final DataFormatter fmt = new DataFormatter();

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                final Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                final String dni = leerDni(row.getCell(COL_DNI), fmt);
                if (dni == null) {
                    continue; // fila sin DNI → fin/relleno
                }
                total++;
                final String nombre = fmt.formatCellValue(row.getCell(COL_NOMBRE)).trim();
                final Double ganados = leerNumero(row.getCell(COL_GANADOS));
                final Double gozados = leerNumero(row.getCell(COL_GOZADOS));
                final LocalDate corte = leerFecha(row.getCell(COL_FECHA_CORTE));
                if (corte != null) {
                    corteGlobal = corte;
                }
                if (ganados == null && gozados == null) {
                    errores.add("Fila " + (i + 1) + " (" + nombre + "): sin días ganados/gozados.");
                    continue;
                }

                final Optional<Persona> persona = personaRepository.findByDniNormalizado(dni);
                if (persona.isEmpty()) {
                    noEncontrados.add(dni + " (" + nombre + ")");
                    continue;
                }
                final Optional<Empleado> empleado = empleadoRepository.findByPersonaId(persona.get().getId());
                if (empleado.isEmpty()) {
                    noEncontrados.add(dni + " (" + nombre + ") — persona sin empleado");
                    continue;
                }

                final int anio = (corte != null ? corte : LocalDate.now()).getYear();
                upsert(empleado.get().getId(), anio, ganados, gozados, corte, nombre);
                importados++;
            }
        } catch (NegocioException e) {
            throw e;
        } catch (Exception e) {
            throw new NegocioException("No se pudo leer el Excel: " + e.getMessage());
        }

        auditoriaContext.setDetalle(
                "Import baseline vacaciones: " + importados + "/" + total + " importados; "
                        + noEncontrados.size() + " no encontrados; " + errores.size() + " errores.");

        return new ImportacionVacacionesResultDto(
                total, importados, noEncontrados, errores,
                corteGlobal != null ? corteGlobal.toString() : null,
                ORIGEN_MIGRACION);
    }

    private void upsert(Long empleadoId, int anio, Double ganados, Double gozados,
            LocalDate corte, String nombre) {
        final VacacionSaldo e = vacacionSaldoRepository
                .findByEmpleadoIdAndAnioAndActivo(empleadoId, anio, 1)
                .orElseGet(() -> {
                    VacacionSaldo n = new VacacionSaldo();
                    n.setEmpleadoId(empleadoId);
                    n.setAnio(anio);
                    n.setActivo(1);
                    n.setCreatedAt(LocalDateTime.now());
                    return n;
                });
        e.setDiasGanados(ganados != null ? ganados : 0d);
        e.setDiasGozados(gozados != null ? gozados : 0d);
        e.setOrigen(ORIGEN_MIGRACION);
        e.setFechaCorte(corte);
        e.setObservacion("Línea base importada del Excel (" + nombre + ")");
        vacacionSaldoRepository.save(e);
    }

    private Sheet sheetDatos(Workbook wb) {
        final Sheet nombrada = wb.getSheet("DATOS");
        if (nombrada != null) {
            return nombrada;
        }
        return wb.getNumberOfSheets() > 0 ? wb.getSheetAt(0) : null;
    }

    /** DNI robusto: numérico o texto → dígitos → normalizado a 8 (LPAD) para findByDniNormalizado. */
    private String leerDni(Cell cell, DataFormatter fmt) {
        if (cell == null) {
            return null;
        }
        String raw;
        if (cell.getCellType() == CellType.NUMERIC
                || cell.getCellType() == CellType.FORMULA) {
            try {
                raw = String.valueOf((long) cell.getNumericCellValue());
            } catch (Exception ex) {
                raw = fmt.formatCellValue(cell);
            }
        } else {
            raw = fmt.formatCellValue(cell);
        }
        final String digits = raw.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return null;
        }
        return String.format("%08d", Long.parseLong(digits));
    }

    private Double leerNumero(Cell cell) {
        if (cell == null) {
            return null;
        }
        try {
            if (cell.getCellType() == CellType.NUMERIC
                    || cell.getCellType() == CellType.FORMULA) {
                return cell.getNumericCellValue();
            }
        } catch (Exception ignored) {
            // celda no numérica → null
        }
        return null;
    }

    private LocalDate leerFecha(Cell cell) {
        if (cell == null) {
            return null;
        }
        try {
            if (DateUtil.isCellDateFormatted(cell)) {
                return cell.getLocalDateTimeCellValue().toLocalDate();
            }
            if (cell.getCellType() == CellType.NUMERIC) {
                return DateUtil.getJavaDate(cell.getNumericCellValue())
                        .toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            }
        } catch (Exception ignored) {
            // formato inesperado → sin fecha
        }
        return null;
    }
}
