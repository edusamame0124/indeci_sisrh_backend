package com.indeci.rrhh.service;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.DocumentoAdjuntoDto;
import com.indeci.rrhh.dto.SaldoVacacionalDto;
import com.indeci.rrhh.dto.SolicitudCompensacionDetDto;
import com.indeci.rrhh.dto.SolicitudRrhhDto;
import com.indeci.rrhh.dto.SolicitudRrhhResponseDto;
import com.indeci.rrhh.dto.SolicitudVacacionDetDto;
import com.indeci.rrhh.dto.SolicitudWorkflowDocumentoDto;
import com.indeci.rrhh.entity.*;

import com.indeci.rrhh.repository.*;
import com.indeci.security.util.SecurityUtil;
import com.itextpdf.text.Document;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import com.indeci.audit.annotation.Auditable;
import com.indeci.audit.context.AuditoriaContext;
import java.util.Set;
import java.util.stream.Collectors;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.PdfWriter;

@Service
@RequiredArgsConstructor
public class SolicitudRrhhService {

    private final SolicitudRrhhRepository repository;

    private final EmpleadoRepository empleadoRepository;
    private final AuditoriaContext auditoriaContext;
    private final VacacionService vacacionService;

    private final TipoSolicitudRrhhRepository tipoSolicitudRepository;

    private final EstadoSolicitudRepository estadoSolicitudRepository;
    private final SolicitudRrhhHistRepository historialRepository;
    private final SolicitudRrhhDocRepository solicitudRrhhDocRepository;
    private final EmpleadoPuestoRepository empleadoPuestoRepository;
    private final FtpService ftpService;
    private final TipoLicenciaRepository tipoLicenciaRepository;
    
    private final TipoVacacionRepository tipoVacacionRepository;

    private final SolicitudVacacionDetRepository solicitudVacacionDetRepository;
    private final SolicitudCompensacionDetRepository solicitudCompensacionDetRepository;
    private final TipoDescansoDocRepository tipoDescansoDocRepository;
    
    //private static final String TIPO_VACACIONES = "VAC";
    

    @Auditable(
            accion = "CREAR_SOLICITUD_RRHH")
    public void registrar(
            SolicitudRrhhDto dto,
            MultipartFile sustento,
            List<MultipartFile> documentos){

    	Long empleadoId =
    	        obtenerEmpleadoActual();
        TipoSolicitudRrhh tipo =
                obtenerTipoSolicitud(
                        dto.getTipoSolicitudId());
        
        System.out.println("TIPO COMPLETO = " + tipo);
        
        System.out.println("TIPO ID = " + tipo.getId());
        System.out.println("TIPO CODIGO = " + tipo.getCodigo());
        System.out.println("TIPO NOMBRE = " + tipo.getNombre());
        System.out.println("TIPO MOSTRAR HORAS = " + tipo.getMostrarHoras());

        validarSolicitud(
                dto,
                tipo,
                empleadoId,
                sustento,
                documentos);

        EstadoSolicitud estadoBorrador =
                obtenerEstadoBorrador();

        SolicitudRrhh entity =
                construirSolicitud(
                        dto,
                        empleadoId,
                        estadoBorrador,
                        tipo);
        
        System.out.println("=================================");
        System.out.println("horaInicio=" + entity.getHoraInicio());
        System.out.println("horaFin=" + entity.getHoraFin());
        System.out.println("cantidadHoras=" + entity.getCantidadHoras());
        System.out.println("cantidadDias=" + entity.getCantidadDias());
        System.out.println("=================================");

        repository.save(entity);
        
        guardarDetalleVacacion(
                entity.getId(),
                dto);
        
        guardarDetalleCompensacion(
        		entity.getId(),
                dto);

        guardarSustento(
                entity,
                sustento);
        
        guardarDocumentosRequeridos(
                entity.getId(),
                documentos,
                dto);

        registrarHistorial(
                entity.getId(),
                null,
                entity.getEstadoSolicitudId(),
                "CREAR",
                "Solicitud creada");
    }
    
    private void guardarDocumentosRequeridos(
            Long solicitudId,
            List<MultipartFile> documentos,
            SolicitudRrhhDto dto) {

        if(documentos == null
                || documentos.isEmpty()) {
            return;
        }

        for(int i = 0;
        	    i < documentos.size();
        	    i++) {

        	    MultipartFile archivo =
        	            documentos.get(i);
        	    
        	    DocumentoAdjuntoDto meta = null;

        	    if(dto.getDocumentosAdjuntos() != null
        	            && dto.getDocumentosAdjuntos().size() > i) {

        	        meta =
        	                dto.getDocumentosAdjuntos()
        	                   .get(i);
        	    }

            if(archivo == null
                    || archivo.isEmpty()) {
                continue;
            }

            String ruta =
                    ftpService.subirArchivo(
                            archivo,
                            "papeletas",
                            archivo.getOriginalFilename());

            SolicitudRrhhDoc doc =
                    new SolicitudRrhhDoc();

            doc.setSolicitudId(
                    solicitudId);

            doc.setEtapa(
                    "REQUISITO");

            doc.setNombreArchivo(
                    archivo.getOriginalFilename());

            doc.setRutaArchivo(
                    ruta);

            doc.setMimeType(
                    archivo.getContentType());

            doc.setTamanioBytes(
                    archivo.getSize());

            doc.setVersionDoc(1);

            doc.setActivo(1);
            
            if(meta != null) {

                doc.setDocumentoRequeridoId(
                        meta.getDocumentoRequeridoId());
            }

            solicitudRrhhDocRepository.save(doc);
        }
    }
    private void guardarDetalleVacacion(
            Long solicitudId,
            SolicitudRrhhDto dto) {

        if(dto.getDetallesVacacion() == null
                || dto.getDetallesVacacion().isEmpty()) {
            return;
        }

        for(SolicitudVacacionDetDto det
                : dto.getDetallesVacacion()) {

            SolicitudVacacionDet entity =
                    new SolicitudVacacionDet();

            entity.setSolicitudId(
                    solicitudId);

            entity.setTipo(
                    det.getTipo());

            entity.setFechaInicio(
                    det.getFechaInicio());

            entity.setFechaFin(
                    det.getFechaFin());

            entity.setTotalDias(
                    det.getTotalDias());

            entity.setActivo(1);

            solicitudVacacionDetRepository
                    .save(entity);
        }
    }
    private void guardarDetalleCompensacion(
            Long solicitudId,
            SolicitudRrhhDto dto) {

        if(dto.getDetallesCompensacion() == null
                || dto.getDetallesCompensacion()
                      .isEmpty()) {
            return;
        }

        for(SolicitudCompensacionDetDto det
                : dto.getDetallesCompensacion()) {

            SolicitudCompensacionDet entity =
                    new SolicitudCompensacionDet();

            entity.setSolicitudId(
                    solicitudId);

            entity.setFechaCompensacion(
                    det.getFechaCompensacion());

            entity.setHoraInicio(
                    det.getHoraInicio());

            entity.setHoraFin(
                    det.getHoraFin());

            entity.setCantidadHoras(
                    det.getCantidadHoras());

            entity.setActivo(1);

            solicitudCompensacionDetRepository
                    .save(entity);
        }
    }
    
    private void validarCompensacion(
            SolicitudRrhhDto dto,
            TipoSolicitudRrhh tipo) {

        if(!Integer.valueOf(1)
                .equals(
                        tipo.getMostrarCompensacion())) {
            return;
        }

        if(dto.getDetallesCompensacion() == null
                || dto.getDetallesCompensacion()
                        .isEmpty()) {

            throw new NegocioException(
                    "Debe registrar el cronograma de compensación");
        }
    }
    private void validarVacacion(
            SolicitudRrhhDto dto,
            TipoSolicitudRrhh tipo) {

        if(!Integer.valueOf(1)
                .equals(tipo.getMostrarVacacion())) {
            return;
        }

        if(dto.getTipoVacacionId() == null) {

            throw new NegocioException(
                    "Debe seleccionar el tipo de vacación");
        }

        if(dto.getDetallesVacacion() == null
                || dto.getDetallesVacacion().isEmpty()) {

            throw new NegocioException(
                    "Debe registrar el detalle de vacaciones");
        }
    }
    
    private void validarDetalleVacacion(
            SolicitudRrhhDto dto) {

        if(dto.getTipoVacacionId() == null
                || dto.getDetallesVacacion() == null) {
            return;
        }

        TipoVacacion tipoVacacion =
                tipoVacacionRepository
                        .findById(
                                dto.getTipoVacacionId())
                        .orElseThrow(() ->
                                new NegocioException(
                                        "Tipo vacación no encontrado"));

        String codigo =
                tipoVacacion.getCodigo();

        switch(codigo) {

            case "001":
                validarProgramacion(
                        dto.getDetallesVacacion());
                break;

            case "002":
                validarAdelanto(
                        dto.getDetallesVacacion());
                break;

            case "003":
                validarReprogramacion(
                        dto.getDetallesVacacion());
                break;

            case "004":
                validarFraccionamiento(
                        dto.getDetallesVacacion());
                break;
        }
    }
    
