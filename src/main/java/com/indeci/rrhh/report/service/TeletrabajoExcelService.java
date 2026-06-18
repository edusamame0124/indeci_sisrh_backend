package com.indeci.rrhh.report.service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import com.indeci.rrhh.dto.TeletrabajoReporteResponseDto;
import com.indeci.rrhh.repository.EmpleadoRepository;
import com.indeci.rrhh.dto.EmpleadoPuestoResponseDto;
import com.indeci.rrhh.dto.PersonaEmpleadoResponseDto;
import com.indeci.rrhh.dto.TeletrabajoReporteDetResponseDto;
import com.indeci.rrhh.service.EmpleadoPuestoService;
import com.indeci.rrhh.service.PersonaService;
import com.indeci.rrhh.service.TeletrabajoReporteService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TeletrabajoExcelService {

    private final TeletrabajoReporteService service;
    private final PersonaService personaService;

    private final EmpleadoPuestoService empleadoPuestoService;
    private final EmpleadoRepository empleadoRepository;

    public String generarExcel(
            Long reporteId) {

        try {

            TeletrabajoReporteResponseDto reporte =
                    service.obtener(reporteId);
            
            PersonaEmpleadoResponseDto persona =
                    personaService.obtenerPorId(
                            obtenerPersonaId(
                                    reporte.getEmpleadoId()));

            EmpleadoPuestoResponseDto puesto =
                    empleadoPuestoService
                            .listar(
                                    reporte.getEmpleadoId())
                            .stream()
                            .filter(p -> p.getActivo() == 1)
                            .findFirst()
                            .orElse(null);

            InputStream plantilla =
                    getClass()
                            .getResourceAsStream(
                                    "/reportes/rrhh/anexo10.xlsx");
            
            if (plantilla == null) {

                throw new RuntimeException(
                        "No se encontró la plantilla anexo10.xlsx");
            }

            Workbook workbook =
                    new XSSFWorkbook(
                            plantilla);

            Sheet sheet =
                    workbook.getSheetAt(0);
            
            sheet.getRow(3)
            .createCell(2)
            .setCellValue("PRUEBA OFICINA");

    sheet.getRow(4)
            .createCell(2)
            .setCellValue(persona.getNombreCompleto());

    sheet.getRow(4)
            .createCell(4)
            .setCellValue(persona.getDni());

    sheet.getRow(4)
            .createCell(9)
            .setCellValue(persona.getRegimenLaboral());

    sheet.getRow(5)
            .createCell(2)
            .setCellValue(
                    puesto != null
                            ? puesto.getCargo()
                            : "");

    sheet.getRow(5)
            .createCell(5)
            .setCellValue(
                    reporte.getModalidad());

    sheet.getRow(5)
            .createCell(9)
            .setCellValue(
                    reporte.getFechaReporte() != null
                            ? reporte.getFechaReporte().toString()
                            : "");
    
            sheet.getRow(3)
                    .getCell(4)
                    .setCellValue(
                            obtenerMes(
                                    reporte.getMes()));

            sheet.getRow(3)
                    .getCell(9)
                    .setCellValue(
                            reporte.getAnio());

            /*
             * DETALLE
             */

            int fila = 10;

            for(TeletrabajoReporteDetResponseDto det :
                    reporte.getDetalles()) {

                Row row =
                        sheet.getRow(fila);

                if(row == null) {
                    row =
                            sheet.createRow(
                                    fila);
                }

                row.getCell(0)
                        .setCellValue(
                                det.getNroOrden());

                row.getCell(1)
                        .setCellValue(
                                det.getActividadProgramada());

                row.getCell(2)
                        .setCellValue(
                                det.getActividadEjecutada());

                row.getCell(3)
                        .setCellValue(
                                det.getMedioVerificacion());

                if(det.getFechaInicio() != null) {

                    row.getCell(4)
                            .setCellValue(
                                    det.getFechaInicio()
                                            .toString());
                }

                if(det.getFechaFin() != null) {

                    row.getCell(5)
                            .setCellValue(
                                    det.getFechaFin()
                                            .toString());
                }

                row.getCell(8)
                .setCellValue(
                        det.getPorcentajeAvance()
                                / 100.0);

                row.getCell(9)
                        .setCellValue(
                                det.getIncidenciaObservacion());
                
                if(det.getEstadoCumplimiento() != null) {

                    switch(det.getEstadoCumplimiento().toUpperCase()) {

                        case "CUMPLIDO":

                            sheet.getRow(fila)
                                    .getCell(7)
                                    .setCellValue("X");

                            break;

                        case "PARCIAL":

                            sheet.getRow(fila + 1)
                                    .getCell(7)
                                    .setCellValue("X");

                            break;

                        case "NO CUMPLIDO":

                            sheet.getRow(fila + 2)
                                    .getCell(7)
                                    .setCellValue("X");

                            break;
                    }
                }
                
                /*
                 * CONFORMIDAD
                 */

                if(det.getConformidad() != null) {

                    switch(det.getConformidad().toUpperCase()) {

                        case "CONFORME":

                            sheet.getRow(fila)
                                    .getCell(11)
                                    .setCellValue("X");

                            break;

                        case "OBSERVADO":

                            sheet.getRow(fila + 1)
                                    .getCell(11)
                                    .setCellValue("X");

                            break;
                    }
                }

                fila=fila+3;
            }

            String ruta =
                    System.getProperty(
                            "java.io.tmpdir")
                    + "/teletrabajo_"
                    + reporteId
                    + ".xlsx";

            workbook.write(
                    Files.newOutputStream(
                            Paths.get(ruta)));

            workbook.close();

            return ruta;

        } catch(Exception e) {

            throw new RuntimeException(
                    "Error generando Excel",
                    e);
        }
    }

    private String obtenerMes(
            Integer mes) {

        return switch(mes) {

            case 1 -> "ENERO";
            case 2 -> "FEBRERO";
            case 3 -> "MARZO";
            case 4 -> "ABRIL";
            case 5 -> "MAYO";
            case 6 -> "JUNIO";
            case 7 -> "JULIO";
            case 8 -> "AGOSTO";
            case 9 -> "SETIEMBRE";
            case 10 -> "OCTUBRE";
            case 11 -> "NOVIEMBRE";
            case 12 -> "DICIEMBRE";

            default -> "";
        };
    }
    private Long obtenerPersonaId(
            Long empleadoId) {

        return empleadoRepository
                .findById(empleadoId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Empleado no encontrado"))
                .getPersonaId();
    }
}