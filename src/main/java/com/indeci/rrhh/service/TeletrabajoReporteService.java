package com.indeci.rrhh.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.TeletrabajoReporteCabeceraDto;
import com.indeci.rrhh.dto.TeletrabajoReporteDetDto;
import com.indeci.rrhh.dto.TeletrabajoReporteDetResponseDto;
import com.indeci.rrhh.dto.TeletrabajoReporteDto;
import com.indeci.rrhh.dto.TeletrabajoReporteResponseDto;
import com.indeci.rrhh.entity.TeletrabajoReporte;
import com.indeci.rrhh.entity.TeletrabajoReporteDet;
import com.indeci.rrhh.repository.EmpleadoRepository;
import com.indeci.rrhh.repository.TeletrabajoReporteDetRepository;
import com.indeci.rrhh.repository.TeletrabajoReporteRepository;
import com.indeci.rrhh.entity.Empleado;
import com.indeci.rrhh.entity.Persona;
import com.indeci.rrhh.repository.PersonaRepository;

import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TeletrabajoReporteService {

    private final TeletrabajoReporteRepository
            reporteRepository;

    private final TeletrabajoReporteDetRepository
            detalleRepository;

    private final EmpleadoRepository
            empleadoRepository;
    
    private final PersonaRepository personaRepository;
    
    @Transactional
    public void registrar(
            TeletrabajoReporteDto dto) {

        empleadoRepository
                .findById(
                        dto.getEmpleadoId())
                .orElseThrow(() ->
                new NegocioException(
                        "Persona no encontrada"));

        TeletrabajoReporte reporte =
                new TeletrabajoReporte();

        reporte.setEmpleadoId(
                dto.getEmpleadoId());

        reporte.setMes(
                dto.getMes());

        reporte.setAnio(
                dto.getAnio());

        reporte.setModalidadId(
                dto.getModalidadId());

        reporte.setFechaReporte(
                dto.getFechaReporte());

        reporte.setEstado(
                "BORRADOR");

        reporte.setActivo(1);

        reporte.setCreatedAt(
                LocalDateTime.now());

        reporte =
                reporteRepository.save(
                        reporte);

        int orden = 1;

        for(TeletrabajoReporteDetDto det :
                dto.getDetalles()) {

            TeletrabajoReporteDet detalle =
                    new TeletrabajoReporteDet();

            detalle.setReporteId(
                    reporte.getId());

            detalle.setNroOrden(
                    orden++);

            detalle.setActividadProgramada(
                    det.getActividadProgramada());

            detalle.setActividadEjecutada(
                    det.getActividadEjecutada());

            detalle.setMedioVerificacion(
                    det.getMedioVerificacion());

            detalle.setFechaInicio(
                    det.getFechaInicio());

            detalle.setFechaFin(
                    det.getFechaFin());

            detalle.setEstadoCumplimientoId(
                    det.getEstadoCumplimientoId());

            detalle.setPorcentajeAvance(
                    det.getPorcentajeAvance());

            detalle.setIncidenciaObservacion(
                    det.getIncidenciaObservacion());

            detalle.setConformidadId(
                    det.getConformidadId());

            detalle.setActivo(1);

            detalle.setCreatedAt(
                    LocalDateTime.now());

            detalleRepository.save(
                    detalle);
        }
    }
    
    @Transactional(readOnly = true)
    public List<TeletrabajoReporteResponseDto>
    listar() {

        return reporteRepository
                .findByActivoOrderByIdDesc(1)
                .stream()
                .map(this::toDto)
                .toList();
    }
    
    @Transactional(readOnly = true)
    public TeletrabajoReporteResponseDto
    obtener(
            Long id) {

        TeletrabajoReporte reporte =
                reporteRepository
                        .findById(id)
                        .orElseThrow(() ->
                                new NegocioException(
                                        "Reporte no encontrado"));

        return toDto(reporte);
    }
    
    @Transactional
    public void eliminar(
            Long id) {

        TeletrabajoReporte reporte =
                reporteRepository
                        .findById(id)
                        .orElseThrow(() ->
                                new NegocioException(
                                        "Reporte no encontrado"));

        reporte.setActivo(0);

        reporteRepository.save(
                reporte);
    }
    
    private TeletrabajoReporteResponseDto
    toDto(
            TeletrabajoReporte reporte) {

        TeletrabajoReporteResponseDto dto =
                new TeletrabajoReporteResponseDto();

        dto.setId(
                reporte.getId());

        dto.setEmpleadoId(
                reporte.getEmpleadoId());
        Empleado empleado = empleadoRepository
                .findById(reporte.getEmpleadoId())
                .orElse(null);

        if (empleado != null) {

            dto.setCodigoInterno(
                    empleado.getCodigoInterno());

            if (empleado.getPersonaId() != null) {

                Persona persona = personaRepository
                        .findById(empleado.getPersonaId())
                        .orElse(null);

                if (persona != null) {

                    dto.setTrabajador(
                            persona.getNombreCompleto());

                    dto.setDni(
                            persona.getDni());
                }
            }
        }
        dto.setMes(
                reporte.getMes());

        dto.setAnio(
                reporte.getAnio());

        dto.setModalidadId(
                reporte.getModalidadId());

        if(reporte.getModalidad() != null) {

            dto.setModalidad(
                    reporte.getModalidad()
                            .getNombre());
        }

        dto.setFechaReporte(
                reporte.getFechaReporte());

        dto.setEstado(
                reporte.getEstado());

        dto.setDetalles(
                detalleRepository
                        .findByReporteIdAndActivoOrderByNroOrdenAsc(
                                reporte.getId(),
                                1)
                        .stream()
                        .map(this::toDetalleDto)
                        .toList());

        return dto;
    }
    
    private TeletrabajoReporteDetResponseDto
    toDetalleDto(
            TeletrabajoReporteDet det) {

        TeletrabajoReporteDetResponseDto dto =
                new TeletrabajoReporteDetResponseDto();

        dto.setId(
                det.getId());

        dto.setNroOrden(
                det.getNroOrden());

        dto.setActividadProgramada(
                det.getActividadProgramada());

        dto.setActividadEjecutada(
                det.getActividadEjecutada());

        dto.setMedioVerificacion(
                det.getMedioVerificacion());

        dto.setFechaInicio(
                det.getFechaInicio());

        dto.setFechaFin(
                det.getFechaFin());

        dto.setEstadoCumplimientoId(
                det.getEstadoCumplimientoId());

        if(det.getEstadoCumplimiento() != null) {

            dto.setEstadoCumplimiento(
                    det.getEstadoCumplimiento()
                            .getNombre());
        }

        dto.setPorcentajeAvance(
                det.getPorcentajeAvance());

        dto.setIncidenciaObservacion(
                det.getIncidenciaObservacion());

        dto.setConformidadId(
                det.getConformidadId());

        if(det.getConformidad() != null) {

            dto.setConformidad(
                    det.getConformidad()
                            .getNombre());
        }

        return dto;
    }
    @Transactional
    public void agregarDetalle(
            TeletrabajoReporteDetDto dto) {

        reporteRepository
                .findById(
                        dto.getReporteId())
                .orElseThrow(() ->
                        new NegocioException(
                                "Reporte no encontrado"));

        Integer orden =
                detalleRepository
                        .countByReporteId(
                                dto.getReporteId())
                        + 1;

        TeletrabajoReporteDet detalle =
                new TeletrabajoReporteDet();

        detalle.setReporteId(
                dto.getReporteId());

        detalle.setNroOrden(
                orden);

        detalle.setActividadProgramada(
                dto.getActividadProgramada());

        detalle.setActividadEjecutada(
                dto.getActividadEjecutada());

        detalle.setMedioVerificacion(
                dto.getMedioVerificacion());

        detalle.setFechaInicio(
                dto.getFechaInicio());

        detalle.setFechaFin(
                dto.getFechaFin());

        detalle.setEstadoCumplimientoId(
                dto.getEstadoCumplimientoId());

        detalle.setPorcentajeAvance(
                dto.getPorcentajeAvance());

        detalle.setIncidenciaObservacion(
                dto.getIncidenciaObservacion());

        detalle.setConformidadId(
                dto.getConformidadId());

        detalle.setActivo(1);

        detalle.setCreatedAt(
                LocalDateTime.now());

        detalleRepository.save(
                detalle);
    }
    
    @Transactional
    public void actualizarDetalle(
            Long detalleId,
            TeletrabajoReporteDetDto dto) {

        TeletrabajoReporteDet detalle =
                detalleRepository
                        .findById(
                                detalleId)
                        .orElseThrow(() ->
                                new NegocioException(
                                        "Detalle no encontrado"));

        detalle.setActividadProgramada(
                dto.getActividadProgramada());

        detalle.setActividadEjecutada(
                dto.getActividadEjecutada());

        detalle.setMedioVerificacion(
                dto.getMedioVerificacion());

        detalle.setFechaInicio(
                dto.getFechaInicio());

        detalle.setFechaFin(
                dto.getFechaFin());

        detalle.setEstadoCumplimientoId(
                dto.getEstadoCumplimientoId());

        detalle.setPorcentajeAvance(
                dto.getPorcentajeAvance());

        detalle.setIncidenciaObservacion(
                dto.getIncidenciaObservacion());

        detalle.setConformidadId(
                dto.getConformidadId());

        detalleRepository.save(
                detalle);
    }
    
    @Transactional
    public void eliminarDetalle(
            Long detalleId) {

        TeletrabajoReporteDet detalle =
                detalleRepository
                        .findById(
                                detalleId)
                        .orElseThrow(() ->
                                new NegocioException(
                                        "Detalle no encontrado"));

        detalle.setActivo(0);

        detalleRepository.save(
                detalle);
    }
    
    @Transactional
    public Long registrarCabecera(
            TeletrabajoReporteCabeceraDto dto) {

        TeletrabajoReporte reporte =
                new TeletrabajoReporte();

        reporte.setEmpleadoId(
                dto.getEmpleadoId());

        reporte.setMes(
                dto.getMes());

        reporte.setAnio(
                dto.getAnio());

        reporte.setModalidadId(
                dto.getModalidadId());

        reporte.setFechaReporte(
                dto.getFechaReporte());

        reporte.setEstado(
                "BORRADOR");

        reporte.setActivo(1);

        reporte.setCreatedAt(
                LocalDateTime.now());

        return reporteRepository
                .save(reporte)
                .getId();
    }
    
}