    private void validarReprogramacion(
            List<SolicitudVacacionDetDto> detalles) {

        long actual =
                detalles.stream()
                        .filter(d ->
                                "REPROG_ACTUAL"
                                        .equals(
                                                d.getTipo()))
                        .count();

        long nuevos =
                detalles.stream()
                        .filter(d ->
                                "REPROG_NUEVO"
                                        .equals(
                                                d.getTipo()))
                        .count();

        if(actual != 1) {

            throw new NegocioException(
                    "Debe existir un detalle REPROG_ACTUAL");
        }

        if(nuevos < 1) {

            throw new NegocioException(
                    "Debe existir al menos un detalle REPROG_NUEVO");
        }

        if(nuevos > 2) {

            throw new NegocioException(
                    "Solo se permiten 2 reprogramaciones");
        }
        
        double diasActual =
                detalles.stream()
                        .filter(d ->
                                "REPROG_ACTUAL"
                                        .equals(
                                                d.getTipo()))
                        .mapToDouble(
                                d -> d.getTotalDias() == null
                                        ? 0
                                        : d.getTotalDias())
                        .sum();

        double diasNuevos =
                detalles.stream()
                        .filter(d ->
                                "REPROG_NUEVO"
                                        .equals(
                                                d.getTipo()))
                        .mapToDouble(
                                d -> d.getTotalDias() == null
                                        ? 0
                                        : d.getTotalDias())
                        .sum();

        if(Double.compare(
                diasActual,
                diasNuevos) != 0) {

            throw new NegocioException(
                    "La suma de los días reprogramados debe ser igual a los días de la programación actual");
        }
        
        
    }
    
    private void validarFraccionamiento(
            List<SolicitudVacacionDetDto> detalles) {

        long actual =
                detalles.stream()
                        .filter(d ->
                                "FRACC_ACTUAL"
                                        .equals(
                                                d.getTipo()))
                        .count();

        if(actual != 1) {

            throw new NegocioException(
                    "Debe existir un detalle FRACC_ACTUAL");
        }

        long fracciones =
                detalles.stream()
                        .filter(d ->
                                d.getTipo() != null
                                        &&
                                        d.getTipo()
                                                .startsWith(
                                                        "FRACC_")
                                        &&
                                        !"FRACC_ACTUAL"
                                                .equals(
                                                        d.getTipo()))
                        .count();

        if(fracciones < 1) {

            throw new NegocioException(
                    "Debe existir al menos una fracción");
        }

        if(fracciones > 4) {

            throw new NegocioException(
                    "Solo se permiten 4 fracciones");
        }
        
        double diasActual =
                detalles.stream()
                        .filter(d ->
                                "FRACC_ACTUAL"
                                        .equals(
                                                d.getTipo()))
                        .mapToDouble(
                                d -> d.getTotalDias() == null
                                        ? 0
                                        : d.getTotalDias())
                        .sum();

        double diasFraccionados =
                detalles.stream()
                        .filter(d ->
                                d.getTipo() != null
                                        &&
                                        d.getTipo()
                                                .startsWith(
                                                        "FRACC_")
                                        &&
                                        !"FRACC_ACTUAL"
                                                .equals(
                                                        d.getTipo()))
                        .mapToDouble(
                                d -> d.getTotalDias() == null
                                        ? 0
                                        : d.getTotalDias())
                        .sum();

        if(Double.compare(
                diasActual,
                diasFraccionados) != 0) {

            throw new NegocioException(
                    "La suma de las fracciones debe ser igual a los días de la programación actual");
        }
    }
    private void completarFechasVacacion(
            SolicitudRrhhDto dto,
            TipoSolicitudRrhh tipo) {

        if(!Integer.valueOf(1)
                .equals(tipo.getMostrarVacacion())) {
            return;
        }

        SolicitudVacacionDetDto detalle =
                dto.getDetallesVacacion()
                        .get(0);

        dto.setFechaInicio(
                detalle.getFechaInicio());

        dto.setFechaFin(
                detalle.getFechaFin());
    }
    
    private void validarDescansoMedico(
            SolicitudRrhhDto dto,
            TipoSolicitudRrhh tipo) {

        boolean esDescanso =
                "010".equals(
                        tipo.getCodigo());

        if(!esDescanso) {
            return;
        }

        if(dto.getTipoDescansoMedicoId() == null) {

            throw new NegocioException(
                    "Debe seleccionar el tipo de descanso médico");
        }

        if(dto.getNombreMedico() == null
                || dto.getNombreMedico().isBlank()) {

            throw new NegocioException(
                    "Debe ingresar el nombre del médico");
        }

        if(dto.getNumeroColegiatura() == null
                || dto.getNumeroColegiatura().isBlank()) {

            throw new NegocioException(
                    "Debe ingresar el número de colegiatura");
        }
    }
    
    private Long obtenerEmpleadoActual() {
    	
    	Long empleadoId=SecurityUtil.getEmpleadoId();
    	
    	if (empleadoId == null) {
            throw new NegocioException(
                    "El usuario no tiene un empleado vinculado. Solicite al administrador que vincule su cuenta.");
        }

        
        
        // ==========================================
        // VALIDAR EMPLEADO
        // ==========================================

        empleadoRepository
                .findById(empleadoId)
                .orElseThrow(() ->
                        new NegocioException(
                                "Empleado no encontrado"));

        return empleadoId;
    }
    
    private TipoSolicitudRrhh obtenerTipoSolicitud(
            Long id) {

        return tipoSolicitudRepository
                .findById(id)
                .orElseThrow(() ->
                        new NegocioException(
                                "Tipo de solicitud no existe"));
    }
    
    private void guardarSustento(
            SolicitudRrhh solicitud,
            MultipartFile sustento) {

        if(sustento != null
                && !sustento.isEmpty()) {

            String ruta =
                    ftpService.subirArchivo(
                            sustento,
                            "papeletas",
                            sustento.getOriginalFilename());

            SolicitudRrhhDoc doc =
                    new SolicitudRrhhDoc();

            doc.setSolicitudId(
            		solicitud.getId());

            doc.setEtapa(
                    "SUSTENTO");

            doc.setNombreArchivo(
                    sustento.getOriginalFilename());

            doc.setRutaArchivo(
                    ruta);

            doc.setMimeType(
                    sustento.getContentType());

            doc.setTamanioBytes(
                    sustento.getSize());

            doc.setVersionDoc(1);

            doc.setActivo(1);

            solicitudRrhhDocRepository.save(doc);
        }
    }
    
    private EstadoSolicitud obtenerEstadoBorrador() {

        return estadoSolicitudRepository
                .findByCodigo("BORRADOR")
                .orElseThrow(() ->
                        new NegocioException(
                                "Estado BORRADOR no encontrado"));
    }
    
    private void validarSolicitud(
            SolicitudRrhhDto dto,
            TipoSolicitudRrhh tipo,
            Long empleadoId,
            MultipartFile sustento,
            List<MultipartFile> documentos){

        validarLactancia(
                dto,
                tipo);
        
        validarDescansoMedico(
                dto,
                tipo);
        
        validarDocumentosRequeridos(
                dto,
                documentos);
        
        validarLicencia(
                dto,
                tipo);
        validarVacacion(
                dto,
                tipo);
        
        validarDetalleVacacion(
                dto); 
        
        completarFechasVacacion(
                dto,
                tipo);
        
        validarCompensacion(
                dto,
                tipo);

        if(Integer.valueOf(1)
                .equals(tipo.getMostrarCompensacion())) {
            validarHorasCompensacion(dto);
        }

        validarDetalleVacacion(
                dto);
        

        validarSustento(
                tipo,
                sustento);

        validarLugar(
                dto,
                tipo);

        validarObservacion(
                dto,
                tipo);

        validarFechas(
                dto,
                tipo);

        validarDuplicidad(
                dto,
                empleadoId);

        validarHoras(
                dto,
                tipo);
    }
    
