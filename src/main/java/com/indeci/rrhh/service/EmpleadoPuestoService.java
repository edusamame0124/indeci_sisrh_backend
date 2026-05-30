package com.indeci.rrhh.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.indeci.audit.annotation.Auditable;
import com.indeci.audit.context.AuditoriaContext;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.EmpleadoPuestoDto;
import com.indeci.rrhh.dto.EmpleadoPuestoResponseDto;
import com.indeci.rrhh.entity.Cargo;

import com.indeci.rrhh.entity.Empleado;
import com.indeci.rrhh.entity.EmpleadoPuesto;

import com.indeci.rrhh.entity.Nivel;
import com.indeci.rrhh.entity.Oficina;
import com.indeci.rrhh.entity.Persona;

import com.indeci.rrhh.entity.TipoCargo;
import com.indeci.rrhh.repository.CargoRepository;

import com.indeci.rrhh.repository.EmpleadoPuestoRepository;
import com.indeci.rrhh.repository.EmpleadoRepository;

import com.indeci.rrhh.repository.NivelRepository;
import com.indeci.rrhh.repository.PersonaRepository;

import com.indeci.rrhh.repository.TipoCargoRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmpleadoPuestoService {

    private final EmpleadoPuestoRepository repository;
    private final AuditoriaContext auditoriaContext;
    private final NivelRepository nivelRepository;




    private final EmpleadoRepository
            empleadoRepository;

    private final PersonaRepository
            personaRepository;
    private final CargoRepository cargoRepository;
    
    private final TipoCargoRepository tipoCargoRepository;

    // ============================
    // CREAR NUEVO PUESTO (MOVIMIENTO)
    // ============================
    @Auditable(accion = "CAMBIO_PUESTO")
    public void guardar(EmpleadoPuestoDto dto) {

        // 🔥 VALIDAR
     	if (dto.getCargoId() == null) {

    	    throw new NegocioException(
    	            "Debe indicar el cargo");
    	}
     
        // 🔥 CERRAR PUESTO ACTUAL
        Optional<EmpleadoPuesto> actual =
                repository.findFirstByEmpleadoIdAndActivo(dto.getEmpleadoId(), 1);

        if (actual.isPresent()) {
            EmpleadoPuesto puestoActual = actual.get();
            puestoActual.setActivo(0);
            puestoActual.setFechaFin(LocalDate.now());

            repository.save(puestoActual);
        }
        
        cargoRepository
        .findById(
                dto.getCargoId())
        .orElseThrow(() ->
                new NegocioException(
                        "Cargo no encontrado"));

        // 🔥 CREAR NUEVO
        EmpleadoPuesto nuevo = new EmpleadoPuesto();
        nuevo.setEmpleadoId(dto.getEmpleadoId());
        nuevo.setCargoId(dto.getCargoId());
        nuevo.setNivelId(dto.getNivelId());
   
        nuevo.setOficinaId(dto.getOficinaId());
 

   
        nuevo.setJefeId(dto.getJefeId());
        nuevo.setActivo(1);
        nuevo.setFechaInicio(LocalDate.now());
        nuevo.setCreatedAt(LocalDateTime.now());
        

        repository.save(nuevo);

        auditoriaContext.setDetalle("Cambio de puesto empleado: " + dto.getEmpleadoId());
    }

    // ============================
    // ACTUALIZAR PUESTO VIGENTE (sin nuevo movimiento)
    // ============================
    @Auditable(accion = "ACTUALIZAR_PUESTO")
    public void actualizar(Long id, EmpleadoPuestoDto dto) {

    	if (dto.getCargoId() == null) {

    	    throw new NegocioException(
    	            "Debe indicar el cargo");
    	}
        EmpleadoPuesto entity = repository.findById(id)
                .orElseThrow(() -> new NegocioException("Puesto no encontrado"));

        if (entity.getActivo() == null || entity.getActivo() != 1) {
            throw new NegocioException("Solo puede editarse el puesto vigente");
        }

        if (!entity.getEmpleadoId().equals(dto.getEmpleadoId())) {
            throw new NegocioException("El puesto no corresponde al empleado indicado");
        }

        cargoRepository
        .findById(
                dto.getCargoId())
        .orElseThrow(() ->
                new NegocioException(
                        "Cargo no encontrado"));
        
        entity.setCargoId(dto.getCargoId());
        entity.setNivelId(dto.getNivelId());
      
        entity.setOficinaId(dto.getOficinaId());
     
    
        entity.setJefeId(dto.getJefeId());

        repository.save(entity);

        auditoriaContext.setDetalle("Puesto actualizado ID: " + id);
    }

    // ============================
    // LISTAR HISTORIAL
    // ============================
    public List<EmpleadoPuestoResponseDto> listar(Long empleadoId) {

        return repository.findByEmpleadoIdOrderByFechaInicioDesc(empleadoId)
                .stream()
                .map(e -> {
                    EmpleadoPuestoResponseDto dto = new EmpleadoPuestoResponseDto();
                    dto.setId(e.getId());

                    dto.setCargoId(
                            e.getCargoId());
                    
                    
                    if (e.getCargo() != null) {

                        Cargo cargo =
                                e.getCargo();

                        dto.setCargoId(
                                cargo.getId());

                        dto.setCargo(
                                cargo.getNombre());

                        if (cargo.getTipoCargo() != null) {

                            dto.setTipoCargoId(
                                    cargo.getTipoCargo().getId());

                            dto.setTipoCargo(
                                    cargo.getTipoCargo().getNombre());
                        }
                    }
                    
                    
                    
                    dto.setNivelId(e.getNivelId());
                    
                    if (e.getNivelId() != null) {

                        Nivel nivel =
                                nivelRepository
                                        .findById(
                                                e.getNivelId())
                                        .orElse(null);

                        if (nivel != null) {

                            dto.setNivel(
                                    nivel.getNombre());
                        }
                    }
                
                    dto.setOficinaId(e.getOficinaId());
                    if (e.getOficinaId() != null) {

                    	Oficina oficina =
                    	        e.getOficina();

                    	if (oficina != null) {

                    	    dto.setOficina(
                    	            oficina.getNombre());

                    	    if (oficina.getSede() != null) {

                    	        dto.setSede(
                    	                oficina
                    	                        .getSede()
                    	                        .getNombre());
                    	    }

                    	    if (oficina.getEstructuraOrganica() != null) {

                    	        dto.setEstructuraOrganica(
                    	                oficina
                    	                        .getEstructuraOrganica()
                    	                        .getNombre());
                    	    }
                    	}
                        
                    }
                    dto.setJefeId(e.getJefeId());
                    if (e.getJefeId() != null) {

                        Empleado jefe =
                                empleadoRepository
                                        .findById(
                                                e.getJefeId())
                                        .orElse(null);

                        if (jefe != null) {

                            Persona personaJefe =
                                    personaRepository
                                            .findById(
                                                    jefe.getPersonaId())
                                            .orElse(null);

                            if (personaJefe != null) {

                                dto.setJefe(
                                        personaJefe
                                                .getNombreCompleto());
                            }
                        }
                    }
                    dto.setActivo(e.getActivo());
            

                
                    return dto;
                }).toList();
    }

    // ============================
    // ELIMINAR (NO SE RECOMIENDA)
    // ============================
    @Auditable(accion = "ELIMINAR_PUESTO")
    public void eliminar(Long id) {

        EmpleadoPuesto entity = repository.findById(id)
                .orElseThrow(() -> new NegocioException("Puesto no encontrado"));

        entity.setActivo(0);
        entity.setFechaFin(LocalDate.now());

        repository.save(entity);

        auditoriaContext.setDetalle("Puesto desactivado ID: " + id);
    }
    
    
}