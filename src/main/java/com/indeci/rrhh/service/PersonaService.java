package com.indeci.rrhh.service;

import com.indeci.audit.annotation.Auditable;
import com.indeci.audit.context.AuditoriaContext;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.PersonaEmpleadoDto;
import com.indeci.rrhh.dto.PersonaEmpleadoResponseDto;
import com.indeci.rrhh.entity.Persona;
import com.indeci.rrhh.entity.Profesion;
import com.indeci.rrhh.entity.Empleado;
import com.indeci.rrhh.repository.PersonaRepository;
import com.indeci.rrhh.repository.ProfesionRepository;
import com.indeci.rrhh.repository.EmpleadoRepository;
import com.indeci.rrhh.entity.EstadoCivil;
import com.indeci.rrhh.entity.GradoAcademico;
import com.indeci.rrhh.entity.Sexo;
import com.indeci.rrhh.entity.TipoDocumento;
import com.indeci.rrhh.entity.TipoPersonal;

import com.indeci.rrhh.repository.EstadoCivilRepository;
import com.indeci.rrhh.repository.GradoAcademicoRepository;
import com.indeci.rrhh.repository.SexoRepository;
import com.indeci.rrhh.repository.TipoDocumentoRepository;
import com.indeci.rrhh.repository.TipoPersonalRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PersonaService {

    private final PersonaRepository personaRepository;
    private final EmpleadoRepository empleadoRepository;
    private final AuditoriaContext auditoriaContext;
    private final SexoRepository sexoRepository;
    private final ProfesionRepository profesionRepository;
    private final GradoAcademicoRepository gradoAcademicoRepository;

    private final EstadoCivilRepository estadoCivilRepository;

    private final TipoDocumentoRepository tipoDocumentoRepository;

    private final TipoPersonalRepository tipoPersonalRepository;

    // ============================
    // CREAR
    // ============================
    @Auditable(accion = "CREAR_PERSONA_EMPLEADO")
    public void guardar(PersonaEmpleadoDto dto) {

        // 🔥 VALIDACIONES
        if (dto.getDni() == null || dto.getDni().isBlank()) {
            auditoriaContext.setDetalle("Intento sin DNI");
            throw new NegocioException("El DNI es obligatorio");
        }
        if (!dto.getDni().matches("\\d{8}")) {
            throw new NegocioException("El DNI debe tener 8 dígitos");
        }

        if (personaRepository.existsByDni(dto.getDni())) {
            auditoriaContext.setDetalle("DNI duplicado: " + dto.getDni());
            throw new NegocioException("El DNI ya está registrado");
        }

        if (dto.getEmail() != null && personaRepository.existsByEmail(dto.getEmail())) {
            auditoriaContext.setDetalle("Email duplicado: " + dto.getEmail());
            throw new NegocioException("El email ya está registrado");
        }

        // ============================
        // PERSONA
        // ============================
        Persona persona = new Persona();
        persona.setNombreCompleto(dto.getNombreCompleto());
        persona.setDni(dto.getDni());
        if (dto.getEmail() != null
                && !dto.getEmail().equals(persona.getEmail())
                && personaRepository.existsByEmail(dto.getEmail())) {

            throw new NegocioException(
                    "El email ya está registrado");
        }
        persona.setEmail(dto.getEmail());
        persona.setTelefono(dto.getTelefono());
        persona.setDireccion(dto.getDireccion());
        persona.setDistritoId(dto.getDistritoId());
        persona.setCreatedAt(LocalDateTime.now());
        persona.setSexoId(dto.getSexoId());
        persona.setEstadoCivilId(dto.getEstadoCivilId());
        persona.setTipoDocumentoId(dto.getTipoDocumentoId());
    

        persona.setNacionalidad(dto.getNacionalidad());
        persona.setRuc(dto.getRuc());
        persona.setCorreoInstitucional(dto.getCorreoInstitucional());
        persona.setFotoPerfil(dto.getFotoPerfil());
        
  
        persona.setContactoEmergenciaNombre(dto.getContactoEmergenciaNombre());
        persona.setContactoEmergenciaParentesco(dto.getContactoEmergenciaParentesco());
        persona.setContactoEmergenciaTelefono(dto.getContactoEmergenciaTelefono());
        
  
       

        persona = personaRepository.save(persona);

        // ============================
        // EMPLEADO
        // ============================
        Empleado empleado = new Empleado();
        empleado.setPersonaId(persona.getId());
        empleado.setEstado(dto.getEstado());
        empleado.setCreatedAt(LocalDateTime.now());
        empleado.setTipoPersonalId(dto.getTipoPersonalId());
        
        empleado.setProfesionId(dto.getProfesionId());
        empleado.setGradoAcademicoId(dto.getGradoAcademicoId());
        empleado.setConadisCodigo(dto.getConadisCodigo());

        empleado = empleadoRepository.save(empleado);

        // 🔥 CODIGO INTERNO SEGURO
        String codigo = String.format("EMP%05d", empleado.getId());
        empleado.setCodigoInterno(codigo);

        empleadoRepository.save(empleado);

        auditoriaContext.setDetalle("Creación persona DNI: " + dto.getDni());
    }

    // ============================
    // LISTAR
    // ============================
    public List<PersonaEmpleadoResponseDto> listar() {

    	return personaRepository.findAll()
    	        .stream()
    	        .map(p -> {

    	            Empleado emp =
    	                    empleadoRepository
    	                            .findByPersonaId(
    	                                    p.getId())
    	                            .orElse(null);

    	            return mapearPersona(
    	                    p,
    	                    emp);

    	        }).toList();
    }

    // ============================
    // OBTENER
    // ============================
    public PersonaEmpleadoResponseDto
    obtenerPorId(Long id) {

        Persona p =
                personaRepository.findById(id)
                        .orElseThrow(() ->
                                new NegocioException(
                                        "Persona no encontrada"));

        Empleado emp =
                empleadoRepository
                        .findByPersonaId(id)
                        .orElse(null);

        return mapearPersona(
                p,
                emp);
    }

    // ============================
    // ACTUALIZAR
    // ============================
    @Auditable(accion = "ACTUALIZAR_PERSONA")
    public void actualizar(Long id, PersonaEmpleadoDto dto) {

        Persona persona = personaRepository.findById(id)
                .orElseThrow(() -> new NegocioException("Persona no encontrada"));

        persona.setNombreCompleto(dto.getNombreCompleto());
        persona.setEmail(dto.getEmail());
        persona.setTelefono(dto.getTelefono());
        persona.setDireccion(dto.getDireccion());
        persona.setDistritoId(dto.getDistritoId());
        persona.setSexoId(
                dto.getSexoId());

        persona.setEstadoCivilId(
                dto.getEstadoCivilId());

        persona.setTipoDocumentoId(
                dto.getTipoDocumentoId());

        persona.setNacionalidad(
                dto.getNacionalidad());

        persona.setRuc(
                dto.getRuc());

        persona.setCorreoInstitucional(
                dto.getCorreoInstitucional());

        persona.setFotoPerfil(
                dto.getFotoPerfil());

        persona.setContactoEmergenciaNombre(
                dto.getContactoEmergenciaNombre());

        persona.setContactoEmergenciaParentesco(
                dto.getContactoEmergenciaParentesco());

        persona.setContactoEmergenciaTelefono(
                dto.getContactoEmergenciaTelefono());

        personaRepository.save(persona);

        Empleado emp = empleadoRepository.findByPersonaId(id)
                .orElseThrow(() -> new NegocioException("Empleado no encontrado"));

        emp.setEstado(dto.getEstado());

        empleadoRepository.save(emp);
        emp.setProfesionId(
                dto.getProfesionId());

        emp.setGradoAcademicoId(
                dto.getGradoAcademicoId());

        emp.setConadisCodigo(
                dto.getConadisCodigo());

        emp.setTipoPersonalId(
                dto.getTipoPersonalId());

        auditoriaContext.setDetalle("Actualización persona ID: " + id);
    }

    // ============================
    // ELIMINAR
    // ============================
    @Auditable(accion = "ELIMINAR_PERSONA")
    public void eliminar(Long id) {

        Empleado emp = empleadoRepository.findByPersonaId(id)
                .orElseThrow(() -> new NegocioException("Empleado no encontrado"));

        emp.setEstado("INACTIVO");

        empleadoRepository.save(emp);

        auditoriaContext.setDetalle("Eliminación lógica persona ID: " + id);
    }
    
    private PersonaEmpleadoResponseDto
    mapearPersona(Persona p, Empleado emp) {

        PersonaEmpleadoResponseDto dto =
                new PersonaEmpleadoResponseDto();

        dto.setId(p.getId());

        dto.setNombreCompleto(
                p.getNombreCompleto());

        dto.setDni(
                p.getDni());

        dto.setEmail(
                p.getEmail());

        dto.setTelefono(
                p.getTelefono());

        dto.setDireccion(
                p.getDireccion());

        dto.setDistritoId(
                p.getDistritoId());

        dto.setContactoEmergenciaNombre(
                p.getContactoEmergenciaNombre());

        dto.setContactoEmergenciaParentesco(
                p.getContactoEmergenciaParentesco());

        dto.setContactoEmergenciaTelefono(
                p.getContactoEmergenciaTelefono());

        dto.setSexoId(
                p.getSexoId());

        dto.setEstadoCivilId(
                p.getEstadoCivilId());

        dto.setTipoDocumentoId(
                p.getTipoDocumentoId());

        dto.setNacionalidad(
                p.getNacionalidad());

        dto.setRuc(
                p.getRuc());

        dto.setCorreoInstitucional(
                p.getCorreoInstitucional());

        dto.setFotoPerfil(
                p.getFotoPerfil());

        // ======================================
        // CATALOGOS
        // ======================================

        if (p.getSexoId() != null) {

            Sexo sexo =
                    sexoRepository
                            .findById(
                                    p.getSexoId())
                            .orElse(null);

            if (sexo != null) {
                dto.setSexo(
                        sexo.getNombre());
            }
        }

        if (p.getEstadoCivilId() != null) {

            EstadoCivil estadoCivil =
                    estadoCivilRepository
                            .findById(
                                    p.getEstadoCivilId())
                            .orElse(null);

            if (estadoCivil != null) {
                dto.setEstadoCivil(
                        estadoCivil.getNombre());
            }
        }

        if (p.getTipoDocumentoId() != null) {

            TipoDocumento tipoDocumento =
                    tipoDocumentoRepository
                            .findById(
                                    p.getTipoDocumentoId())
                            .orElse(null);

            if (tipoDocumento != null) {
                dto.setTipoDocumento(
                        tipoDocumento.getNombre());
            }
        }

        if (emp != null) {

            dto.setCodigoInterno(
                    emp.getCodigoInterno());

            dto.setEstado(
                    emp.getEstado());

            dto.setProfesionId(
                    emp.getProfesionId());

            dto.setGradoAcademicoId(
                    emp.getGradoAcademicoId());
            if (emp.getProfesionId() != null) {

                Profesion profesion =
                        profesionRepository
                                .findById(
                                        emp.getProfesionId())
                                .orElse(null);

                if (profesion != null) {

                    dto.setProfesion(
                            profesion.getNombre());
                }
            }

            if (emp.getGradoAcademicoId() != null) {

                GradoAcademico grado =
                        gradoAcademicoRepository
                                .findById(
                                        emp.getGradoAcademicoId())
                                .orElse(null);

                if (grado != null) {

                    dto.setGradoAcademico(
                            grado.getNombre());
                }
            }

            dto.setConadisCodigo(
                    emp.getConadisCodigo());

            dto.setTipoPersonalId(
                    emp.getTipoPersonalId());

            if (emp.getTipoPersonalId() != null) {

                TipoPersonal tipoPersonal =
                        tipoPersonalRepository
                                .findById(
                                        emp.getTipoPersonalId())
                                .orElse(null);

                if (tipoPersonal != null) {

                    dto.setTipoPersonal(
                            tipoPersonal.getNombre());
                }
            }
        }

        return dto;
    }
}