    private void validarDocumentosRequeridos(
            SolicitudRrhhDto dto,
            List<MultipartFile> documentos) {

        if(dto.getTipoDescansoMedicoId() == null) {
            return;
        }

        List<TipoDescansoDoc> requeridos =
                tipoDescansoDocRepository
                        .findByTipoDescansoIdAndActivo(
                                dto.getTipoDescansoMedicoId(),
                                1);

        if(requeridos.isEmpty()) {
            return;
        }

        if(dto.getDocumentosAdjuntos() == null
                || dto.getDocumentosAdjuntos().isEmpty()) {

            throw new NegocioException(
                    "Debe adjuntar los documentos requeridos");
        }
        if(documentos == null
                || documentos.isEmpty()) {

            throw new NegocioException(
                    "Debe adjuntar los archivos de los documentos requeridos");
        }

        if(documentos.size()
                != dto.getDocumentosAdjuntos().size()) {

            throw new NegocioException(
                    "La cantidad de archivos no coincide con los documentos enviados");
        }
        
        Set<Long> ids =
                dto.getDocumentosAdjuntos()
                        .stream()
                        .map(
                            DocumentoAdjuntoDto::getDocumentoRequeridoId)
                        .collect(
                            Collectors.toSet());

        if(ids.size()
                != dto.getDocumentosAdjuntos().size()) {

            throw new NegocioException(
                    "Existen documentos repetidos");
        }
        if(documentos.size()
                != dto.getDocumentosAdjuntos().size()) {

            throw new NegocioException(
                    "La cantidad de archivos no coincide con los documentos enviados");
        }

        for(TipoDescansoDoc requerido
                : requeridos) {

            boolean existe =
                    dto.getDocumentosAdjuntos()
                            .stream()
                            .anyMatch(
                                    d ->
                                        requerido.getDocumentoId()
                                                .equals(
                                                        d.getDocumentoRequeridoId()));

            if(!existe) {

                throw new NegocioException(
                        "Falta adjuntar el documento: "
                        + requerido.getDocumento().getNombre());
            }
        }
    }
    private void validarProgramacion(
            List<SolicitudVacacionDetDto> detalles) {

        long total =
                detalles.stream()
                        .filter(d ->
                                "PROGRAMACION"
                                        .equals(
                                                d.getTipo()))
                        .count();

        if(total != 1) {

            throw new NegocioException(
                    "Debe existir un detalle PROGRAMACION");
        }
    }
    
    private void validarAdelanto(
            List<SolicitudVacacionDetDto> detalles) {

        long total =
                detalles.stream()
                        .filter(d ->
                                "ADELANTO"
                                        .equals(
                                                d.getTipo()))
                        .count();

        if(total != 1) {

            throw new NegocioException(
                    "Debe existir un detalle ADELANTO");
        }
    }
    
    private void validarLactancia(
            SolicitudRrhhDto dto,
            TipoSolicitudRrhh tipo) {

        boolean esLactancia =
                "008".equals(tipo.getCodigo())
                || "009".equals(tipo.getCodigo());

        if(!esLactancia){
            return;
        }

        if(dto.getFechaNacimientoHijo()
                .isAfter(LocalDate.now())) {

            throw new NegocioException(
                    "La fecha de nacimiento del hijo no puede ser futura");
        }
        
        
        boolean continua =
                dto.getHoraInicio() != null
                && !dto.getHoraInicio().isBlank()
                && dto.getHoraFin() != null
                && !dto.getHoraFin().isBlank();

        boolean fraccionada =
                dto.getMinutosIngreso() != null
                || dto.getMinutosSalida() != null;

        
        	
        	
        	
        	if(!continua && !fraccionada) {

        	    throw new NegocioException(
        	            "Debe registrar horario o minutos fraccionados");
        	}
        	
        	if(continua && fraccionada) {

        	    throw new NegocioException(
        	            "Debe elegir horario continuo o fraccionado, no ambos");
        	}
        	
        	if(continua) {

        	    double horas =
        	            calcularHoras(
        	                    dto.getHoraInicio(),
        	                    dto.getHoraFin());

        	    if(horas > 1) {

        	        throw new NegocioException(
        	                "El permiso por lactancia no puede exceder 1 hora diaria");
        	    }
        	    
        		LocalTime inicio =
            	        LocalTime.parse(dto.getHoraInicio());

            	LocalTime fin =
            	        LocalTime.parse(dto.getHoraFin());

            
            	
            	if(!fin.isAfter(inicio)) {

            	    throw new NegocioException(
            	            "La hora fin debe ser mayor que la hora inicio");
            	}
        	}
        	
        	if(fraccionada) {

        	    int ingreso =
        	            dto.getMinutosIngreso() == null
        	            ? 0
        	            : dto.getMinutosIngreso();

        	    int salida =
        	            dto.getMinutosSalida() == null
        	            ? 0
        	            : dto.getMinutosSalida();

        	    int total = ingreso + salida;

        	    if(total <= 0) {

        	        throw new NegocioException(
        	                "Debe indicar minutos de ingreso o salida");
        	    }

        	    if(total > 60) {

        	        throw new NegocioException(
        	                "El permiso por lactancia no puede exceder 60 minutos diarios");
        	    }
        	}
        	
        	if(dto.getMinutosIngreso() != null
        	        && dto.getMinutosIngreso() < 0) {

        	    throw new NegocioException(
        	            "Minutos ingreso inválidos");
        	}

        	if(dto.getMinutosSalida() != null
        	        && dto.getMinutosSalida() < 0) {

        	    throw new NegocioException(
        	            "Minutos salida inválidos");
        	}

            if(dto.getFechaNacimientoHijo() == null) {
                throw new NegocioException(
                        "Fecha nacimiento del hijo es obligatoria");
            }

            if(dto.getFechaFinPostnatal() == null) {
                throw new NegocioException(
                        "Fecha fin postnatal es obligatoria");
            }
            
            
        	LocalDate fechaLimite =
        	        dto.getFechaNacimientoHijo()
        	           .plusYears(1);

        	if(LocalDate.now().isAfter(fechaLimite)) {

        	    throw new NegocioException(
        	            "El menor ya cumplió un año de edad");
        	}
            
        
        
    }
    
    private void validarSustento(
            TipoSolicitudRrhh tipo,
            MultipartFile sustento) {


        	
        	if(Integer.valueOf(1)
        	        .equals(tipo.getRequiereSustento())) {

            if(sustento == null
                    || sustento.isEmpty()) {

                throw new NegocioException(
                        "Debe adjuntar sustento");
            }
        }
    }
    
    private void validarLugar(
            SolicitudRrhhDto dto,
            TipoSolicitudRrhh tipo) {

    	if(Integer.valueOf(1)
    	        .equals(tipo.getRequiereLugar())) {

            if(dto.getLugarComision() == null
                    || dto.getLugarComision().isBlank()) {

                throw new NegocioException(
                        "Debe indicar el lugar de comisión");
            }
        }
    }
    
    private void validarObservacion(
            SolicitudRrhhDto dto,
            TipoSolicitudRrhh tipo) {

 
        	if(Integer.valueOf(1)
        	        .equals(tipo.getRequiereObservacion())) {

            if(dto.getObservacion() == null
                    || dto.getObservacion().isBlank()) {

                throw new NegocioException(
                        "Debe registrar una observación");
            }
        }
    }
    
    private void validarFechas(
            SolicitudRrhhDto dto,
            TipoSolicitudRrhh tipo) {
    	
    	if(Integer.valueOf(1)
    	        .equals(tipo.getMostrarVacacion())) {
    	    return;
    	}

        if(tipo.getMostrarHoras() == 0) {

            if(dto.getFechaFin() == null) {

                throw new NegocioException(
                        "Fecha fin es obligatoria");
            }

        } else {

            dto.setFechaFin(
                    dto.getFechaInicio());
        }
    }
    
    private void validarDuplicidad(
            SolicitudRrhhDto dto,
            Long empleadoId) {

        boolean existe =
                repository
                        .existsByEmpleadoIdAndTipoSolicitudIdAndActivoAndFechaInicioLessThanEqualAndFechaFinGreaterThanEqual(
                                empleadoId,
                                dto.getTipoSolicitudId(),
                                1,
                                dto.getFechaFin(),
                                dto.getFechaInicio());

        if(existe) {

            throw new NegocioException(
                    "El empleado ya tiene una solicitud de este tipo en esas fechas");
        }
    }

    
    private void validarHoras(
            SolicitudRrhhDto dto,
            TipoSolicitudRrhh tipo) {

        boolean esLactancia =
                "008".equals(tipo.getCodigo())
                || "009".equals(tipo.getCodigo());

        if(tipo.getMostrarHoras() == 1
                && !esLactancia) {

            if(dto.getHoraInicio() == null
                    || dto.getHoraInicio().isBlank()) {

                throw new NegocioException(
                        "Hora inicio es obligatoria");
            }

            if(dto.getHoraFin() == null
                    || dto.getHoraFin().isBlank()) {

                throw new NegocioException(
                        "Hora fin es obligatoria");
            }
        }
    }
    private void validarLicencia(
            SolicitudRrhhDto dto,
            TipoSolicitudRrhh tipo) {

        if(!Integer.valueOf(1)
                .equals(tipo.getMostrarLicencia())) {
            return;
        }

        if(dto.getTipoLicenciaId() == null) {

            throw new NegocioException(
                    "Debe seleccionar el tipo de licencia");
        }

        if(dto.getTotalFolios() == null
                || dto.getTotalFolios() <= 0) {

            throw new NegocioException(
                    "Debe indicar la cantidad de folios");
        }
    }
    
