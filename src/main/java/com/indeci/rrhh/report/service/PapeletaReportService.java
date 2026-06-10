package com.indeci.rrhh.report.service;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.entity.Dependencia;
import com.indeci.rrhh.entity.Empleado;
import com.indeci.rrhh.entity.EmpleadoPlanilla;
import com.indeci.rrhh.entity.EmpleadoPuesto;
import com.indeci.rrhh.entity.Oficina;
import com.indeci.rrhh.entity.Persona;
import com.indeci.rrhh.entity.RegimenLaboral;
import com.indeci.rrhh.entity.SolicitudCompensacionDet;
import com.indeci.rrhh.entity.SolicitudRrhh;
import com.indeci.rrhh.entity.SolicitudVacacionDet;
import com.indeci.rrhh.entity.TipoDescansoDoc;
import com.indeci.rrhh.entity.TipoLicencia;
import com.indeci.rrhh.entity.TipoSolicitudRrhh;
import com.indeci.rrhh.entity.TipoVacacion;
import com.indeci.rrhh.report.dto.CompensacionReporteDto;
import com.indeci.rrhh.report.dto.PapeletaReportDto;
import com.indeci.rrhh.repository.DependenciaRepository;
import com.indeci.rrhh.repository.EmpleadoPlanillaRepository;
import com.indeci.rrhh.repository.EmpleadoPuestoRepository;
import com.indeci.rrhh.repository.EmpleadoRepository;
import com.indeci.rrhh.repository.OficinaRepository;
import com.indeci.rrhh.repository.PeriodoPlanillaRepository;
import com.indeci.rrhh.repository.PersonaRepository;
import com.indeci.rrhh.repository.RegimenLaboralRepository;
import com.indeci.rrhh.repository.SolicitudCompensacionDetRepository;
import com.indeci.rrhh.repository.SolicitudRrhhRepository;
import com.indeci.rrhh.repository.SolicitudVacacionDetRepository;
import com.indeci.rrhh.repository.TipoDescansoDocRepository;
import com.indeci.rrhh.repository.TipoLicenciaRepository;
import com.indeci.rrhh.repository.TipoSolicitudRrhhRepository;
import com.indeci.rrhh.repository.TipoVacacionRepository;

import lombok.RequiredArgsConstructor;

import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
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
	
    private static final String VAC_PROGRAMACION =
            "PROGRAMACION";

    private static final String VAC_ADELANTO =
            "ADELANTO";

    private static final String VAC_REPROG_ACTUAL =
            "REPROG_ACTUAL";

    private static final String VAC_REPROG_NUEVO =
            "REPROG_NUEVO";

    private static final String VAC_FRACC_ACTUAL =
            "FRACC_ACTUAL";

    private static final String VAC_FRACC_1 =
            "FRACC_1";

    private static final String VAC_FRACC_2 =
            "FRACC_2";

    private static final String VAC_FRACC_3 =
            "FRACC_3";

    private static final String VAC_FRACC_4 =
            "FRACC_4";
    
  
	
	
	private final SolicitudRrhhRepository
    solicitudRepository;

private final TipoSolicitudRrhhRepository
    tipoSolicitudRepository;

private final EmpleadoRepository
    empleadoRepository;


private final EmpleadoPuestoRepository
empleadoPuestoRepository;

private final DependenciaRepository
dependenciaRepository;

private final PersonaRepository
personaRepository;

private final EmpleadoPlanillaRepository empleadoPlanillaRepository;

private final TipoDescansoDocRepository tipoDescansoDocRepository;

private final TipoLicenciaRepository tipoLicenciaRepository;

private final TipoVacacionRepository tipoVacacionRepository;

private final SolicitudVacacionDetRepository
solicitudVacacionDetRepository;

private final RegimenLaboralRepository regimenLaboralRepository;
private final SolicitudCompensacionDetRepository
solicitudCompensacionDetRepository;


