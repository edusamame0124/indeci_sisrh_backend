package com.indeci.rrhh.service;

import com.indeci.audit.annotation.Auditable;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.Suspension4taImportCsvRow;
import com.indeci.rrhh.dto.Suspension4taImportReportDto;
import com.indeci.rrhh.dto.Suspension4taRequestDto;
import com.indeci.rrhh.entity.Empleado;
import com.indeci.rrhh.entity.Persona;
import com.indeci.rrhh.repository.EmpleadoRepository;
import com.indeci.rrhh.repository.PersonaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class Suspension4taImportService {

    private final PersonaRepository personaRepository;
    private final EmpleadoRepository empleadoRepository;
    private final Suspension4taService suspension4taService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Transactional
    @Auditable(accion = "IMPORTAR_MASIVO_SUSPENSION_4TA")
    public Suspension4taImportReportDto importarCsv(MultipartFile file) {
        if (file.isEmpty()) {
            throw new NegocioException("El archivo CSV está vacío.");
        }

        List<Suspension4taImportCsvRow> rows = parsearCsv(file);
        List<Suspension4taImportCsvRow> filasError = new ArrayList<>();
        int procesados = 0;

        for (Suspension4taImportCsvRow row : rows) {
            try {
                if (row.getNroDoc() == null || row.getNroDoc().trim().isEmpty()) {
                    throw new NegocioException("Documento de identidad vacío.");
                }

                // 1. Buscar Persona
                Persona persona = personaRepository.findByDniNormalizado(row.getNroDoc().trim())
                        .orElseThrow(() -> new NegocioException("No se encontró persona con DNI: " + row.getNroDoc()));

                // 2. Obtener Empleado Activo (Preferimos el activo para aplicarle la suspensión)
                List<Empleado> empleados = empleadoRepository.findAllByPersonaId(persona.getId());
                Empleado empleado = empleados.stream()
                        .filter(e -> "ACTIVO".equalsIgnoreCase(e.getEstado()))
                        .findFirst()
                        .orElseGet(() -> empleados.isEmpty() ? null : empleados.get(0));

                if (empleado == null) {
                    throw new NegocioException("La persona no tiene un registro de empleado válido.");
                }
                
                row.setEmpleadoId(empleado.getId());

                // 3. Mapear al Request DTO del servicio existente
                Suspension4taRequestDto dto = new Suspension4taRequestDto();
                dto.setEmpleadoId(empleado.getId());
                dto.setNroConstancia(row.getNroConstancia());
                dto.setFechaEmision(row.getFechaEmision());
                dto.setFechaVigIni(row.getFechaVigIni());
                dto.setFechaVigFin(row.getFechaVigFin());
                dto.setObservacion(row.getObservacion());

                // 4. Crear a través del servicio que ya valida solapamientos
                suspension4taService.crear(dto);
                procesados++;

            } catch (Exception ex) {
                row.setValido(false);
                row.setMensajeError(ex instanceof NegocioException ? ex.getMessage() : "Error inesperado al procesar la fila");
                filasError.add(row);
                log.warn("Fila {} con error: {}", row.getNumeroFila(), row.getMensajeError());
            }
        }

        Suspension4taImportReportDto report = new Suspension4taImportReportDto();
        report.setTotalFilas(rows.size());
        report.setTotalProcesados(procesados);
        report.setTotalErrores(filasError.size());
        report.setFilasError(filasError);

        return report;
    }

    private List<Suspension4taImportCsvRow> parsearCsv(MultipartFile file) {
        List<Suspension4taImportCsvRow> rows = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            
            // Quitar BOM si existe
            br.mark(1);
            if (br.read() != 0xFEFF) {
                br.reset();
            }

            String line;
            int lineNumber = 0;
            boolean isHeader = true;

            while ((line = br.readLine()) != null) {
                lineNumber++;
                if (line.trim().isEmpty()) continue;

                if (isHeader) {
                    isHeader = false;
                    continue;
                }

                String[] values = line.split(";|\\,"); // Soporta ; o ,
                if (values.length < 2) continue;

                Suspension4taImportCsvRow row = new Suspension4taImportCsvRow();
                row.setNumeroFila(lineNumber);
                
                try {
                    row.setTipoDoc(getValue(values, 0));
                    row.setNroDoc(getValue(values, 1));
                    row.setNroConstancia(getValue(values, 2));
                    row.setFechaEmision(parseDate(getValue(values, 3)));
                    row.setFechaVigIni(parseDate(getValue(values, 4)));
                    row.setFechaVigFin(parseDate(getValue(values, 5)));
                    row.setObservacion(getValue(values, 6));
                } catch (Exception ex) {
                    row.setValido(false);
                    row.setMensajeError("Error de formato en las columnas: " + ex.getMessage());
                }
                
                rows.add(row);
            }
        } catch (Exception ex) {
            throw new NegocioException("Error al leer el archivo CSV: " + ex.getMessage());
        }
        return rows;
    }

    private String getValue(String[] values, int index) {
        if (index >= values.length) return null;
        String val = values[index].trim();
        return val.isEmpty() ? null : val;
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return null;
        try {
            return LocalDate.parse(dateStr, DATE_FORMATTER);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Fecha inválida: " + dateStr + ". Use dd/MM/yyyy.");
        }
    }
}