    private SolicitudRrhh construirSolicitud(
            SolicitudRrhhDto dto,
            Long empleadoId,
            EstadoSolicitud estado,
            TipoSolicitudRrhh tipo) {

        SolicitudRrhh entity =
                new SolicitudRrhh();

        entity.setEmpleadoId(empleadoId);
        entity.setTipoSolicitudId(dto.getTipoSolicitudId());
        entity.setEstadoSolicitudId(estado.getId());

        entity.setFechaInicio(dto.getFechaInicio());
        entity.setFechaFin(dto.getFechaFin());

        entity.setMotivo(dto.getMotivo());
        entity.setObservacion(dto.getObservacion());

        entity.setHoraInicio(dto.getHoraInicio());
        entity.setHoraFin(dto.getHoraFin());

        entity.setLugarComision(dto.getLugarComision());

        entity.setFechaNacimientoHijo(
                dto.getFechaNacimientoHijo());

        entity.setFechaFinPostnatal(
                dto.getFechaFinPostnatal());

        entity.setMinutosIngreso(
                dto.getMinutosIngreso());

        entity.setMinutosSalida(
                dto.getMinutosSalida());

        entity.setActivo(1);

        entity.setCreatedAt(
                LocalDateTime.now());
        
        entity.setTipoDescansoMedicoId(
                dto.getTipoDescansoMedicoId());

        entity.setNombreMedico(
                dto.getNombreMedico());

        entity.setNumeroColegiatura(
                dto.getNumeroColegiatura());
        
        entity.setTipoLicenciaId(
                dto.getTipoLicenciaId());
        
        entity.setDocumento1(
                dto.getDocumento1());

        entity.setDocumento2(
                dto.getDocumento2());

        entity.setTotalFolios(
                dto.getTotalFolios());
        entity.setTipoVacacionId(
                dto.getTipoVacacionId());

        calcularCantidades(
                entity,
                dto,
                tipo);

        return entity;
    }
    
    private void calcularCantidades(
            SolicitudRrhh entity,
            SolicitudRrhhDto dto,
            TipoSolicitudRrhh tipo) {
    	
    	System.out.println("codigo=" + tipo.getCodigo());
    	System.out.println("mostrarHoras=" + tipo.getMostrarHoras());
    	System.out.println("horaInicio dto=" + dto.getHoraInicio());
    	System.out.println("horaFin dto=" + dto.getHoraFin());
    	
    	if(Integer.valueOf(1)
    	        .equals(tipo.getMostrarVacacion())) {

    	    double total =
    	            dto.getDetallesVacacion()
    	               .stream()
    	               .mapToDouble(
    	                    d -> d.getTotalDias() == null
    	                         ? 0
    	                         : d.getTotalDias())
    	               .sum();

    	    entity.setCantidadDias(
    	            total);

    	    entity.setCantidadHoras(
    	            null);

    	    return;
    	}

        boolean esLactancia =
                "008".equals(tipo.getCodigo())
                || "009".equals(tipo.getCodigo());

        boolean fraccionada =
                dto.getMinutosIngreso() != null
                || dto.getMinutosSalida() != null;

        if(tipo.getMostrarHoras() == 0) {

            entity.setCantidadDias(
                    calcularDias(
                            dto.getFechaInicio(),
                            dto.getFechaFin()));

            entity.setCantidadHoras(
                    null);

            return;
        }

        entity.setCantidadDias(
                null);

        if(esLactancia && fraccionada){

            int ingreso =
                    dto.getMinutosIngreso() == null
                    ? 0
                    : dto.getMinutosIngreso();

            int salida =
                    dto.getMinutosSalida() == null
                    ? 0
                    : dto.getMinutosSalida();

            entity.setCantidadHoras(
                    (ingreso + salida) / 60.0);

        } else {
        	
        	System.out.println("Ingrese a HORAS CALCULADAS=");
        	Double horas =
        	        calcularHoras(
        	                dto.getHoraInicio(),
        	                dto.getHoraFin());

        	System.out.println("HORAS CALCULADAS=" + horas);


            entity.setCantidadHoras(
                    calcularHoras(
                            dto.getHoraInicio(),
                            dto.getHoraFin()));
        }
    }
    // ==========================================
    // REGISTRAR
    // ==========================================
  