private void cargarParametrosVacacion(
        Map<String, Object> params,
        SolicitudRrhh solicitud) {

    TipoVacacion tipoVacacion =
            tipoVacacionRepository
                    .findById(
                            solicitud.getTipoVacacionId())
                    .orElseThrow(() ->
                            new NegocioException(
                                    "Tipo vacación no encontrada"));

    params.put(
            "P_TIPO_VACACION",
            tipoVacacion.getCodigo());

    List<SolicitudVacacionDet> detalles =
            solicitudVacacionDetRepository
                    .findBySolicitudIdAndActivo(
                            solicitud.getId(),
                            1);

    if(detalles.isEmpty()) {
        return;
    }
    int contadorReprog = 1;
    
    int contadorFracc = 1;
    for(SolicitudVacacionDet det : detalles) {
    	
    	

        switch(det.getTipo()) {
        
        case VAC_REPROG_ACTUAL:

            params.put(
                    "P_REPROG_ACTUAL_DEL",
                    formatearFecha(
                            det.getFechaInicio()));

            params.put(
                    "P_REPROG_ACTUAL_AL",
                    formatearFecha(
                            det.getFechaFin()));

            params.put(
                    "P_REPROG_ACTUAL_DIAS",
                    valor(
                            det.getTotalDias()));

            break;

            case VAC_PROGRAMACION:

                params.put(
                        "P_PROGRAM_DEL",
                        formatearFecha(
                                det.getFechaInicio()));

                params.put(
                        "P_PROGRAM_AL",
                        formatearFecha(
                                det.getFechaFin()));

                params.put(
                        "P_PROGRAM_DIAS",
                        valor(
                                det.getTotalDias()));

                break;

            case VAC_ADELANTO:

                params.put(
                        "P_ADELANTO_DEL",
                        formatearFecha(
                                det.getFechaInicio()));

                params.put(
                        "P_ADELANTO_AL",
                        formatearFecha(
                                det.getFechaFin()));

                params.put(
                        "P_ADELANTO_DIAS",
                        valor(
                                det.getTotalDias()));

                break;
            case VAC_REPROG_NUEVO:

                if(contadorReprog == 1) {

                    params.put(
                            "P_REPROG_NUEVO_DEL_1",
                            formatearFecha(
                                    det.getFechaInicio()));

                    params.put(
                            "P_REPROG_NUEVO_AL_1",
                            formatearFecha(
                                    det.getFechaFin()));

                    params.put(
                            "P_REPROG_NUEVO_DIAS_1",
                            valor(
                                    det.getTotalDias()));
                }

                if(contadorReprog == 2) {

                    params.put(
                            "P_REPROG_NUEVO_DEL_2",
                            formatearFecha(
                                    det.getFechaInicio()));

                    params.put(
                            "P_REPROG_NUEVO_AL_2",
                            formatearFecha(
                                    det.getFechaFin()));

                    params.put(
                            "P_REPROG_NUEVO_DIAS_2",
                            valor(
                                    det.getTotalDias()));
                }

                contadorReprog++;

                break;
                
            case VAC_FRACC_ACTUAL:

                params.put(
                        "P_FRACC_ACTUAL_DEL",
                        formatearFecha(
                                det.getFechaInicio()));

                params.put(
                        "P_FRACC_ACTUAL_AL",
                        formatearFecha(
                                det.getFechaFin()));

                params.put(
                        "P_FRACC_ACTUAL_DIAS",
                        valor(
                                det.getTotalDias()));

                break;
                
            case VAC_FRACC_1:
            case VAC_FRACC_2:
            case VAC_FRACC_3:
            case VAC_FRACC_4:

                params.put(
                        "P_FRACC_DEL_" + contadorFracc,
                        formatearFecha(
                                det.getFechaInicio()));

                params.put(
                        "P_FRACC_AL_" + contadorFracc,
                        formatearFecha(
                                det.getFechaFin()));

                params.put(
                        "P_FRACC_DIAS_" + contadorFracc,
                        valor(
                                det.getTotalDias()));

                contadorFracc++;

                break;
        }
        
        
    }
    
    params.put(
            "P_OFICINA_DESTINO",
            "OFICINA DE RECURSOS HUMANOS");

    params.put(
            "P_FECHA_EMISION",
            obtenerFechaActual());
}

private void cargarParametrosCompensacion(
        Map<String,Object> params,
        SolicitudRrhh solicitud){

    params.put(
            "P_HORAS_PERMISO",
            valor(
                    solicitud.getCantidadHoras()));

    params.put(
            "P_FECHA_PERMISO",
            formatearFecha(
                    solicitud.getFechaInicio()));

    params.put(
            "P_HORA_INICIO",
            valor(
                    solicitud.getHoraInicio()));

    params.put(
            "P_HORA_FIN",
            valor(
                    solicitud.getHoraFin()));

    params.put(
            "P_FECHA_EMISION",
            obtenerFechaActual());
    
    List<SolicitudCompensacionDet> detalles =
            solicitudCompensacionDetRepository
                    .findBySolicitudIdAndActivo(
                            solicitud.getId(),
                            1);
    
    List<CompensacionReporteDto> data =
            detalles.stream()
                    .map(d ->
                            new CompensacionReporteDto(
                                    valor(d.getCantidadHoras()),
                                    formatearFecha(
                                            d.getFechaCompensacion()),
                                    valor(
                                            d.getHoraInicio()),
                                    valor(
                                            d.getHoraFin())))
                    .toList();

    params.put(
            "DS_COMPENSACIONES",
            new JRBeanCollectionDataSource(
                    data));
}

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
        
        if("012".equals(tipo.getCodigo())) {

            nombreReporte = "formato_5.jasper";
        }
        
        if("013".equals(tipo.getCodigo())) {

            nombreReporte = "formato_6.jasper";
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
        
        String nombreDependencia = "";

        if (puesto.getDependenciaId() != null) {

            nombreDependencia =
                    dependenciaRepository
                            .findById(
                                    puesto.getDependenciaId())
                            .map(Dependencia::getNombre)
                            .orElse("");
        }

        params.put(
                "P_DEPENDENCIA",
                nombreDependencia);
        
       
       //INICIO REGIMEN LABORAL 
        
        RegimenLaboral reg =
                regimenLaboralRepository
                        .findById(
                                empleadoPlanilla.getRegimenLaboralId())
                        .orElse(null);

        String regimenCodigo = "";
        String regimenNombre = "";

        if(reg != null){
            regimenCodigo = reg.getCodigo();
            regimenNombre = reg.getNombre();
        }

        params.put(
                "P_REGIMEN",
                regimenCodigo);

        params.put(
                "P_REGIMEN_NOMBRE",
                regimenNombre);
        
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
        
        
        if("012".equals(tipo.getCodigo())) {
            cargarParametrosVacacion(
                    params,
                    solicitud);
        }
        if("013".equals(tipo.getCodigo())) {
            cargarParametrosCompensacion(
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