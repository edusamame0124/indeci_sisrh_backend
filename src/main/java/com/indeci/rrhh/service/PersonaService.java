package com.indeci.rrhh.service;

import com.indeci.audit.annotation.Auditable;
import com.indeci.audit.context.AuditoriaContext;
import com.indeci.exception.NegocioException;
import com.indeci.rrhh.dto.PersonaEmpleadoDto;
import com.indeci.rrhh.dto.PersonaEmpleadoResponseDto;
import com.indeci.rrhh.entity.Persona;
import com.indeci.rrhh.entity.Empleado;
import com.indeci.rrhh.repository.PersonaRepository;
import com.indeci.rrhh.repository.EmpleadoRepository;

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

    // ============================
    // CREAR
    // ============================
    @Auditable(accion = "CREAR_PERSONA_EMPLEADO")
    public void guardar(PersonaEmpleadoDto dto) {

        if (dto.getDni() == null || dto.getDni().isBlank()) {
            auditoriaContext.setDetalle("Intento sin DNI");
            throw new NegocioException("El DNI es obligatorio");
        }

        Persona persona = new Persona();
        persona.setNombreCompleto(dto.getNombreCompleto());
        persona.setDni(dto.getDni());
        persona.setEmail(dto.getEmail());
        persona.setTelefono(dto.getTelefono());
        persona.setDireccion(dto.getDireccion());
        persona.setDistritoId(dto.getDistritoId());
        persona.setCreatedAt(LocalDateTime.now());

        persona = personaRepository.save(persona);

        Empleado empleado = new Empleado();
        empleado.setPersonaId(persona.getId());
        empleado.setCodigoInterno(dto.getCodigoInterno());
        empleado.setEstado(dto.getEstado());
        empleado.setCreatedAt(LocalDateTime.now());

        empleadoRepository.save(empleado);

        auditoriaContext.setDetalle("Creación persona DNI: " + dto.getDni());
    }

    // ============================
    // LISTAR
    // ============================
    public List<PersonaEmpleadoResponseDto> listar() {

        List<Persona> personas = personaRepository.findAll();

        return personas.stream().map(p -> {

            Empleado emp = empleadoRepository
                    .findAll()
                    .stream()
                    .filter(e -> e.getPersonaId().equals(p.getId()))
                    .findFirst()
                    .orElse(null);

            PersonaEmpleadoResponseDto dto = new PersonaEmpleadoResponseDto();
            dto.setId(p.getId());
            dto.setNombreCompleto(p.getNombreCompleto());
            dto.setDni(p.getDni());
            dto.setEmail(p.getEmail());
            dto.setTelefono(p.getTelefono());
            dto.setDireccion(p.getDireccion());
            dto.setDistritoId(p.getDistritoId());

            if (emp != null) {
                dto.setCodigoInterno(emp.getCodigoInterno());
                dto.setEstado(emp.getEstado());
            }

            return dto;

        }).toList();
    }

    // ============================
    // OBTENER POR ID
    // ============================
    public PersonaEmpleadoResponseDto obtenerPorId(Long id) {

        Persona p = personaRepository.findById(id)
                .orElseThrow(() -> new NegocioException("Persona no encontrada"));

        Empleado emp = empleadoRepository
                .findAll()
                .stream()
                .filter(e -> e.getPersonaId().equals(id))
                .findFirst()
                .orElse(null);

        PersonaEmpleadoResponseDto dto = new PersonaEmpleadoResponseDto();

        dto.setId(p.getId());
        dto.setNombreCompleto(p.getNombreCompleto());
        dto.setDni(p.getDni());
        dto.setEmail(p.getEmail());
        dto.setTelefono(p.getTelefono());
        dto.setDireccion(p.getDireccion());
        dto.setDistritoId(p.getDistritoId());

        if (emp != null) {
            dto.setCodigoInterno(emp.getCodigoInterno());
            dto.setEstado(emp.getEstado());
        }

        return dto;
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

        personaRepository.save(persona);

        Empleado emp = empleadoRepository.findAll()
                .stream()
                .filter(e -> e.getPersonaId().equals(id))
                .findFirst()
                .orElseThrow(() -> new NegocioException("Empleado no encontrado"));

        emp.setCodigoInterno(dto.getCodigoInterno());
        emp.setEstado(dto.getEstado());

        empleadoRepository.save(emp);

        auditoriaContext.setDetalle("Actualización persona ID: " + id);
    }

    // ============================
    // ELIMINAR (lógico)
    // ============================
    @Auditable(accion = "ELIMINAR_PERSONA")
    public void eliminar(Long id) {

        Empleado emp = empleadoRepository.findAll()
                .stream()
                .filter(e -> e.getPersonaId().equals(id))
                .findFirst()
                .orElseThrow(() -> new NegocioException("Empleado no encontrado"));

        emp.setEstado("INACTIVO");

        empleadoRepository.save(emp);

        auditoriaContext.setDetalle("Eliminación lógica persona ID: " + id);
    }
}