    public void registrarAntiguo(
            SolicitudRrhhDto dto,
            MultipartFile sustento) {
        Long empleadoId = SecurityUtil.getEmpleadoId();
        if (empleadoId == null) {
            throw new NegocioException(
                    "El usuario no tiene un empleado vinculado. Solicite al administrador que vincule su cuenta.");
        }

        
        
        // ==========================================
        // VALIDAR EMPLEADO
        // ==========================================

        empleadoRepository
                .findById(empleadoId)
                .orElseThrow(() ->
                        new NegocioException(
                                "Empleado no encontrado"));

        // ==========================================
        // OBTENER TIPO SOLICITUD
        // ==========================================

        TipoSolicitudRrhh tipo =
                tipoSolicitudRepository
                        .findById(
                                dto.getTipoSolicitudId())
                        .orElseThrow(() ->
                                new NegocioException(
                                        "Tipo solicitud no encontrado"));
        
        boolean esLactancia =
                "008".equals(tipo.getCodigo())
                || "009".equals(tipo.getCodigo());
        
        boolean continua =
    	        dto.getHoraInicio() != null
    	        && !dto.getHoraInicio().isBlank()
    	        && dto.getHoraFin() != null
    	        && !dto.getHoraFin().isBlank();

    	boolean fraccionada =
    	        dto.getMinutosIngreso() != null
    	        || dto.getMinutosSalida() != null;
        
        if(esLactancia) {
        	
        	
        	
        	if(!continua && !fraccionada) {

        	    throw new NegocioException(
        	            "Debe registrar horario o minutos fraccionados");
        	}
        	
        	if(continua && fraccionada) {

        	    throw new NegocioException(
        	            "Debe elegir horario continuo o fraccionado, no ambos");
        	}
        	
        	if(continua) {

        	    double horas =
        	            calcularHoras(
        	                    dto.getHoraInicio(),
        	                    dto.getHoraFin());

        	    if(horas > 1) {

        	        throw new NegocioException(
        	                "El permiso por lactancia no puede exceder 1 hora diaria");
        	    }
        	    
        		LocalTime inicio =
            	        LocalTime.parse(dto.getHoraInicio());

            	LocalTime fin =
            	        LocalTime.parse(dto.getHoraFin());

            
            	
            	if(!fin.isAfter(inicio)) {

            	    throw new NegocioException(
            	            "La hora fin debe ser mayor que la hora inicio");
            	}
        	}
        	
        	if(fraccionada) {

        	    int ingreso =
        	            dto.getMinutosIngreso() == null
        	            ? 0
        	            : dto.getMinutosIngreso();

        	    int salida =
        	            dto.getMinutosSalida() == null
        	            ? 0
        	            : dto.getMinutosSalida();

        	    int total = ingreso + salida;

        	    if(total <= 0) {

        	        throw new NegocioException(
        	                "Debe indicar minutos de ingreso o salida");
        	    }

        	    if(total > 60) {

        	        throw new NegocioException(
        	                "El permiso por lactancia no puede exceder 60 minutos diarios");
        	    }
        	}
        	
        	if(dto.getMinutosIngreso() != null
        	        && dto.getMinutosIngreso() < 0) {

        	    throw new NegocioException(
        	            "Minutos ingreso inválidos");
        	}

        	if(dto.getMinutosSalida() != null
        	        && dto.getMinutosSalida() < 0) {

        	    throw new NegocioException(
        	            "Minutos salida inválidos");
        	}

            if(dto.getFechaNacimientoHijo() == null) {
                throw new NegocioException(
                        "Fecha nacimiento del hijo es obligatoria");
            }

            if(dto.getFechaFinPostnatal() == null) {
                throw new NegocioException(
                        "Fecha fin postnatal es obligatoria");
            }
            
            
        	LocalDate fechaLimite =
        	        dto.getFechaNacimientoHijo()
        	           .plusYears(1);

        	if(LocalDate.now().isAfter(fechaLimite)) {

        	    throw new NegocioException(
        	            "El menor ya cumplió un año de edad");
        	}
            
        
        }
        
    
        
        
        
        
        if(tipo.getRequiereSustento() == 1) {

            if(sustento == null
                    || sustento.isEmpty()) {

                throw new NegocioException(
                        "Debe adjuntar sustento");
            }
        }

        // ==========================================
        // VALIDAR FECHA FIN
        // ==========================================
        
        if(tipo.getRequiereLugar() == 1) {

            if(dto.getLugarComision() == null
                    || dto.getLugarComision().isBlank()) {

                throw new NegocioException(
                        "Debe indicar el lugar de comisión");
            }
        }
        
     // ===============================
     // VALIDAR OBSERVACION
     // ===============================

     if(tipo.getRequiereObservacion() == 1) {

         if(dto.getObservacion() == null
                 || dto.getObservacion().isBlank()) {

             throw new NegocioException(
                     "Debe registrar una observación");
         }
     }

        if (tipo.getMostrarHoras() == 0) {

            if (dto.getFechaFin() == null) {

                throw new NegocioException(
                        "Fecha fin es obligatoria");
            }

        } else {

            dto.setFechaFin(
                    dto.getFechaInicio());
        }

        // ==========================================
        // VALIDAR DUPLICIDAD
        // ==========================================

        boolean existe =
                repository
                        .existsByEmpleadoIdAndTipoSolicitudIdAndActivoAndFechaInicioLessThanEqualAndFechaFinGreaterThanEqual(
                        		empleadoId,
                                dto.getTipoSolicitudId(),
                                1,
                                dto.getFechaFin(),
                                dto.getFechaInicio());

        if (existe) {

            throw new NegocioException(
                    "El empleado ya tiene una solicitud de este tipo en esas fechas");
        }

        // ==========================================
        // VALIDAR HORAS
        // ==========================================
        
      

        if(tipo.getMostrarHoras() == 1
                && !esLactancia) {

            if (dto.getHoraInicio() == null
                    || dto.getHoraInicio().isBlank()) {

                throw new NegocioException(
                        "Hora inicio es obligatoria");
            }

            if (dto.getHoraFin() == null
                    || dto.getHoraFin().isBlank()) {

                throw new NegocioException(
                        "Hora fin es obligatoria");
            }
        }

        // ==========================================
        // VALIDAR SUSTENTO
        // ==========================================

   

        // ==========================================
        // OBTENER ESTADO BORRADOR
        // ==========================================

        EstadoSolicitud estadoBorrador =
                estadoSolicitudRepository
                        .findByCodigo(
                                "BORRADOR")
                        .orElseThrow(() ->
                                new NegocioException(
                                        "Estado BORRADOR no existe"));

        // ==========================================
        // CREAR ENTIDAD
        // ==========================================

        SolicitudRrhh entity =
                new SolicitudRrhh();
       
        entity.setEmpleadoId(
                empleadoId);

        entity.setTipoSolicitudId(
                dto.getTipoSolicitudId());

        entity.setEstadoSolicitudId(
                estadoBorrador.getId());

        entity.setFechaInicio(
                dto.getFechaInicio());

        entity.setFechaFin(
                dto.getFechaFin());

        entity.setMotivo(
                dto.getMotivo());

        entity.setObservacion(
                dto.getObservacion());

        

        entity.setHoraInicio(
                dto.getHoraInicio());

        entity.setHoraFin(
                dto.getHoraFin());

        entity.setActivo(1);
        entity.setLugarComision(dto.getLugarComision());
        
        
        entity.setFechaNacimientoHijo(
                dto.getFechaNacimientoHijo());

        entity.setFechaFinPostnatal(
                dto.getFechaFinPostnatal());

        entity.setMinutosIngreso(
                dto.getMinutosIngreso());

        entity.setMinutosSalida(
                dto.getMinutosSalida());

        entity.setCreatedAt(
                LocalDateTime.now());

        // ==========================================
        // CALCULAR HORAS / DÍAS
        // ==========================================
        if(esLactancia && fraccionada){

            int ingreso =
                    dto.getMinutosIngreso() == null ? 0 : dto.getMinutosIngreso();

            int salida =
                    dto.getMinutosSalida() == null ? 0 : dto.getMinutosSalida();

            entity.setCantidadHoras(
                    (ingreso + salida) / 60.0);

        }
        else {

            entity.setCantidadHoras(
                    calcularHoras(
                            dto.getHoraInicio(),
                            dto.getHoraFin()));
        }

        // ==========================================
        // GUARDAR
        // ==========================================

        repository.save(entity);
        
        ///GUARDAR SUSTENTO
        ///
        if(sustento != null
                && !sustento.isEmpty()) {

            String ruta =
                    ftpService.subirArchivo(
                            sustento,
                            "papeletas",
                            sustento.getOriginalFilename());

            SolicitudRrhhDoc doc =
                    new SolicitudRrhhDoc();

            doc.setSolicitudId(
                    entity.getId());

            doc.setEtapa(
                    "SUSTENTO");

            doc.setNombreArchivo(
                    sustento.getOriginalFilename());

            doc.setRutaArchivo(
                    ruta);

            doc.setMimeType(
                    sustento.getContentType());

            doc.setTamanioBytes(
                    sustento.getSize());

            doc.setVersionDoc(1);

            doc.setActivo(1);

            solicitudRrhhDocRepository.save(doc);
        }

        // ==========================================
        // HISTORIAL
        // ==========================================

        registrarHistorial(
                entity.getId(),
                null,
                entity.getEstadoSolicitudId(),
                "CREAR",
                "Solicitud creada");

        // ==========================================
        // AUDITORÍA
        // ==========================================

        auditoriaContext.setDetalle(
                "Solicitud RRHH creada empleado ID: "
                        + empleadoId);
    }
    
    private Double calcularDias(
            LocalDate fechaInicio,
            LocalDate fechaFin) {

        if (fechaInicio == null
                || fechaFin == null) {

            return 0.0;
        }

        long dias =
                ChronoUnit.DAYS.between(
                        fechaInicio,
                        fechaFin) + 1;

        return (double) dias;
    }
    
    private Double calcularHoras(
            String horaInicio,
            String horaFin) {

        if (horaInicio == null
                || horaFin == null) {

            return 0.0;
        }

        LocalTime inicio =
                LocalTime.parse(
                        horaInicio);

        LocalTime fin =
                LocalTime.parse(
                        horaFin);

        long minutos =
                Duration.between(
                        inicio,
                        fin)
                        .toMinutes();

        return minutos / 60.0;
    }

    // ==========================================
    // LISTAR EMPLEADO
    // ==========================================

    public List<SolicitudRrhhResponseDto>
    listarPorEmpleado(Long empleadoId) {

        return repository
                .findByEmpleadoIdAndActivo(
                        empleadoId,
                        1)
                .stream()
                .map(this::convertir)
                .toList();
    }

    // ==========================================
    // CONVERTIR DTO
    // ==========================================

