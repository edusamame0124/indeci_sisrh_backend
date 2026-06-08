package com.indeci.rrhh.report.service;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.entity.Empleado;
import com.indeci.rrhh.entity.EmpleadoPlanilla;
import com.indeci.rrhh.entity.EmpleadoPuesto;
import com.indeci.rrhh.entity.Oficina;
import com.indeci.rrhh.entity.Persona;
import com.indeci.rrhh.entity.SolicitudRrhh;
import com.indeci.rrhh.entity.TipoDescansoDoc;
import com.indeci.rrhh.entity.TipoLicencia;
import com.indeci.rrhh.entity.TipoSolicitudRrhh;
import com.indeci.rrhh.report.dto.PapeletaReportDto;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.EmpleadoPuestoRepository;
import com.indeci.rrhh.repository.EmpleadoRepository;
import com.indeci.rrhh.repository.OficinaRepository;
import com.indeci.rrhh.repository.PeriodoPlanillaRepository;
import com.indeci.rrhh.repository.PersonaRepository;
import com.indeci.rrhh.repository.SolicitudRrhhRepository;
import com.indeci.rrhh.repository.TipoDescansoDocRepository;
import com.indeci.rrhh.repository.TipoLicenciaRepository;
import com.indeci.rrhh.repository.TipoSolicitudRrhhRepository;

import lombok.RequiredArgsConstructor;

import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.util.JRLoader;

import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
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

private final EmpleadoPlanillaRepository empleadoPlanillaRepository;

private final TipoDescansoDocRepository tipoDescansoDocRepository;

private final TipoLicenciaRepository tipoLicenciaRepository;





