package com.indeci.rrhh.service;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.SaldoVacacionalDto;
import com.indeci.rrhh.dto.SolicitudRrhhDto;
import com.indeci.rrhh.dto.SolicitudRrhhResponseDto;
import com.indeci.rrhh.dto.SolicitudWorkflowDocumentoDto;
import com.indeci.rrhh.entity.*;

import com.indeci.rrhh.repository.*;
import com.indeci.security.util.SecurityUtil;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import com.indeci.audit.annotation.Auditable;
import com.indeci.audit.context.AuditoriaContext;

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
    
    private static final String TIPO_VACACIONES = "VAC";
    
    @Auditable(
            accion = "CREAR_SOLICITUD_RRHH")
    public void registrar(
            SolicitudRrhhDto dto,
            MultipartFile sustento) {

    	Long empleadoId =
    	        obtenerEmpleadoActual();
        TipoSolicitudRrhh tipo =
                obtenerTipoSolicitud(
                        dto.getTipoSolicitudId());

        validarSolicitud(
                dto,
                tipo,
                empleadoId,
                sustento);

        EstadoSolicitud estadoBorrador =
                obtenerEstadoBorrador();

        SolicitudRrhh entity =
                construirSolicitud(
                        dto,
                        empleadoId,
                        estadoBorrador,
                        tipo);

        repository.save(entity);

        guardarSustento(
                entity,
                sustento);

        registrarHistorial(
                entity.getId(),
                null,
                entity.getEstadoSolicitudId(),
                "CREAR",
                "Solicitud creada");
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
                            "sustentos",
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
            MultipartFile sustento) {

        validarLactancia(
                dto,
                tipo);

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

        if(tipo.getRequiereSustento() == 1) {

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

        if(tipo.getRequiereLugar() == 1) {

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

        if(tipo.getRequiereObservacion() == 1) {

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
                            "sustentos",
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

        // ==========================================
        // VALIDAR ARCHIVO
        // ==========================================

        if (file == null
                || file.isEmpty()) {

            throw new NegocioException(
                    "Documento firmado es obligatorio");
        }

        // ==========================================
        // VALIDAR BORRADOR
        // ==========================================

        if (!estadoActual.getCodigo()
                .equals("BORRADOR")) {

            throw new NegocioException(
                    "Solo solicitudes en borrador pueden enviarse");
        }

        // ==========================================
        // SUBIR FTP
        // ==========================================

        String rutaArchivo =
                ftpService.subirArchivo(
                        file,
                        "papeletas",
                        file.getOriginalFilename());

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
                file.getOriginalFilename());

        doc.setRutaArchivo(
                rutaArchivo);

        doc.setMimeType(
                file.getContentType());

        doc.setTamanioBytes(
                file.getSize());

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

        if (file == null
                || file.isEmpty()) {

            throw new NegocioException(
                    "Documento firmado por jefe es obligatorio");
        }

        // ==========================================
        // SUBIR FTP
        // ==========================================

        String rutaArchivo =
                ftpService.subirArchivo(
                        file,
                        "papeletas",
                        file.getOriginalFilename());

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
                file.getOriginalFilename());

        doc.setRutaArchivo(
                rutaArchivo);

        doc.setMimeType(
                file.getContentType());

        doc.setTamanioBytes(
                file.getSize());

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
        
        if(TIPO_VACACIONES.equals(
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
        }

        // ==========================================
        // VALIDAR ARCHIVO
        // ==========================================

        if (file == null
                || file.isEmpty()) {

            throw new NegocioException(
                    "Documento firmado por RRHH es obligatorio");
        }

        // ==========================================
        // SUBIR FTP
        // ==========================================

        String rutaArchivo =
                ftpService.subirArchivo(
                        file,
                        "papeletas",
                        file.getOriginalFilename());

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
                file.getOriginalFilename());

        doc.setRutaArchivo(
                rutaArchivo);

        doc.setMimeType(
                file.getContentType());

        doc.setTamanioBytes(
                file.getSize());

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

        auditoriaContext.setDetalle(
                "Solicitud RRHH editada ID: "
                        + solicitudId);
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
    
}