    private SolicitudRrhhResponseDto
    convertir(SolicitudRrhh s) {

        SolicitudRrhhResponseDto dto =
                new SolicitudRrhhResponseDto();

        dto.setId(s.getId());

        dto.setEmpleadoId(
                s.getEmpleadoId());
        
        dto.setLugarComision(
                s.getLugarComision());

        Empleado empleado =
                empleadoRepository
                        .findById(
                                s.getEmpleadoId())
                        .orElse(null);

        if (empleado != null) {

            dto.setEmpleado(
                    empleado.getCodigoInterno());
        }

        dto.setTipoSolicitudId(
                s.getTipoSolicitudId());

        TipoSolicitudRrhh tipo =
                tipoSolicitudRepository
                        .findById(
                                s.getTipoSolicitudId())
                        .orElse(null);

        if (tipo != null) {

            dto.setTipoSolicitud(
                    tipo.getNombre());
        }

        dto.setEstadoSolicitudId(
                s.getEstadoSolicitudId());

        EstadoSolicitud estado =
                estadoSolicitudRepository
                        .findById(
                                s.getEstadoSolicitudId())
                        .orElse(null);

        if (estado != null) {

            dto.setEstadoSolicitud(
                    estado.getNombre());
        }
        
        if (s.getTipoLicenciaId() != null) {

            TipoLicencia licencia =
                    tipoLicenciaRepository
                            .findById(
                                    s.getTipoLicenciaId())
                            .orElse(null);

            if (licencia != null) {

                dto.setTipoLicencia(
                        licencia.getNombre());
            }
        }
        dto.setFechaInicio(
                s.getFechaInicio());

        dto.setFechaFin(
                s.getFechaFin());

        dto.setCantidadDias(
                s.getCantidadDias());

        dto.setMotivo(
                s.getMotivo());

        dto.setObservacion(
                s.getObservacion());

        dto.setArchivoSustento(
                s.getArchivoSustento());

        dto.setActivo(
                s.getActivo());
        
        dto.setHoraInicio(
                s.getHoraInicio());

        dto.setHoraFin(
                s.getHoraFin());

        dto.setCantidadHoras(
                s.getCantidadHoras());
        
        dto.setFechaNacimientoHijo(
                s.getFechaNacimientoHijo());

        dto.setFechaFinPostnatal(
                s.getFechaFinPostnatal());

        dto.setMinutosIngreso(
                s.getMinutosIngreso());

        dto.setMinutosSalida(
                s.getMinutosSalida());
        
        dto.setDocumento1(
                s.getDocumento1());

        dto.setDocumento2(
                s.getDocumento2());

        dto.setTotalFolios(
                s.getTotalFolios());
        
        dto.setTipoVacacionId(
                s.getTipoVacacionId());
        
        
        
        
        if(s.getTipoVacacionId() != null) {

            TipoVacacion vacacion =
                    tipoVacacionRepository
                            .findById(
                                    s.getTipoVacacionId())
                            .orElse(null);

            if(vacacion != null) {

                dto.setTipoVacacion(
                        vacacion.getNombre());
            }
        }
        
        List<SolicitudVacacionDetDto> detalles =
                solicitudVacacionDetRepository
                        .findBySolicitudIdAndActivo(
                                s.getId(),
                                1)
                        .stream()
                        .map(det -> {

                            SolicitudVacacionDetDto d =
                                    new SolicitudVacacionDetDto();

                            d.setTipo(
                                    det.getTipo());

                            d.setFechaInicio(
                                    det.getFechaInicio());

                            d.setFechaFin(
                                    det.getFechaFin());

                            d.setTotalDias(
                                    det.getTotalDias());

                            return d;
                        })
                        .toList();

        dto.setDetallesVacacion(
                detalles);
        
        List<SolicitudCompensacionDet> detallesCompensacion =
                solicitudCompensacionDetRepository
                        .findBySolicitudIdAndActivo(
                                s.getId(),
                                1);

        if(!detallesCompensacion.isEmpty()) {

            dto.setDetallesCompensacion(
                    detallesCompensacion
                            .stream()
                            .map(det -> {

                                SolicitudCompensacionDetDto d =
                                        new SolicitudCompensacionDetDto();

                                d.setFechaCompensacion(
                                        det.getFechaCompensacion());

                                d.setHoraInicio(
                                        det.getHoraInicio());

                                d.setHoraFin(
                                        det.getHoraFin());

                                d.setCantidadHoras(
                                        det.getCantidadHoras());

                                return d;

                            })
                            .toList());
        }

        return dto;
    }
    
    @Auditable(
            accion = "ENVIAR_SOLICITUD_RRHH")
    public void enviar(
            Long id,
            MultipartFile file,
            String observacion) {

        SolicitudRrhh solicitud =
                repository
                        .findById(id)
                        .orElseThrow(() ->
                                new NegocioException(
                                        "Solicitud no encontrada"));

        EstadoSolicitud estadoActual =
                estadoSolicitudRepository
                        .findById(
                                solicitud.getEstadoSolicitudId())
                        .orElseThrow(() ->
                                new NegocioException(
                                        "Estado no encontrado"));
        
        if (!"BORRADOR".equals(
                estadoActual.getCodigo())) {

            throw new NegocioException(
                    "Solo solicitudes en borrador pueden enviarse");
        }

        // ==========================================
        // VALIDAR ARCHIVO
        // ==========================================

        MultipartFile archivoFinal = file;

        String rutaArchivo;
        String nombreArchivo;
        String mimeType;
        Long tamanioBytes;

        if (file == null || file.isEmpty()) {

            byte[] pdf =
                    generarPdfSinFirma(
                            "SOLICITUD NO REQUIERE FIRMA");

            rutaArchivo =
                    ftpService.subirPdfGenerado(
                            pdf,
                            "papeletas",
                            "solicitud_no_requiere_firma.pdf");

            nombreArchivo =
                    "solicitud_no_requiere_firma.pdf";

            mimeType =
                    "application/pdf";

            tamanioBytes =
                    (long) pdf.length;

        } else {

            rutaArchivo =
                    ftpService.subirArchivo(
                            file,
                            "papeletas",
                            file.getOriginalFilename());

            nombreArchivo =
                    file.getOriginalFilename();

            mimeType =
                    file.getContentType();

            tamanioBytes =
                    file.getSize();
        }
        // ==========================================
        // SUBIR FTP
        // ==========================================

       

        // ==========================================
        // GUARDAR DOCUMENTO
        // ==========================================

        SolicitudRrhhDoc doc =
                new SolicitudRrhhDoc();

        doc.setSolicitudId(
                id);

        doc.setEtapa(
                "EMPLEADO");

        doc.setNombreArchivo(
                nombreArchivo);

        doc.setRutaArchivo(
                rutaArchivo);

        doc.setMimeType(
                mimeType);

        doc.setTamanioBytes(
                tamanioBytes);

        doc.setVersionDoc(1);

        doc.setObservacion(
                observacion);

        doc.setUsuarioUpload(
                "ADMIN");

        doc.setCreatedAt(
                LocalDateTime.now());

        doc.setActivo(1);

        solicitudRrhhDocRepository.save(doc);

        // ==========================================
        // OBTENER ESTADO ENVIADO
        // ==========================================

        Long estadoOrigen =
                solicitud.getEstadoSolicitudId();

        EstadoSolicitud estadoEnviado =
                estadoSolicitudRepository
                        .findByCodigo("ENVIADO")
                        .orElseThrow(() ->
                                new NegocioException(
                                        "Estado ENVIADO no existe"));

        // ==========================================
        // ACTUALIZAR
        // ==========================================

        solicitud.setEstadoSolicitudId(
                estadoEnviado.getId());

        repository.save(solicitud);

        registrarHistorial(
                solicitud.getId(),
                estadoOrigen,
                estadoEnviado.getId(),
                "ENVIAR",
                "Solicitud enviada");
    }
    

    @Auditable(
            accion = "APROBAR_SOLICITUD_JEFE")
    public void aprobarSupervisor(
            Long solicitudId,
            MultipartFile file,
            String observacion) {

        SolicitudRrhh solicitud =
                repository
                        .findById(solicitudId)
                        .orElseThrow(() ->
                                new NegocioException(
                                        "Solicitud no encontrada"));

        EstadoSolicitud estadoActual =
                estadoSolicitudRepository
                        .findById(
                                solicitud.getEstadoSolicitudId())
                        .orElseThrow(() ->
                                new NegocioException(
                                        "Estado no encontrado"));

        // ==========================================
        // VALIDAR ESTADO
        // ==========================================

        if (!estadoActual.getCodigo()
                .equals("ENVIADO")) {

            throw new NegocioException(
                    "Solo solicitudes enviadas pueden aprobarse");
        }

        // ==========================================
        // VALIDAR ARCHIVO
        // ==========================================

   

        String rutaArchivo;
        String nombreArchivo;
        String mimeType;
        Long tamanioBytes;

        if (file == null || file.isEmpty()) {

            byte[] pdf =
                    generarPdfSinFirma(
                            "SOLICITUD NO REQUIERE FIRMA");

            rutaArchivo =
                    ftpService.subirPdfGenerado(
                            pdf,
                            "papeletas",
                            "solicitud_no_requiere_firma.pdf");

            nombreArchivo =
                    "solicitud_no_requiere_firma.pdf";

            mimeType =
                    "application/pdf";

            tamanioBytes =
                    (long) pdf.length;

        } else {

            rutaArchivo =
                    ftpService.subirArchivo(
                            file,
                            "papeletas",
                            file.getOriginalFilename());

            nombreArchivo =
                    file.getOriginalFilename();

            mimeType =
                    file.getContentType();

            tamanioBytes =
                    file.getSize();
        }

        // ==========================================
        // SUBIR FTP
        // ==========================================

       

        // ==========================================
        // GUARDAR DOCUMENTO
        // ==========================================

        SolicitudRrhhDoc doc =
                new SolicitudRrhhDoc();

        doc.setSolicitudId(
                solicitudId);

        doc.setEtapa(
                "JEFE");

        doc.setNombreArchivo(
                nombreArchivo);

        doc.setRutaArchivo(
                rutaArchivo);

        doc.setMimeType(
                mimeType);

        doc.setTamanioBytes(
                tamanioBytes);

        doc.setVersionDoc(2);

        doc.setObservacion(
                observacion);

        doc.setUsuarioUpload(
                "ADMIN");

        doc.setCreatedAt(
                LocalDateTime.now());

        doc.setActivo(1);

        solicitudRrhhDocRepository.save(doc);

        // ==========================================
        // OBTENER ESTADO ORIGEN
        // ==========================================

        Long estadoOrigen =
                solicitud.getEstadoSolicitudId();

        // ==========================================
        // BUSCAR ESTADO APROBADO_JEFE
        // ==========================================

        EstadoSolicitud estado =
                estadoSolicitudRepository
                        .findByCodigo(
                                "APROBADO_JEFE")
                        .orElseThrow(() ->
                                new NegocioException(
                                        "Estado no existe"));

        // ==========================================
        // ACTUALIZAR SOLICITUD
        // ==========================================

        solicitud.setEstadoSolicitudId(
                estado.getId());

        repository.save(solicitud);

        // ==========================================
        // HISTORIAL
        // ==========================================

        registrarHistorial(
                solicitud.getId(),
                estadoOrigen,
                estado.getId(),
                "APROBAR_JEFE",
                "Solicitud aprobada por jefe");

        // ==========================================
        // AUDITORIA
        // ==========================================

        auditoriaContext.setDetalle(
                "Solicitud aprobada por jefe ID: "
                        + solicitudId);
    }
    

    
    @Auditable(
            accion = "RECHAZAR_SOLICITUD_JEFE")
    public void rechazarSupervisor(Long solicitudId) {

        SolicitudRrhh solicitud =
                repository
                        .findById(solicitudId)
                        .orElseThrow(() ->
                                new NegocioException(
                                        "Solicitud no encontrada"));
        
        Long estadoOrigen =
                solicitud.getEstadoSolicitudId();

        EstadoSolicitud estadoActual =
                estadoSolicitudRepository
                        .findById(
                                solicitud.getEstadoSolicitudId())
                        .orElseThrow(() ->
                                new NegocioException(
                                        "Estado no encontrado"));

        if (!estadoActual.getCodigo()
                .equals("ENVIADO")) {

            throw new NegocioException(
                    "Solo solicitudes enviadas pueden rechazarse");
        }

        EstadoSolicitud estado =
                estadoSolicitudRepository
                        .findByCodigo(
                                "RECHAZADO_JEFE")
                        .orElseThrow(() ->
                                new NegocioException(
                                        "Estado no existe"));

        solicitud.setEstadoSolicitudId(
                estado.getId());

        repository.save(solicitud);
        registrarHistorial(
                solicitud.getId(),
                estadoOrigen,
                estado.getId(),
                "RECHAZAR_JEFE",
                "Solicitud rechazada por jefe");
    }
    