public String generarPdf(
        Long solicitudId) {
	 System.out.println("GENERANDO PAPELETA ID = " + solicitudId);
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
        
        System.out.println("Solicitud ID = " + solicitud.getId());
        System.out.println("TipoSolicitudId = " + solicitud.getTipoSolicitudId());
        System.out.println("EmpleadoId = " + solicitud.getEmpleadoId());


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
        
        System.out.println("TIPO OK: " + tipo.getId());

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
        System.out.println("EMPLEADO OK: " + empleado.getId());
        
      
        
        EmpleadoPlanilla empleadoPlanilla =
        		empleadoPlanillaRepository
                        .findFirstByEmpleadoIdAndActivo(
                        		empleado.getId(),1)
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
        System.out.println("PERSONA ID: " + empleado.getPersonaId());
        System.out.println("PERSONA OK: " + persona.getId());

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
        
        System.out.println("PUESTO OK: " + puesto.getId());
        System.out.println("OFICINA ID: " + puesto.getOficinaId());

        // ==========================================
        // OBTENER OFICINA
        // ==========================================

   

        // ==========================================
        // CARGAR LOGO
        // ==========================================

        InputStream logo =
                getClass()
                        .getResourceAsStream(
                                "/reportes/img/logoPeru.png");

        // ==========================================
        // CARGAR JASPER
        // ==========================================

        String nombreReporte = "formato_1.jasper";

        if("008".equals(tipo.getCodigo())
                || "009".equals(tipo.getCodigo())) {

            nombreReporte = "formato_2.jasper";
        }
        
        if("010".equals(tipo.getCodigo())) {

            nombreReporte = "formato_4.jasper";
        }
        if("011".equals(tipo.getCodigo())) {

            nombreReporte = "formato_3.jasper";
        }
        InputStream jasperStream =
                getClass()
                        .getResourceAsStream(
                                "/reportes/rrhh/" + nombreReporte);

        
	if (jasperStream == null) {
	    throw new RuntimeException(
	            "No existe /reportes/rrhh/" + nombreReporte);
	}

        if (logo == null) {
            throw new RuntimeException(
                    "No existe /reportes/img/logoPeru.png");
        }

        System.out.println("ANTES DE CARGAR JASPER");
        
        JasperReport jasperReport =
                (JasperReport)
                JRLoader.loadObject(
                        jasperStream);

        System.out.println("DESPUES DE CARGAR JASPER");

        // ==========================================
        // PARAMETERS
        // ==========================================
        
     
        Map<String, Object> params =
                new HashMap<>();

        params.put(
                "P_LOGO_PERU",
                logo);

        params.put(
                "INSTITUCION",
                "INDECI");

        params.put(
                "P_NOMBRE_TRABAJADOR",
                valor(
                		persona.getNombreCompleto()));
        
        params.put(
                "P_DEPENDENCIA",
                puesto.getDependencia() != null
                        ? puesto.getDependencia().getNombre()
                        : "");
       //INICIO REGIMEN LABORAL 
        
        params.put(
                "P_REGIMEN",
                valor(
                		empleadoPlanilla.getRegimenLaboral().getCodigo()));
        
        
        if ("010".equals(tipo.getCodigo())) {

            cargarParametrosDescansoMedico(
                    params,
                    solicitud);
        }
        if ("011".equals(tipo.getCodigo())) {

            cargarParametrosLicencia(
                    params,
                    solicitud);
        }
        
        
        System.out.println("P_REGIMEN = " + params.get("P_REGIMEN"));

        if(params.get("P_REGIMEN") != null){
            System.out.println(
                "TIPO P_REGIMEN = " +
                params.get("P_REGIMEN").getClass().getName());
        }
        //FINDE REGIMEN LABORAL
   
        ///tipo de permiso
        if("001".equals(tipo.getCodigo())){
        	
        	  params.put(
                      "P_PERMISO_ASUNTOS",
                
                      		Boolean.TRUE);
        }
        if("002".equals(tipo.getCodigo())){
        	
      	  params.put(
                    "P_CITA_MEDICA",
                 
                    		Boolean.TRUE);
      }
        
        if("003".equals(tipo.getCodigo())){
        	
        	  params.put(
                      "P_CITACION",
                    
                    		  Boolean.TRUE);
        }
        if("004".equals(tipo.getCodigo())){
        	
      	  params.put(
                    "P_OMISION_REGISTRO",
                 
                    		Boolean.TRUE);
      }
        
        if("005".equals(tipo.getCodigo())){
        	
        	  params.put(
                      "P_TARDANZA",
                  
                    		  Boolean.TRUE);
        }
        
        if("006".equals(tipo.getCodigo())){
        	
      	  params.put(
                    "P_COMISION_SERVICIO",
                    Boolean.TRUE);
      	  
      
      	  params.put(
                  "P_LUGAR",
                  valor(
                  		solicitud.getLugarComision()));
      }
        
        if("007".equals(tipo.getCodigo())){
        	
        	  params.put(
                      "P_OTROS",
                     
                    		  Boolean.TRUE);
        	  
        	  params.put(
                      "P_DESCRIPCION_OTROS",
                      valor(
                    		  solicitud.getObservacion()));
        	  
        }
        
        if("008".equals(tipo.getCodigo())) {

            params.put(
                    "P_TIPO_SOLICITUD",
                    "PRIMERA");
        }

        if("009".equals(tipo.getCodigo())) {

            params.put(
                    "P_TIPO_SOLICITUD",
                    "MODIFICACION");
        }
        
        if("008".equals(tipo.getCodigo())
                || "009".equals(tipo.getCodigo())) {

            params.put(
                    "P_FECHA_NACIMIENTO",
                    valor(
                            formatearFecha(solicitud.getFechaNacimientoHijo())));

            params.put(
                    "P_FECHA_FIN_POSTNATAL",
                    valor(
                            formatearFecha(solicitud.getFechaFinPostnatal())));

            params.put(
                    "P_MINUTOS_INGRESO",
                    valor(
                            solicitud.getMinutosIngreso()));

            params.put(
                    "P_MINUTOS_SALIDA",
                    valor(
                            solicitud.getMinutosSalida()));

            params.put(
                    "P_HORA_DESDE",
                    valor(
                            solicitud.getHoraInicio()));

            params.put(
                    "P_HORA_HASTA",
                    valor(
                            solicitud.getHoraFin()));
        }
        
        if(solicitud.getFechaNacimientoHijo() != null) {

            LocalDate primerAnio =
                    solicitud.getFechaNacimientoHijo()
                             .plusYears(1);

            params.put(
                    "P_FECHA_PRIMER_ANIO",
                    primerAnio.format(
                            DateTimeFormatter.ofPattern(
                                    "dd/MM/yyyy")));
        }
        
        params.put(
                "P_HORAS_DIARIAS",
                valor(
                        solicitud.getCantidadHoras()));
        
        //tipo de permiso
        params.put(
                "P_FECHA_PERMISO",
                valor(
                        solicitud.getFechaInicio()));

      
        params.put(
                "P_FECHA_EMISION",
                valor(
                		obtenerFechaActual()));


        params.put(
                "P_HORA_SALIDA",
                valor(
                        solicitud.getHoraInicio()));
        
        System.out.println("hora de inicio"+solicitud.getHoraInicio());

        params.put(
                "P_HORA_INGRESO",
                valor(
                        solicitud.getHoraFin()));

        System.out.println("hora de inicio"+solicitud.getHoraFin());

        System.out.println("P_HORA_SALIDA = [" + params.get("P_HORA_SALIDA") + "]");
        System.out.println("P_HORA_INGRESO = [" + params.get("P_HORA_INGRESO") + "]");

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

private void cargarParametrosDescansoMedico(
        Map<String, Object> params,
        SolicitudRrhh solicitud) {

    params.put(
            "P_NOMBRE_MEDICO",
            valor(
                    solicitud.getNombreMedico()));

    params.put(
            "P_COLEGIATURA",
            valor(
                    solicitud.getNumeroColegiatura()));
    
    

    params.put(
            "P_FECHA_INICIO",
            formatearFecha(
                    solicitud.getFechaInicio()));

    params.put(
            "P_FECHA_FIN",
            formatearFecha(
                    solicitud.getFechaFin()));

    params.put(
            "P_DIAS",
            valor(
                    solicitud.getCantidadDias()));
    
    params.put(
            "P_EXPEDIDO_POR",
            solicitud.getTipoDescansoMedicoId() != null
                    ? solicitud.getTipoDescansoMedicoId().toString()
                    : "");
    
    List<TipoDescansoDoc> docs =
            tipoDescansoDocRepository
                    .findByTipoDescansoIdAndActivo(
                            solicitud.getTipoDescansoMedicoId(),
                            1);
    

    boolean citt = false;
    boolean comprobanteAtencion = false;
    boolean receta = false;
    boolean comprobanteTratamiento = false;

    for(TipoDescansoDoc doc : docs){

        String codigo =
                doc.getDocumento()
                   .getCodigo();

        switch(codigo){

            case "001":
                citt = true;
                break;

            case "002":
                comprobanteAtencion = true;
                break;

            case "003":
                receta = true;
                break;

            case "004":
                comprobanteTratamiento = true;
                break;
        }
    }
    
    params.put("P_DOC_CITT", citt);

    params.put(
            "P_DOC_COMPROBANTE_ATENCION",
            comprobanteAtencion);

    params.put(
            "P_DOC_RECETA",
            receta);

    params.put(
            "P_DOC_COMPROBANTE_TRATAMIENTO",
            comprobanteTratamiento);
    
    
}
    
private Double calcularDias(
        LocalDate fechaInicio,
        LocalDate fechaFin) {

    if (fechaInicio == null
            || fechaFin == null) {

        return 0.0;
    }

    return (double)
            ChronoUnit.DAYS.between(
                    fechaInicio,
                    fechaFin) + 1;
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
    
    private String formatearFecha(
            LocalDate fecha){

        if(fecha == null){
            return "";
        }

        return fecha.format(
                DateTimeFormatter.ofPattern(
                        "dd/MM/yyyy"));
    }
    private void cargarParametrosLicencia(
            Map<String, Object> params,
            SolicitudRrhh solicitud) {

        TipoLicencia licencia =
                tipoLicenciaRepository
                        .findById(
                                solicitud.getTipoLicenciaId())
                        .orElseThrow(() ->
                                new NegocioException(
                                        "Tipo licencia no encontrado"));

        params.put(
                "P_TIPO_LICENCIA",
                licencia.getCodigo());

        params.put(
                "P_MOTIVO",
                valor(
                        solicitud.getMotivo()));

        params.put(
                "P_FECHA_INICIO",
                formatearFecha(
                        solicitud.getFechaInicio()));

        params.put(
                "P_FECHA_FIN",
                formatearFecha(
                        solicitud.getFechaFin()));
        
        params.put(
                "P_DOCUMENTO_1",
                valor(
                        solicitud.getDocumento1()));

        params.put(
                "P_DOCUMENTO_2",
                valor(
                        solicitud.getDocumento2()));

        params.put(
                "P_FOLIOS",
                solicitud.getTotalFolios() == null
                        ? ""
                        : solicitud.getTotalFolios().toString());
    }
    
}