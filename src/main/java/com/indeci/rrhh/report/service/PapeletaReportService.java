package com.indeci.rrhh.report.service;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.entity.Empleado;
import com.indeci.rrhh.entity.EmpleadoPuesto;
import com.indeci.rrhh.entity.Oficina;
import com.indeci.rrhh.entity.Persona;
import com.indeci.rrhh.entity.SolicitudRrhh;
import com.indeci.rrhh.entity.TipoSolicitudRrhh;
import com.indeci.rrhh.report.dto.PapeletaReportDto;
import com.indeci.rrhh.repository.EmpleadoPuestoRepository;
import com.indeci.rrhh.repository.EmpleadoRepository;
import com.indeci.rrhh.repository.OficinaRepository;
import com.indeci.rrhh.repository.PersonaRepository;
import com.indeci.rrhh.repository.SolicitudRrhhRepository;
import com.indeci.rrhh.repository.TipoSolicitudRrhhRepository;

import lombok.RequiredArgsConstructor;

import net.sf.jasperreports.engine.*;

import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PapeletaReportService {
	
	
	private final SolicitudRrhhRepository
    solicitudRepository;

private final TipoSolicitudRrhhRepository
    tipoSolicitudRepository;

private final EmpleadoRepository
    empleadoRepository;

private final EmpleadoPuestoRepository
empleadoPuestoRepository;

private final OficinaRepository
oficinaRepository;

private final PersonaRepository
personaRepository;

public String generarPdf(
        Long solicitudId) {

    try {

        // ==========================================
        // OBTENER SOLICITUD
        // ==========================================

        SolicitudRrhh solicitud =
                solicitudRepository
                        .findById(solicitudId)
                        .orElseThrow(() ->
                                new NegocioException(
                                        "Solicitud no encontrada"));

        // ==========================================
        // OBTENER TIPO SOLICITUD
        // ==========================================

        TipoSolicitudRrhh tipo =
                tipoSolicitudRepository
                        .findById(
                                solicitud.getTipoSolicitudId())
                        .orElseThrow(() ->
                                new NegocioException(
                                        "Tipo solicitud no encontrado"));

        // ==========================================
        // OBTENER EMPLEADO
        // ==========================================

        Empleado empleado =
                empleadoRepository
                        .findById(
                                solicitud.getEmpleadoId())
                        .orElseThrow(() ->
                                new NegocioException(
                                        "Empleado no encontrado"));
        
        Persona persona =
        		personaRepository
                        .findById(
                        		empleado.getPersonaId())
                        .orElseThrow(() ->
                                new NegocioException(
                                        "Persona no encontrado"));

        // ==========================================
        // OBTENER PUESTO ACTUAL
        // ==========================================

        EmpleadoPuesto puesto =
                empleadoPuestoRepository
                        .findFirstByEmpleadoIdAndActivo(
                                empleado.getId(),
                                1)
                        .orElseThrow(() ->
                                new NegocioException(
                                        "Empleado sin puesto activo"));

        // ==========================================
        // OBTENER OFICINA
        // ==========================================

        Oficina oficina =
                oficinaRepository
                        .findById(
                                puesto.getOficinaId())
                        .orElse(null);

        // ==========================================
        // CARGAR LOGO
        // ==========================================

        InputStream logo =
                getClass()
                        .getResourceAsStream(
                                "/reportes/img/LogoIndeci.png");

        // ==========================================
        // CARGAR JASPER
        // ==========================================

        InputStream jasperStream =
                getClass()
                        .getResourceAsStream(
                                "/reportes/rrhh/papeleta.jrxml");

        JasperReport jasperReport =
                JasperCompileManager
                        .compileReport(
                                jasperStream);

        // ==========================================
        // PARAMETERS
        // ==========================================

        Map<String, Object> params =
                new HashMap<>();

        params.put(
                "LOGO",
                logo);

        params.put(
                "INSTITUCION",
                "INDECI");

        params.put(
                "P_NOMBRES",
                valor(
                		persona.getNombreCompleto()));

        params.put(
                "P_AREA",
                oficina != null
                        ? valor(
                                oficina.getNombre())
                        : "");

        params.put(
                "P_CARGO",
                valor(
                        puesto.getCargo()));

        params.put(
                "P_TIPO",
                valor(
                        tipo.getNombre()));

        params.put(
                "P_DIAS",
                valor(
                        solicitud.getCantidadDias()));

        params.put(
                "P_HORAS",
                valor(
                        solicitud.getCantidadHoras()));

        params.put(
                "P_DESDE",
                valor(
                        solicitud.getFechaInicio()));

        params.put(
                "P_HASTA",
                valor(
                        solicitud.getFechaFin()));

        params.put(
                "P_HORA_INICIO",
                valor(
                        solicitud.getHoraInicio()));

        params.put(
                "P_HORA_FIN",
                valor(
                        solicitud.getHoraFin()));

        params.put(
                "P_MOTIVO",
                valor(
                        solicitud.getMotivo()));

        params.put(
                "P_SHOW_TIME",
                tipo.getMostrarHoras() == 1);
        

        params.put(
                "P_FECHA_PAPELETA",
                obtenerFechaActual());
        


        // ==========================================
        // GENERAR REPORTE
        // ==========================================

        JasperPrint jasperPrint =
                JasperFillManager.fillReport(
                        jasperReport,
                        params,
                        new JREmptyDataSource());

        // ==========================================
        // CREAR CARPETA
        // ==========================================

        String carpeta =
                "/opt/indeci/rrhh/solicitudes/";

        Files.createDirectories(
                Paths.get(carpeta));

        // ==========================================
        // RUTA PDF
        // ==========================================

        String rutaPdf =
                carpeta
                        + "SOL_"
                        + solicitudId
                        + "_V0.pdf";

        // ==========================================
        // EXPORTAR PDF
        // ==========================================

        JasperExportManager
                .exportReportToPdfFile(
                        jasperPrint,
                        rutaPdf);

        return rutaPdf;

    } catch (Exception e) {

        throw new NegocioException(
                "Error generando PDF: "
                        + e.getMessage());
    }
}

private String valor(
        Object o) {

    return o != null
            ? o.toString()
            : "";
}


    
    private String obtenerFechaActual() {

        Locale locale = new Locale("es", "PE");

        DateTimeFormatter formatter =
                DateTimeFormatter.ofPattern(
                        "dd 'de' MMMM 'de' yyyy",
                        locale);

        return "Lima, "
                + LocalDate.now()
                        .format(formatter);
    }
    
    
}