    @Auditable(
            accion = "APROBAR_SOLICITUD_RRHH")
    public void aprobarRrhh(
            Long solicitudId,
            MultipartFile file,
            String observacion) {

        SolicitudRrhh solicitud =
                repository
                        .findById(solicitudId)
                        .orElseThrow(() ->
                                new NegocioException(
                                        "Solicitud no encontrada"));

        Long estadoOrigen =
                solicitud.getEstadoSolicitudId();

        EstadoSolicitud estadoActual =
                estadoSolicitudRepository
                        .findById(
                                solicitud.getEstadoSolicitudId())
                        .orElseThrow(() ->
                                new NegocioException(
                                        "Estado no encontrado"));

        // ==========================================
        // VALIDAR ESTADO
        // ==========================================

        if (!estadoActual.getCodigo()
                .equals("APROBADO_JEFE")) {

            throw new NegocioException(
                    "Solo solicitudes aprobadas por jefe pueden aprobarse en RRHH");
        }
        
        //EN CASO VACACIONES VALIDAR SALDO VACACIONAL
        
        TipoSolicitudRrhh tipoSolicitud =
                tipoSolicitudRepository
                        .findById(
                                solicitud.getTipoSolicitudId())
                        .orElseThrow(() ->
                                new NegocioException(
                                        "Tipo solicitud no encontrado"));
        
      /*  if(TIPO_VACACIONES.equals(
                tipoSolicitud.getCodigo())) {

            SaldoVacacionalDto saldo =
                    vacacionService
                            .obtenerSaldoVacacional(
                                    solicitud.getEmpleadoId());

            BigDecimal solicitado =
                    BigDecimal.valueOf(
                            solicitud.getCantidadDias());

            if(solicitado.compareTo(saldo.getSaldo()) > 0) {

                throw new NegocioException(
                        "Saldo vacacional insuficiente. Disponible: "
                                + saldo.getSaldo());
            }
        }*/

        // ==========================================
        // VALIDAR ARCHIVO
        // ==========================================

       

        String rutaArchivo;
        String nombreArchivo;
        String mimeType;
        Long tamanioBytes;

        if (file == null || file.isEmpty()) {

            byte[] pdf =
                    generarPdfSinFirma(
                            "SOLICITUD NO REQUIERE FIRMA");

            rutaArchivo =
                    ftpService.subirPdfGenerado(
                            pdf,
                            "papeletas",
                            "solicitud_no_requiere_firma.pdf");

            nombreArchivo =
                    "solicitud_no_requiere_firma.pdf";

            mimeType =
                    "application/pdf";

            tamanioBytes =
                    (long) pdf.length;

        } else {

            rutaArchivo =
                    ftpService.subirArchivo(
                            file,
                            "papeletas",
                            file.getOriginalFilename());

            nombreArchivo =
                    file.getOriginalFilename();

            mimeType =
                    file.getContentType();

            tamanioBytes =
                    file.getSize();
        }

        // ==========================================
        // SUBIR FTP
        // ==========================================

       

        // ==========================================
        // GUARDAR DOCUMENTO
        // ==========================================

        SolicitudRrhhDoc doc =
                new SolicitudRrhhDoc();

        doc.setSolicitudId(
                solicitudId);

        doc.setEtapa(
                "RRHH");

        doc.setNombreArchivo(
                nombreArchivo);

        doc.setRutaArchivo(
                rutaArchivo);

        doc.setMimeType(
                mimeType);


        doc.setTamanioBytes(
                tamanioBytes);

        doc.setVersionDoc(3);

        doc.setObservacion(
                observacion);

        doc.setUsuarioUpload(
                "ADMIN");

        doc.setCreatedAt(
                LocalDateTime.now());

        doc.setActivo(1);

        solicitudRrhhDocRepository.save(doc);

        // ==========================================
        // BUSCAR ESTADO APROBADO_RRHH
        // ==========================================

        EstadoSolicitud estado =
                estadoSolicitudRepository
                        .findByCodigo(
                                "APROBADO_RRHH")
                        .orElseThrow(() ->
                                new NegocioException(
                                        "Estado no existe"));

        // ==========================================
        // ACTUALIZAR SOLICITUD
        // ==========================================

        solicitud.setEstadoSolicitudId(
                estado.getId());

        repository.save(solicitud);

        // ==========================================
        // HISTORIAL
        // ==========================================

        registrarHistorial(
                solicitud.getId(),
                estadoOrigen,
                estado.getId(),
                "APROBAR_RRHH",
                "Solicitud aprobada por RRHH");

        // ==========================================
        // AUDITORIA
        // ==========================================

        auditoriaContext.setDetalle(
                "Solicitud aprobada por RRHH ID: "
                        + solicitudId);
    }
    
    @Auditable(
            accion = "RECHAZAR_SOLICITUD_RRHH")
    public void rechazarRrhh(Long solicitudId) {

        SolicitudRrhh solicitud =
                repository
                        .findById(solicitudId)
                        .orElseThrow(() ->
                                new NegocioException(
                                        "Solicitud no encontrada"));

        EstadoSolicitud estadoActual =
                estadoSolicitudRepository
                        .findById(
                                solicitud.getEstadoSolicitudId())
                        .orElseThrow(() ->
                                new NegocioException(
                                        "Estado no encontrado"));

        // ==========================================
        // VALIDAR APROBADO_JEFE
        // ==========================================

        if (!estadoActual.getCodigo()
                .equals("APROBADO_JEFE")) {

            throw new NegocioException(
                    "Solo solicitudes aprobadas por jefe pueden rechazarse en RRHH");
        }

        // ==========================================
        // BUSCAR ESTADO RECHAZADO_RRHH
        // ==========================================

        EstadoSolicitud estado =
                estadoSolicitudRepository
                        .findByCodigo(
                                "RECHAZADO_RRHH")
                        .orElseThrow(() ->
                                new NegocioException(
                                        "Estado no existe"));

        solicitud.setEstadoSolicitudId(
                estado.getId());

        repository.save(solicitud);
    }
    
    @Auditable(
            accion = "EDITAR_SOLICITUD_RRHH")
    public void editar(
            Long solicitudId,
            SolicitudRrhhDto dto) {

        Long empleadoId =
                obtenerEmpleadoActual();

        SolicitudRrhh solicitud =
                repository
                        .findById(solicitudId)
                        .orElseThrow(() ->
                                new NegocioException(
                                        "Solicitud no encontrada"));
        
        if(!solicitud.getEmpleadoId()
                .equals(empleadoId)) {

            throw new NegocioException(
                    "No puede editar solicitudes de otro empleado");
        }
        

        
        EstadoSolicitud estado =
                estadoSolicitudRepository
                        .findById(
                                solicitud.getEstadoSolicitudId())
                        .orElseThrow(() ->
                                new NegocioException(
                                        "Estado no encontrado"));

        if (!"BORRADOR".equals(
                estado.getCodigo())) {

            throw new NegocioException(
                    "Solo solicitudes en borrador pueden editarse");
        }

        TipoSolicitudRrhh tipo =
                obtenerTipoSolicitud(
                        dto.getTipoSolicitudId());
        
       

        validarLactancia(
                dto,
                tipo);
        validarVacacion(
                dto,
                tipo);
        validarCompensacion(
                dto,
                tipo);
        validarHorasCompensacion(
                dto);
        
        validarDescansoMedico(
                dto,
                tipo);
        
        validarLicencia(
                dto,
                tipo);

        validarObservacion(
                dto,
                tipo);

        validarLugar(
                dto,
                tipo);

        validarFechas(
                dto,
                tipo);
        
        validarDuplicidadEditar(
                solicitudId,
                dto,
                empleadoId);

        validarHoras(
                dto,
                tipo);

        // OJO:
        // aquí luego debes corregir la validación de duplicidad
        // para excluir la propia solicitud.

        actualizarSolicitud(
                solicitud,
                dto);

        calcularCantidades(
                solicitud,
                dto,
                tipo);

        repository.save(
                solicitud);
        
        actualizarDetalleVacacion(
                solicitudId,
                dto);

        auditoriaContext.setDetalle(
                "Solicitud RRHH editada ID: "
                        + solicitudId);
    }
    
    private void validarHorasCompensacion(
            SolicitudRrhhDto dto) {

        double horasPermiso =
                dto.getCantidadHoras() == null
                        ? 0
                        : dto.getCantidadHoras();

        double horasCompensacion =
                dto.getDetallesCompensacion()
                        .stream()
                        .mapToDouble(
                                d -> d.getCantidadHoras() == null
                                        ? 0
                                        : d.getCantidadHoras())
                        .sum();

        if(Double.compare(
                horasPermiso,
                horasCompensacion) != 0) {

            throw new NegocioException(
                    "Las horas compensadas deben ser iguales a las horas solicitadas");
        }
    }
    private void actualizarDetalleVacacion(
            Long solicitudId,
            SolicitudRrhhDto dto) {

        solicitudVacacionDetRepository
                .deleteBySolicitudId(
                        solicitudId);

        guardarDetalleVacacion(
                solicitudId,
                dto);
    }
    
    private void validarDuplicidadEditar(
            Long solicitudId,
            SolicitudRrhhDto dto,
            Long empleadoId) {

        boolean existe =
                repository
                .existsByIdNotAndEmpleadoIdAndTipoSolicitudIdAndActivoAndFechaInicioLessThanEqualAndFechaFinGreaterThanEqual(
                        solicitudId,
                        empleadoId,
                        dto.getTipoSolicitudId(),
                        1,
                        dto.getFechaFin(),
                        dto.getFechaInicio());

        if(existe) {

            throw new NegocioException(
                    "El empleado ya tiene una solicitud de este tipo en esas fechas");
        }
    }
    
    private void actualizarSolicitud(
            SolicitudRrhh solicitud,
            SolicitudRrhhDto dto) {

        solicitud.setTipoSolicitudId(
                dto.getTipoSolicitudId());

        solicitud.setFechaInicio(
                dto.getFechaInicio());

        solicitud.setFechaFin(
                dto.getFechaFin());

        solicitud.setMotivo(
                dto.getMotivo());

        solicitud.setObservacion(
                dto.getObservacion());

        solicitud.setHoraInicio(
                dto.getHoraInicio());

        solicitud.setHoraFin(
                dto.getHoraFin());

        solicitud.setLugarComision(
                dto.getLugarComision());

        solicitud.setFechaNacimientoHijo(
                dto.getFechaNacimientoHijo());

        solicitud.setFechaFinPostnatal(
                dto.getFechaFinPostnatal());

        solicitud.setMinutosIngreso(
                dto.getMinutosIngreso());

        solicitud.setMinutosSalida(
                dto.getMinutosSalida());
        
        solicitud.setTipoDescansoMedicoId(
                dto.getTipoDescansoMedicoId());

        solicitud.setNombreMedico(
                dto.getNombreMedico());

        solicitud.setNumeroColegiatura(
                dto.getNumeroColegiatura());
        
        solicitud.setTipoLicenciaId(
                dto.getTipoLicenciaId());
        
        solicitud.setDocumento1(
                dto.getDocumento1());

        solicitud.setDocumento2(
                dto.getDocumento2());

        solicitud.setTotalFolios(
                dto.getTotalFolios());
        solicitud.setTipoVacacionId(
                dto.getTipoVacacionId());
    }
    
    @Auditable(
            accion = "ANULAR_SOLICITUD_RRHH")
    public void anular(Long solicitudId) {

        SolicitudRrhh solicitud =
                repository
                        .findById(solicitudId)
                        .orElseThrow(() ->
                                new NegocioException(
                                        "Solicitud no encontrada"));
        
        Long estadoOrigen =
                solicitud.getEstadoSolicitudId();

        EstadoSolicitud estadoActual =
                estadoSolicitudRepository
                        .findById(
                                solicitud.getEstadoSolicitudId())
                        .orElseThrow(() ->
                                new NegocioException(
                                        "Estado no encontrado"));

        // ==========================================
        // VALIDAR
        // ==========================================

        String codigo =
                estadoActual.getCodigo();

        if (codigo.equals("APROBADO_RRHH")) {

            throw new NegocioException(
                    "Solicitudes aprobadas por RRHH no pueden anularse");
        }

        // ==========================================
        // ESTADO ANULADO
        // ==========================================

        EstadoSolicitud estadoAnulado =
                estadoSolicitudRepository
                        .findByCodigo(
                                "ANULADO")
                        .orElseThrow(() ->
                                new NegocioException(
                                        "Estado ANULADO no existe"));

        solicitud.setEstadoSolicitudId(
                estadoAnulado.getId());

        repository.save(solicitud);
        registrarHistorial(
                solicitud.getId(),
                estadoOrigen,
                estadoAnulado.getId(),
                "ANULAR",
                "Solicitud anulada");
    }
    
    private void registrarHistorial(
            Long solicitudId,
            Long estadoOrigenId,
            Long estadoDestinoId,
            String accion,
            String observacion) {

        SolicitudRrhhHist hist =
                new SolicitudRrhhHist();

        hist.setSolicitudId(
                solicitudId);

        hist.setEstadoOrigenId(
                estadoOrigenId);

        hist.setEstadoDestinoId(
                estadoDestinoId);

        hist.setAccion(
                accion);

        hist.setObservacion(
                observacion);

        hist.setUsuario(
                "ADMIN");

        hist.setFecha(
                LocalDateTime.now());

        historialRepository.save(hist);
    }
    public List<SolicitudRrhhResponseDto>
    listarPorJefe(Long jefeId) {

        List<Long> empleadosIds =
                empleadoPuestoRepository
                        .findByJefeIdAndActivo(
                                jefeId,
                                1)
                        .stream()
                        .map(
                            EmpleadoPuesto::getEmpleadoId)
                        .toList();

        return repository
                .findByEmpleadoIdInAndActivo(
                        empleadosIds,
                        1)
                .stream()
                .map(this::convertir)
                .toList();
    }
    
    public List<SolicitudRrhhResponseDto>
    listarTodas() {

        return repository
                .findByActivo(1)
                .stream()
                .map(this::convertir)
                .toList();
    }
    
    public List<SolicitudRrhhResponseDto>
    listarMisSolicitudes() {

        Long empleadoId =
                SecurityUtil.getEmpleadoId();

        return repository
                .findByEmpleadoIdAndActivo(
                        empleadoId,
                        1)
                .stream()
                .map(this::convertir)
                .toList();
    }
    
    public List<SolicitudRrhhResponseDto>
    listarMisColaboradores() {

        Long jefeId =
                SecurityUtil.getEmpleadoId();

        List<Long> empleadosIds =
                empleadoPuestoRepository
                        .findByJefeIdAndActivo(
                                jefeId,
                                1)
                        .stream()
                        .map(
                            EmpleadoPuesto::getEmpleadoId)
                        .toList();

        return repository
                .findByEmpleadoIdInAndActivo(
                        empleadosIds,
                        1)
                .stream()
                .map(this::convertir)
                .toList();
    }
    
    private byte[] generarPdfSinFirma(
            String mensaje) {

        try {

            ByteArrayOutputStream baos =
                    new ByteArrayOutputStream();

            Document document =
                    new Document();

            PdfWriter.getInstance(
                    document,
                    baos);

            document.open();

            document.add(
                    new Paragraph(
                            mensaje));

            document.close();

            return baos.toByteArray();

        } catch (Exception e) {

            throw new RuntimeException(
                    "Error generando PDF",
                    e);
        }
    }
   
    
}